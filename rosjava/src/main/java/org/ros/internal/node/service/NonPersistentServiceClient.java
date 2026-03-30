/*
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
import org.jboss.netty.channel.ChannelFuture;
import org.ros.exception.RemoteException;
import org.ros.exception.RosRuntimeException;
import org.ros.internal.message.Message;
import org.ros.internal.message.MessageBufferPool;
import org.ros.internal.node.response.StatusCode;
import org.ros.internal.transport.ClientHandshakeListener;
import org.ros.internal.transport.ConnectionHeader;
import org.ros.internal.transport.ConnectionHeaderFields;
import org.ros.internal.transport.tcp.TcpClient;
import org.ros.internal.transport.tcp.TcpClientManager;
import org.ros.message.MessageDeserializer;
import org.ros.message.MessageFactory;
import org.ros.message.MessageSerializer;
import org.ros.namespace.GraphName;
import org.ros.node.service.ServiceCaller;
import org.ros.node.service.ServiceResponseListener;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Non-persistent {@link ServiceCaller} implementation which opens a fresh
 * connection per request.
 *
 * @author Spyros Koukas
 */
final class NonPersistentServiceClient<T extends Message, S extends Message> implements ServiceCaller<T, S> {
    private final ServiceDeclaration serviceDeclaration;
    private final MessageSerializer<T> serializer;
    private final MessageDeserializer<S> deserializer;
    private final MessageFactory messageFactory;
    private final ScheduledExecutorService executorService;
    private final MessageBufferPool messageBufferPool = new MessageBufferPool();
    private final ConnectionHeader connectionHeader = new ConnectionHeader();
    private final Set<CallSession> activeSessions = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicReference<URI> serviceUri = new AtomicReference<>();
    private final Supplier<URI> serviceUriProvider;
    private final Consumer<ServiceCaller<?, ?>> shutdownListener;

    static <S extends Message, T extends Message> NonPersistentServiceClient<S, T> newDefault(
            final GraphName nodeName,
            final ServiceDeclaration serviceDeclaration,
            final MessageSerializer<S> serializer,
            final MessageDeserializer<T> deserializer,
            final MessageFactory messageFactory,
            final ScheduledExecutorService executorService,
            final Supplier<URI> serviceUriProvider,
            final Consumer<ServiceCaller<?, ?>> shutdownListener) {
        return new NonPersistentServiceClient<>(nodeName, serviceDeclaration, serializer, deserializer,
                messageFactory, executorService, serviceUriProvider, shutdownListener);
    }

    private NonPersistentServiceClient(final GraphName nodeName,
                                       final ServiceDeclaration serviceDeclaration,
                                       final MessageSerializer<T> serializer,
                                       final MessageDeserializer<S> deserializer,
                                       final MessageFactory messageFactory,
                                       final ScheduledExecutorService executorService,
                                       final Supplier<URI> serviceUriProvider,
                                       final Consumer<ServiceCaller<?, ?>> shutdownListener) {
        this.serviceDeclaration = serviceDeclaration;
        this.serializer = serializer;
        this.deserializer = deserializer;
        this.messageFactory = messageFactory;
        this.executorService = executorService;
        this.serviceUriProvider = Preconditions.checkNotNull(serviceUriProvider);
        this.shutdownListener = shutdownListener;
        this.serviceUri.set(serviceDeclaration.getUri());
        this.connectionHeader.addField(ConnectionHeaderFields.CALLER_ID, nodeName.toString());
        this.connectionHeader.merge(serviceDeclaration.toConnectionHeader());
    }

