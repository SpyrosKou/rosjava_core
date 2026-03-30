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

import org.ros.exception.DuplicateServiceException;
import org.ros.internal.message.Message;
import org.ros.internal.message.service.ServiceDescription;
import org.ros.internal.node.server.SlaveServer;
import org.ros.message.MessageDeserializer;
import org.ros.message.MessageFactory;
import org.ros.message.MessageSerializer;
import org.ros.namespace.GraphName;
import org.ros.node.service.ServiceCaller;
import org.ros.node.service.ServiceClient;
import org.ros.node.service.ServiceResponseBuilder;
import org.ros.node.service.ServiceServer;

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import java.net.URI;

/**
 * A factory for {@link ServiceServer}s and {@link ServiceClient}s.
 *
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class ServiceFactory {

    private final GraphName nodeName;
    private final SlaveServer slaveServer;
    private final ServiceManager serviceManager;
    private final ScheduledExecutorService executorService;


    public ServiceFactory(final GraphName nodeName, final SlaveServer slaveServer, final ServiceManager serviceManager,
                          final ScheduledExecutorService executorService) {
        this.nodeName = nodeName;
        this.slaveServer = slaveServer;
        this.serviceManager = serviceManager;
        this.executorService = executorService;
    }

    /**
     * Creates a {@link DefaultServiceServer} instance and registers it with the
     * master.
     *
     * @param serviceDeclaration     the {@link ServiceDescription} that is being served
     * @param serviceResponseBuilder the {@link ServiceResponseBuilder} that is used to build responses
     * @param messageDeserializer    a {@link MessageDeserializer} to be used for incoming messages
     * @param messageSerializer      a {@link MessageSerializer} to be used for outgoing messages
     * @param messageFactory         a {@link MessageFactory} to be used for creating responses
     * @return a {@link DefaultServiceServer} instance
     */
    public <T extends Message, S extends Message> DefaultServiceServer<T, S> newServer(
            final ServiceDeclaration serviceDeclaration
            , final ServiceResponseBuilder<T, S> serviceResponseBuilder
            , final MessageDeserializer<T> messageDeserializer
            , final MessageSerializer<S> messageSerializer
            , final MessageFactory messageFactory) {

        final DefaultServiceServer<T, S> serviceServer =
                new DefaultServiceServer<>(serviceDeclaration, serviceResponseBuilder,
                        slaveServer.getTcpRosAdvertiseAddress(), messageDeserializer, messageSerializer, messageFactory,
                        executorService);
        this.serviceManager.addServer(serviceServer);
        return serviceServer;
    }


    /**
     * Gets or creates a {@link DefaultServiceClient} instance.
     * {@link DefaultServiceClient}s are cached and reused per service. When a new
     * {@link DefaultServiceClient} is created, it is connected to the
     * {@link DefaultServiceServer}.
     *
     * @param serviceDeclaration the {@link ServiceDescription} that is being served
     * @param deserializer       a {@link MessageDeserializer} to be used for incoming messages
     * @param serializer         a {@link MessageSerializer} to be used for outgoing messages
     * @param messageFactory     a {@link MessageFactory} to be used for creating requests
     * @return a {@link DefaultServiceClient} instance
     */
    @SuppressWarnings("unchecked")
    public <T extends Message, S extends Message> ServiceClient<T, S> newClient(final ServiceDeclaration serviceDeclaration,
                                                                                 final MessageSerializer<T> serializer, final MessageDeserializer<S> deserializer,
                                                                                 final MessageFactory messageFactory) {
        Preconditions.checkNotNull(serviceDeclaration.getUri());

        final GraphName graphName = serviceDeclaration.getName();

        final Supplier<ServiceClient<? extends Message, ? extends Message>> serviceClientSupplier = () ->
                DefaultServiceClient.newDefault(nodeName, serviceDeclaration, serializer, deserializer,
                        messageFactory, executorService);


        final ServiceClient<T, S> serviceClient = (ServiceClient<T, S>) this.serviceManager.getOrCreateClient(graphName, serviceClientSupplier);
        serviceClient.connectIfUnconnected(serviceDeclaration.getUri());
        return serviceClient;
    }

    public <T extends Message, S extends Message> ServiceCaller<T, S> newNonPersistentClient(
            final ServiceDeclaration serviceDeclaration,
            final MessageSerializer<T> serializer,
            final MessageDeserializer<S> deserializer,
            final MessageFactory messageFactory,
            final Supplier<URI> serviceUriProvider) {
        Preconditions.checkNotNull(serviceDeclaration.getUri());

        final ServiceCaller<T, S> serviceClient = NonPersistentServiceClient.newDefault(nodeName,
                serviceDeclaration, serializer, deserializer, messageFactory, executorService, serviceUriProvider,
                this.serviceManager::removeNonPersistentClient);
        this.serviceManager.addNonPersistentClient(serviceClient);
        return serviceClient;
    }
}
