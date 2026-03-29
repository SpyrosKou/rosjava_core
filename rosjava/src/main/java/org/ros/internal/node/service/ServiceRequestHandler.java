/*
 * Copyright (C) 2011 Google Inc.
 * Copyright (C) 2026 Spyros Koukas
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ros.internal.node.service;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.ros.exception.ServiceException;
import org.ros.internal.message.Message;
import org.ros.internal.message.MessageBufferPool;
import org.ros.message.MessageDeserializer;
import org.ros.message.MessageFactory;
import org.ros.message.MessageSerializer;
import org.ros.node.service.ServiceResponseBuilder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author damonkohler@google.com (Damon Kohler)
 * @author Spyros Koukas
 */
final class ServiceRequestHandler<T extends Message, S extends Message> extends SimpleChannelHandler {

    private final ServiceDeclaration serviceDeclaration;
    private final ServiceResponseBuilder<T, S> responseBuilder;
    private final MessageDeserializer<T> deserializer;
    private final MessageSerializer<S> serializer;
    private final MessageFactory messageFactory;
    private final ExecutorService executorService;
    private final MessageBufferPool messageBufferPool = new MessageBufferPool();
    // Service replies on a persistent connection must be emitted in request
    // order because the client correlates them FIFO rather than by request id.
    private final ConcurrentLinkedQueue<ChannelBuffer> pendingRequests = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean processingRequests = new AtomicBoolean(false);

    public ServiceRequestHandler(final ServiceDeclaration serviceDeclaration,
                                 final ServiceResponseBuilder<T, S> responseBuilder,
                                 final MessageDeserializer<T> deserializer,
                                 final MessageSerializer<S> serializer,
                                 final MessageFactory messageFactory,
                                 final ExecutorService executorService) {
        this.serviceDeclaration = serviceDeclaration;
        this.deserializer = deserializer;
        this.serializer = serializer;
        this.responseBuilder = responseBuilder;
        this.messageFactory = messageFactory;
        this.executorService = executorService;
    }

    private final void handleRequest(final ChannelBuffer requestBuffer, final ChannelBuffer responseBuffer)
            throws ServiceException {
      final T request = this.deserializer.deserialize(requestBuffer);
      final S response = this.messageFactory.newFromType(this.serviceDeclaration.getType());
      this.responseBuilder.build(request, response);
      this.serializer.serialize(response, responseBuffer);
    }

    private final void handleSuccess(final ChannelHandlerContext ctx,
                                     final ServiceServerResponse response,
                                     final ChannelBuffer responseBuffer) {
        response.setErrorCode(1);
        response.setMessageLength(responseBuffer.readableBytes());
        response.setMessage(responseBuffer);
        ctx.getChannel().write(response);
    }

    private final void handleError(final ChannelHandlerContext ctx,
                                   final ServiceServerResponse response,
                                   final String message) {
        response.setErrorCode(0);
        final ByteBuffer encodedMessage = StandardCharsets.US_ASCII.encode(message);
        response.setMessageLength(encodedMessage.limit());
        response.setMessage(ChannelBuffers.wrappedBuffer(encodedMessage));
        ctx.getChannel().write(response);
    }

    private static final String errorMessageFor(final Throwable throwable) {
        final String message = throwable.getMessage();
        if (message != null && !message.isEmpty()) {
            return message;
        }
        return throwable.toString();
    }

    private final void processRequest(final ChannelHandlerContext ctx, final ChannelBuffer requestBuffer) {
        final ServiceServerResponse response = new ServiceServerResponse();
        final ChannelBuffer responseBuffer = messageBufferPool.acquire();

        try {
            handleRequest(requestBuffer, responseBuffer);
            handleSuccess(ctx, response, responseBuffer);
        } catch (final ServiceException ex) {
            handleError(ctx, response, ex.getMessage());
        } catch (final RuntimeException ex) {
            handleError(ctx, response, errorMessageFor(ex));
        } finally {
            this.messageBufferPool.release(responseBuffer);
        }
    }

    private final void scheduleRequestProcessing(final ChannelHandlerContext ctx) {
        if (!this.processingRequests.compareAndSet(false, true)) {
            return;
        }
        try {
            this.executorService.execute(() -> drainPendingRequests(ctx));
        } catch (final RejectedExecutionException e) {
            this.processingRequests.set(false);
            throw e;
        }
    }

    private final void drainPendingRequests(final ChannelHandlerContext ctx) {
        Throwable failure = null;
        try {
            while (true) {
                final ChannelBuffer requestBuffer = this.pendingRequests.poll();
                if (requestBuffer == null) {
                    return;
                }
                processRequest(ctx, requestBuffer);
            }
        } catch (final Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            this.processingRequests.set(false);
            if (!this.pendingRequests.isEmpty()) {
                try {
                    this.scheduleRequestProcessing(ctx);
                } catch (final RejectedExecutionException e) {
                    if (failure != null) {
                        failure.addSuppressed(e);
                    } else {
                        throw e;
                    }
                }
            }
        }
    }

    @Override
    public final void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        // Although the ChannelHandlerContext is explicitly documented as being safe
        // to keep for later use, the MessageEvent is not. So, we make a defensive
        // copy of the ChannelBuffer.
        final ChannelBuffer requestBuffer = ((ChannelBuffer) e.getMessage()).copy();
        this.pendingRequests.add(requestBuffer);
        this.scheduleRequestProcessing(ctx);
        super.messageReceived(ctx, e);
    }
}