    @Override
    public void call(final T request, final ServiceResponseListener<S> listener) {
        Preconditions.checkNotNull(request, "Request must not be null.");
        Preconditions.checkNotNull(listener, "Listener must not be null.");
        if (this.shutdown.get()) {
            failListener(listener, "Service client has been shut down.");
            return;
        }

        final ChannelBuffer buffer = this.messageBufferPool.acquire();
        CallSession session = null;
        boolean queued = false;
        try {
            this.serializer.serialize(request, buffer);
            session = this.newQueuedSession(listener);
            queued = true;
            if (this.shutdown.get()) {
                session.failQueuedListener("Service client has been shut down.");
                return;
            }
            session = this.connectWithRefresh(session, listener);
            final ChannelFuture writeFuture = session.write(buffer).awaitUninterruptibly();
            if (!writeFuture.isSuccess()) {
                session.failQueuedListener(errorMessageFor(writeFuture.getCause(),
                        "Service client connection closed before request could be sent."));
            }
        } catch (final RuntimeException e) {
            final String errorMessage = errorMessageFor(e,
                    "Service client connection closed before request could be sent.");
            if (session == null) {
                failListener(listener, errorMessage);
            } else if (queued) {
                session.failQueuedListener(errorMessage);
            } else {
                session.close();
                failListener(listener, errorMessage);
            }
        } finally {
            this.messageBufferPool.release(buffer);
        }
    }

    @Override
    public GraphName getName() {
        return this.serviceDeclaration.getName();
    }

    @Override
    public void shutdown() {
        if (!this.shutdown.compareAndSet(false, true)) {
            return;
        }
        try {
            for (final CallSession session : this.activeSessions) {
                session.close("Service client has been shut down.");
            }
        } finally {
            this.shutdownListener.accept(this);
        }
    }

    @Override
    public T newMessage() {
        return this.messageFactory.newFromType(this.serviceDeclaration.getType());
    }

    @Override
    public String toString() {
        return "NonPersistentServiceClient<" + this.serviceDeclaration + ">";
    }

    private static <S extends Message> void failListener(final ServiceResponseListener<S> listener,
                                                         final String message) {
        listener.onFailure(new RemoteException(StatusCode.ERROR, message));
    }

    private static String errorMessageFor(final Throwable throwable, final String fallbackMessage) {
        if (throwable == null) {
            return fallbackMessage;
        }
        final String message = throwable.getMessage();
        if (message != null && !message.isEmpty()) {
            return message;
        }
        return throwable.toString();
    }

    private CallSession newQueuedSession(final ServiceResponseListener<S> listener) {
        final CallSession session = new CallSession(listener);
        this.activeSessions.add(session);
        session.queueListener();
        return session;
    }

    private CallSession connectWithRefresh(final CallSession session, final ServiceResponseListener<S> listener) {
        final URI originalUri = this.resolveServiceUri();
        try {
            session.connect(originalUri);
            return session;
        } catch (final RuntimeException e) {
            final URI refreshedUri = this.refreshServiceUri();
            if (!this.shouldRetryWithRefreshedUri(originalUri, refreshedUri)) {
                throw e;
            }
            session.cancel();
            final CallSession retrySession = this.newQueuedSession(listener);
            if (this.shutdown.get()) {
                retrySession.failQueuedListener("Service client has been shut down.");
                return retrySession;
            }
            retrySession.connect(refreshedUri);
            return retrySession;
        }
    }

    private URI resolveServiceUri() {
        final URI uri = this.serviceUri.get();
        if (uri != null) {
            return uri;
        }
        return this.requireResolvedServiceUri(this.refreshServiceUri());
    }

    private URI refreshServiceUri() {
        final URI refreshedUri = this.serviceUriProvider.get();
        this.serviceUri.set(refreshedUri);
        return refreshedUri;
    }

    private URI requireResolvedServiceUri(final URI uri) {
        if (uri != null) {
            return uri;
        }
        throw new RosRuntimeException("No such service " + this.serviceDeclaration.getName());
    }

    private boolean shouldRetryWithRefreshedUri(final URI originalUri, final URI refreshedUri) {
        return !this.shutdown.get() && refreshedUri != null && !refreshedUri.equals(originalUri);
    }

    private final class CallSession {
        private final TcpClientManager tcpClientManager;
        private final ConcurrentLinkedQueue<ServiceResponseListener<S>> responseListeners =
                new ConcurrentLinkedQueue<>();
        private final HandshakeLatch handshakeLatch = new HandshakeLatch();
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final ClosingServiceResponseListener responseListener;

        private TcpClient tcpClient;

