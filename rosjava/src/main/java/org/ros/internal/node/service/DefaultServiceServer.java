/*
 * Copyright (C) 2011 Google Inc.
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
import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelHandler;
import org.ros.address.AdvertiseAddress;
import org.ros.concurrent.ListenerGroup;
import org.ros.internal.message.Message;
import org.ros.internal.message.service.ServiceDescription;
import org.ros.internal.message.service.ServiceDescriptionFactory;
import org.ros.internal.transport.ConnectionHeader;
import org.ros.internal.transport.ConnectionHeaderFields;
import org.ros.message.MessageDeserializer;
import org.ros.message.MessageFactory;
import org.ros.message.MessageSerializer;
import org.ros.namespace.GraphName;
import org.ros.node.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Default implementation of a {@link ServiceServer}.
 *
 * @author damonkohler@google.com (Damon Kohler)
 */
final class DefaultServiceServer<T extends Message, S extends Message> implements ChannelBufferServiceServer<T, S> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String ROSRPC = "rosrpc";

    private final ServiceDeclaration serviceDeclaration;
    private final ServiceResponseBuilder<T, S> serviceResponseBuilder;
    private final AdvertiseAddress advertiseAddress;
    private final MessageDeserializer<T> messageDeserializer;
    private final MessageSerializer<S> messageSerializer;
    private final MessageFactory messageFactory;
    private final ScheduledExecutorService scheduledExecutorService;
    private final ListenerGroup<ServiceServerListener<T, S>> listenerGroup;

    public DefaultServiceServer(
            final ServiceDeclaration serviceDeclaration
            ,final ServiceResponseBuilder<T, S> serviceResponseBuilder
            ,final AdvertiseAddress advertiseAddress
            ,final MessageDeserializer<T> messageDeserializer
            ,final MessageSerializer<S> messageSerializer
            ,final MessageFactory messageFactory
            ,final ScheduledExecutorService scheduledExecutorService) {
        this.serviceDeclaration = serviceDeclaration;
        this.serviceResponseBuilder = serviceResponseBuilder;
        this.advertiseAddress = advertiseAddress;
        this.messageDeserializer = messageDeserializer;
        this.messageSerializer = messageSerializer;
        this.messageFactory = messageFactory;
        this.scheduledExecutorService = scheduledExecutorService;
        this.listenerGroup = new ListenerGroup<>(scheduledExecutorService);
        this.listenerGroup.add(new LoggingServiceServerListener<>());
    }



    public final ChannelBuffer finishHandshake(final ConnectionHeader incomingConnectionHeader) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Client handshake header: " + incomingConnectionHeader);
        }
        final ConnectionHeader connectionHeader = toDeclaration().toConnectionHeader();
        final String expectedChecksum = connectionHeader.getField(ConnectionHeaderFields.MD5_CHECKSUM);
        final String incomingChecksum = incomingConnectionHeader.getField(ConnectionHeaderFields.MD5_CHECKSUM);
        // TODO(damonkohler): Pull out header field comparison logic.
        Preconditions.checkState(incomingChecksum.equals(expectedChecksum) || "*".equals(incomingChecksum));
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Server handshake header: " + connectionHeader);
        }
        return connectionHeader.encode();
    }

    @Override
    public final URI getUri() {
        return this.advertiseAddress.toUri(ROSRPC);
    }

    @Override
    public final GraphName getName() {
        return this.serviceDeclaration.getName();
    }

    /**
     * @return a new {@link ServiceDeclaration} with this
     * {@link DefaultServiceServer}'s {@link URI}
     */
    final ServiceDeclaration toDeclaration() {
        final ServiceIdentifier identifier = new ServiceIdentifier(serviceDeclaration.getName(), getUri());

        return new ServiceDeclaration(identifier, ServiceDescriptionFactory.newCreate(serviceDeclaration.getType(),
                serviceDeclaration.getDefinition(), serviceDeclaration.getMd5Checksum()));
    }

    public final ChannelHandler newRequestHandler() {
        return new ServiceRequestHandler<T, S>(serviceDeclaration, serviceResponseBuilder,
                messageDeserializer, messageSerializer, messageFactory, scheduledExecutorService);
    }

    /**
     * Signal all {@link ServiceServerListener}s that the {@link ServiceServer}
     * has been successfully registered with the master.
     *
     * <p>
     * Each listener is called in a separate thread.
     */
    public final void onMasterRegistrationSuccess() {

        this.listenerGroup.signal(listener -> listener.onMasterRegistrationSuccess(this));
    }

    /**
     * Signal all {@link ServiceServerListener}s that the {@link ServiceServer}
     * has failed to register with the master.
     *
     * <p>
     * Each listener is called in a separate thread.
     */
    public final void onMasterRegistrationFailure() {
        this.listenerGroup.signal(listener -> listener.onMasterRegistrationFailure(this));
    }

    /**
     * Signal all {@link ServiceServerListener}s that the {@link ServiceServer}
     * has been successfully unregistered with the master.
     *
     * <p>
     * Each listener is called in a separate thread.
     */
    public final void onMasterUnregistrationSuccess() {
        this.listenerGroup.signal(listener -> listener.onMasterUnregistrationSuccess(this));
    }

    /**
     * Signal all {@link ServiceServerListener}s that the {@link ServiceServer}
     * has failed to unregister with the master.
     *
     * <p>
     * Each listener is called in a separate thread.
     */
    public final void onMasterUnregistrationFailure() {
        this.listenerGroup.signal(listener -> listener.onMasterUnregistrationFailure(this));
    }

    @Override
    public final void shutdown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void addListener(final ServiceServerListener<T, S> listener) {
        this.listenerGroup.add(listener);
    }

    @Override
    public final String toString() {
        return "ServiceServer<" + toDeclaration() + ">";
    }
}
