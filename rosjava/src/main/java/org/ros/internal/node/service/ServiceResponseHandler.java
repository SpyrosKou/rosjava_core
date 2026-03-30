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

import com.google.common.base.Preconditions;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.ros.exception.RemoteException;
import org.ros.internal.node.response.StatusCode;
import org.ros.message.MessageDeserializer;
import org.ros.node.service.ServiceResponseListener;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A Netty {@link SimpleChannelHandler} for service responses.
 *
 * @author damonkohler@google.com (Damon Kohler)
 * @author Spyros Koukas
 */
final class ServiceResponseHandler<ResponseType> extends SimpleChannelHandler {

  private final ConcurrentLinkedQueue<ServiceResponseListener<ResponseType>> responseListeners;
  private final MessageDeserializer<ResponseType> deserializer;
  private final ExecutorService executorService;
  private final AtomicBoolean terminated = new AtomicBoolean(false);

  public ServiceResponseHandler(ConcurrentLinkedQueue<ServiceResponseListener<ResponseType>> messageListeners,
      MessageDeserializer<ResponseType> deserializer, ExecutorService executorService) {
    this.responseListeners = messageListeners;
    this.deserializer = deserializer;
    this.executorService = executorService;
  }

  @Override
  public final void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
    if (this.terminated.get()) {
      return;
    }
    final ServiceResponseListener<ResponseType> listener = this.responseListeners.poll();
    Preconditions.checkNotNull(listener, "No listener for incoming service response.");
    final ServiceServerResponse response = (ServiceServerResponse) e.getMessage();
    final ChannelBuffer buffer = response.getMessage();
    this.executeOrRun(() -> {
      if (response.getErrorCode() == 1) {
        final ResponseType responseMessage;
        try {
          responseMessage = this.deserializer.deserialize(buffer);
        } catch (final RuntimeException ex) {
          listener.onFailure(new RemoteException(StatusCode.ERROR, errorMessageFor(ex)));
          return;
        }
        listener.onSuccess(responseMessage);
      } else {
        final String message = StandardCharsets.US_ASCII.decode(buffer.toByteBuffer()).toString();
        listener.onFailure(new RemoteException(StatusCode.ERROR, message));
      }
    });
  }

  @Override
  public final void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {
    this.failPendingResponses("Service client connection closed before a response was received.");
    super.channelClosed(ctx, e);
  }

  @Override
  public final void channelDisconnected(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {
    this.failPendingResponses("Service client connection closed before a response was received.");
    super.channelDisconnected(ctx, e);
  }

  @Override
  public final void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e)
      throws Exception {
    this.failPendingResponses(errorMessageFor(e.getCause()));
    super.exceptionCaught(ctx, e);
  }

  private void failPendingResponses(final String message) {
    if (!this.terminated.compareAndSet(false, true)) {
      return;
    }
    while (true) {
      final ServiceResponseListener<ResponseType> listener = this.responseListeners.poll();
      if (listener == null) {
        return;
      }
      this.executeOrRun(() -> this.failListenerSafely(listener, message));
    }
  }

  private void executeOrRun(final Runnable runnable) {
    try {
      this.executorService.execute(runnable);
    } catch (final RejectedExecutionException e) {
      runnable.run();
    }
  }

  private static String errorMessageFor(final Throwable throwable) {
    if (throwable == null) {
      return "Service client connection closed before a response was received.";
    }
    final String message = throwable.getMessage();
    if (message != null && !message.isEmpty()) {
      return message;
    }
    return throwable.toString();
  }

  private void failListenerSafely(final ServiceResponseListener<ResponseType> listener,
      final String message) {
    try {
      listener.onFailure(new RemoteException(StatusCode.ERROR, message));
    } catch (final RuntimeException ignored) {
      // Keep draining the remaining listeners during terminal cleanup.
    }
  }
}