        private CallSession(final ServiceResponseListener<S> listener) {
            this.tcpClientManager = new TcpClientManager(executorService);
            this.responseListener = new ClosingServiceResponseListener(listener);
            final ServiceClientHandshakeHandler<T, S> serviceClientHandshakeHandler =
                    new ServiceClientHandshakeHandler<>(connectionHeader, this.responseListeners, deserializer,
                            executorService);
            serviceClientHandshakeHandler.addListener(this.handshakeLatch);
            this.tcpClientManager.addNamedChannelHandler(serviceClientHandshakeHandler);
        }

        private void queueListener() {
            this.responseListeners.add(this.responseListener);
        }

        private void connect(final URI uri) {
            this.throwIfClosed();
            Preconditions.checkNotNull(uri, "URI must be specified.");
            Preconditions.checkArgument(uri.getScheme().equals("rosrpc"), "Invalid service URI.");
            final InetSocketAddress address = new InetSocketAddress(uri.getHost(), uri.getPort());
            this.handshakeLatch.reset();
            this.tcpClient = this.tcpClientManager.connect(toString(), address);
            try {
                if (!this.handshakeLatch.await(1, TimeUnit.SECONDS)) {
                    throw new RosRuntimeException(this.handshakeLatch.getErrorMessage());
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RosRuntimeException("Handshake timed out.");
            }
            this.throwIfClosed();
        }

        private ChannelFuture write(final ChannelBuffer buffer) {
            this.throwIfClosed();
            return this.tcpClient.write(buffer);
        }

        private void failQueuedListener(final String message) {
            if (this.responseListeners.remove(this.responseListener)) {
                try {
                    this.responseListener.onFailure(new RemoteException(StatusCode.ERROR, message));
                } finally {
                    this.close();
                }
                return;
            }
            this.close();
        }

        private void cancel() {
            this.responseListeners.remove(this.responseListener);
            this.close();
        }

        private void close() {
            this.close(null);
        }

        private void close(final String pendingFailureMessage) {
            if (!this.closed.compareAndSet(false, true)) {
                return;
            }
            if (pendingFailureMessage != null && this.responseListeners.remove(this.responseListener)) {
                try {
                    this.responseListener.onFailure(new RemoteException(StatusCode.ERROR, pendingFailureMessage));
                } finally {
                    activeSessions.remove(this);
                    this.tcpClientManager.shutdown();
                }
                return;
            }
            activeSessions.remove(this);
            this.tcpClientManager.shutdown();
        }

        private void throwIfClosed() {
            if (this.closed.get() || shutdown.get()) {
                throw new RosRuntimeException("Service client has been shut down.");
            }
        }

        private final class HandshakeLatch implements ClientHandshakeListener {
            private CountDownLatch latch;
            private boolean success;
            private String errorMessage;

            @Override
            public void onSuccess(final ConnectionHeader outgoingConnectionHeader,
                                  final ConnectionHeader incomingConnectionHeader) {
                this.success = true;
                this.latch.countDown();
            }

            @Override
            public void onFailure(final ConnectionHeader outgoingConnectionHeader, final String errorMessage) {
                this.errorMessage = errorMessage;
                this.success = false;
                this.latch.countDown();
            }

            private boolean await(final long timeout, final TimeUnit unit) throws InterruptedException {
                if (!this.latch.await(timeout, unit)) {
                    this.errorMessage = "Service client handshake timed out.";
                    return false;
                }
                return this.success;
            }

            private String getErrorMessage() {
                return this.errorMessage;
            }

            private void reset() {
                this.latch = new CountDownLatch(1);
                this.success = false;
                this.errorMessage = null;
            }
        }

        private final class ClosingServiceResponseListener implements ServiceResponseListener<S> {
            private final ServiceResponseListener<S> delegate;

            private ClosingServiceResponseListener(final ServiceResponseListener<S> delegate) {
                this.delegate = delegate;
            }

            @Override
            public void onSuccess(final S response) {
                try {
                    this.delegate.onSuccess(response);
                } finally {
                    close();
                }
            }

            @Override
            public void onFailure(final RemoteException e) {
                try {
                    this.delegate.onFailure(e);
                } finally {
                    close();
                }
            }
        }
    }
}
