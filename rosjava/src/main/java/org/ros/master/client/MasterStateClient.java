/*
 * Copyright (C) 2012 Google Inc.
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

package org.ros.master.client;

import org.ros.internal.node.client.MasterClient;
import org.ros.internal.node.client.Response;
import org.ros.internal.node.server.master.MasterServer;
import org.ros.internal.node.topic.TopicDeclaration;
import org.ros.node.Node;
import org.ros.node.service.ServiceServer;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A remote client for obtaining system information from a master.
 *
 * @author Keith M. Hughes
 * @author Spyros Koukas
 */
public final class MasterStateClient {

    /**
     * The node doing the calling.
     */
    private final Node caller;

    /**
     * Client for speaking to the master.
     */
    private final MasterClient masterClient;

    public MasterStateClient(final Node caller, final URI masterUri) {
        this.caller = caller;
        this.masterClient = new MasterClient(masterUri);
    }

    /**
     * @param nodeName the name of the {@link Node} to lookup
     * @return the {@link URI} of the {@link Node} with the given name
     */
    public final URI lookupNode(final String nodeName) {
        final Response<URI> response = this.masterClient.lookupNode(caller.getName(), nodeName);
        return response.getResult();
    }

    /**
     * @return the {@link URI} of the {@link MasterServer}
     */
    public final URI getUri() {
        final Response<URI> response = this.masterClient.getUri(caller.getName());
        return response.getResult();
    }

    /**
     * @param serviceName the name of the {@link ServiceServer} to look up
     * @return the {@link URI} of the {@link ServiceServer} with the given name
     */
    public final URI lookupService(String serviceName) {
        final Response<URI> result = this.masterClient.lookupService(caller.getName(), serviceName);
        return result.getResult();
    }

    /**
     * @return a {@link List} of {@link TopicType}s known by the master
     */
    public final List<TopicType> getTopicTypes() {
        final Response<List<TopicType>> result = this.masterClient.getTopicTypes(caller.getName());
        return result.getResult();
    }

    /**
     * @return the current {@link SystemState}
     */
    public final SystemState getSystemState() {
        final Response<SystemState> result = this.masterClient.getSystemState(caller.getName());
        return result.getResult();
    }

    /**
     * Checks if the specified node is registered as a subscriber to the given topic
     * with the master system.
     *
     * @param nodeName the name of the node to check; must not be null
     * @param topicName the name of the topic to check; must not be null
     * @return true if the node is registered as a subscriber to the topic, otherwise false
     * @throws NullPointerException if either {@code nodeName} or {@code topicName} is null
     */
    public final boolean isRegisteredWithMaster(final String nodeName, final String topicName) {
        Objects.requireNonNull(nodeName);
        Objects.requireNonNull(topicName);
        return this.getSystemState().getTopics().stream()
                .filter(Objects::nonNull)
                .filter(topicSystemState -> topicName.equals(topicSystemState.getTopicName()))
                .map(TopicSystemState::getSubscribers)
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .anyMatch(nodeName::equals);
    }
}
