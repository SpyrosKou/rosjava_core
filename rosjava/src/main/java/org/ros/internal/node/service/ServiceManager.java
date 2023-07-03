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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.ros.exception.DuplicateServiceException;
import org.ros.internal.message.Message;
import org.ros.namespace.GraphName;
import org.ros.node.service.ChannelBufferServiceServer;
import org.ros.node.service.ServiceClient;
import org.ros.node.service.ServiceServer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Manages a collection of {@link org.ros.node.service.ChannelBufferServiceServer}s and {@link ServiceClient}s.
 *
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class ServiceManager {

    /**
     * A mapping from service name to the server for the service.
     */
    private final ConcurrentHashMap<GraphName, ChannelBufferServiceServer<? extends Message, ? extends Message>> serviceServers = new ConcurrentHashMap<>();

    /**
     * A mapping from service name to a client for the service.
     */
    private final ConcurrentHashMap<GraphName, ServiceClient<? extends Message, ? extends Message>> serviceClients = new ConcurrentHashMap<>();

    // TODO(damonkohler): Change to ListenerGroup.
    private ServiceManagerListener listener;

    public void setListener(final ServiceManagerListener listener) {
        this.listener = listener;
    }

    public final boolean hasServer(GraphName name) {
        return this.serviceServers.containsKey(name);
    }

    /**
     * Will throw a {@link DuplicateServiceException} if a service with the same name already exists on this {@link ServiceServer}
     *
     * @param serviceServer
     */
    public final void addServer(final ChannelBufferServiceServer<? extends Message, ? extends Message> serviceServer) {

        final ChannelBufferServiceServer<? extends Message, ? extends Message> result = this.serviceServers.putIfAbsent(serviceServer.getName(), serviceServer);
        final boolean added = (result == null);
        if (added && (this.listener != null)) {
            this.listener.onServiceServerAdded(serviceServer);
        }
        if (!added) {
            final GraphName graphName = serviceServer.getName();
            throw new DuplicateServiceException(String.format("ServiceServer %s already exists.", graphName));
        }

    }

    /**
     *
     */
    public void removeAllServiceServers() {
        final List<GraphName> allServers = this.serviceServers.values().stream().map(ServiceServer::getName).toList();
        for(final GraphName graphName:allServers){
            this.removeServer(graphName);
        }
        allServers.clear();
    }

    /**
     * Nothing happens if the server does not exist
     *
     * @param graphName
     */
    public void removeServer(final GraphName graphName) {
        final ServiceServer<? extends Message, ? extends Message> server = this.serviceServers.remove(graphName);

        if (server != null && this.listener != null) {
            this.listener.onServiceServerRemoved(server);
        }
    }

    public final ChannelBufferServiceServer<? extends Message, ? extends Message> getServer(final GraphName name) {
        return serviceServers.get(name);
    }

    public final boolean hasClient(final GraphName name) {
        return this.serviceClients.containsKey(name);
    }

    public final ServiceClient<?, ?> getOrCreateClient(final GraphName graphName, final Supplier<ServiceClient<? extends Message, ? extends Message>> serviceClientSupplier) {
        return this.serviceClients.computeIfAbsent(graphName, x -> serviceClientSupplier.get());
    }

    public void removeClient(ServiceClient<?, ?> serviceClient) {
        serviceClients.remove(serviceClient.getName());
    }

    public ServiceClient<?, ?> getClient(GraphName name) {
        return serviceClients.get(name);
    }

    public final List<ChannelBufferServiceServer<?, ?>> getServers() {
        return ImmutableList.copyOf(this.serviceServers.values());
    }

    public final List<ServiceClient<?, ?>> getClients() {
        return ImmutableList.copyOf(serviceClients.values());
    }

    public final Set<GraphName> getServerNames() {
        return Collections.unmodifiableSet(this.serviceServers.keySet());
    }

    public final Set<GraphName> getClientNames() {
        return Collections.unmodifiableSet(this.serviceClients.keySet());
    }
}
