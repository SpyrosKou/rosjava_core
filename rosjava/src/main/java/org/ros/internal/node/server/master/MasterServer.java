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

package org.ros.internal.node.server.master;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;


import org.ros.address.AdvertiseAddress;
import org.ros.address.BindAddress;
import org.ros.internal.node.client.SlaveClient;
import org.ros.internal.node.server.NodeIdentifier;
import org.ros.internal.node.server.SlaveServer;
import org.ros.internal.node.server.XmlRpcServer;
import org.ros.internal.node.topic.TopicParticipant;
import org.ros.internal.node.xmlrpc.MasterXmlRpcEndpointImpl;
import org.ros.master.client.TopicSystemState;
import org.ros.namespace.GraphName;
import org.ros.node.Node;
import org.ros.node.service.ServiceServer;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The {@link MasterServer} provides naming and registration services to the
 * rest of the {@link Node}s in the ROS system. It tracks {@link Publisher}s and
 * {@link Subscriber}s to {@link TopicSystemState}s as well as
 * {@link ServiceServer}s. The role of the {@link MasterServer} is to enable
 * individual ROS {@link Node}s to locate one another. Once these {@link Node}s
 * have located each other they communicate with each other peer-to-peer.
 *
 * @author damonkohler@google.com (Damon Kohler)
 * @author khughes@google.com (Keith M. Hughes)
 * @see <a href="http://www.ros.org/wiki/Master">Master documentation</a>
 */
public final class MasterServer extends XmlRpcServer implements MasterRegistrationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * Position in the {@link #getSystemState()} for publisher information.
     */
    public static final int SYSTEM_STATE_PUBLISHERS = 0;

    /**
     * Position in the {@link #getSystemState()} for subscriber information.
     */
    public static final int SYSTEM_STATE_SUBSCRIBERS = 1;

    /**
     * Position in the {@link #getSystemState()} for service information.
     */
    public static final int SYSTEM_STATE_SERVICES = 2;

    /**
     * The node name (i.e. the callerId XML-RPC field) used when the
     * {@link MasterServer} contacts a {@link SlaveServer}.
     */
    private static final GraphName MASTER_NODE_NAME = GraphName.of("/master");

    /**
     * The manager for handling master registration information.
     */
    private final MasterRegistrationManagerImpl masterRegistrationManager;

    public MasterServer(final BindAddress bindAddress, final AdvertiseAddress advertiseAddress) {
        super(bindAddress, advertiseAddress);
        this.masterRegistrationManager = new MasterRegistrationManagerImpl(this);
    }

    /**
     * Start the {@link MasterServer}.
     */
    public final void start() {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Starting master server.");
        }
        super.start(new MasterXmlRpcEndpointImpl(this));
    }

    /**
     * Register a service with the master.
     *
     * @param nodeName     the {@link GraphName} of the {@link Node} offering the service
     * @param nodeSlaveUri the {@link URI} of the {@link Node}'s {@link SlaveServer}
     * @param serviceName  the {@link GraphName} of the service
     * @param serviceUri   the {@link URI} of the service
     */
    public final void registerService(final GraphName nodeName, final URI nodeSlaveUri, final GraphName serviceName,
                                      final URI serviceUri) {
        synchronized (this.masterRegistrationManager) {
            this.masterRegistrationManager.registerService(nodeName, nodeSlaveUri, serviceName, serviceUri);
        }
    }

    /**
     * Unregister a service from the master.
     *
     * @param nodeName    the {@link GraphName} of the {@link Node} offering the service
     * @param serviceName the {@link GraphName} of the service
     * @param serviceUri  the {@link URI} of the service
     * @return {@code true} if the service was registered
     */
    public final boolean unregisterService(final GraphName nodeName, final GraphName serviceName, final URI serviceUri) {
        synchronized (this.masterRegistrationManager) {
            return this.masterRegistrationManager.unregisterService(nodeName, serviceName, serviceUri);
        }
    }

    /**
     * Subscribe the caller to the specified topic. In addition to receiving a
     * list of current publishers, the subscriber will also receive notifications
     * of new publishers via the publisherUpdate API.
     *
     * @param nodeName         the {@link GraphName} of the {@link Node} offering the service
     * @param nodeSlaveUri     the {@link URI} of the {@link Node}'s {@link SlaveServer}
     * @param topicName        the {@link GraphName} of the subscribed {@link TopicParticipant}
     * @param topicMessageType the message type of the topic
     * @return A {@link List} of XMLRPC API {@link URI}s for nodes currently
     * publishing the specified topic
     */
    public final List<URI> registerSubscriber(final GraphName nodeName, final URI nodeSlaveUri, final GraphName topicName,
                                              final String topicMessageType) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format(
                    "Registering subscriber %s with message type %s on node %s with URI %s", topicName,
                    topicMessageType, nodeName, nodeSlaveUri));
        }
        final List<URI> publisherUris = new ArrayList<>();
        synchronized (this.masterRegistrationManager) {
            final TopicRegistrationInfo topicInfo = this.masterRegistrationManager.registerSubscriber(nodeName, nodeSlaveUri, topicName, topicMessageType);
            for (final NodeRegistrationInfo publisherNodeInfo : topicInfo.getPublishers()) {
                publisherUris.add(publisherNodeInfo.getNodeSlaveUri());
            }
            return publisherUris;
        }
    }

    /**
     * Unregister a {@link Subscriber}.
     *
     * @param nodeName  the {@link GraphName} of the {@link Node} offering the service
     * @param topicName the {@link GraphName} of the subscribed {@link TopicParticipant}
     * @return {@code true} if the {@link Subscriber} was registered
     */
    public final boolean unregisterSubscriber(final GraphName nodeName, final GraphName topicName) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Unregistering subscriber for %s on node %s.", topicName, nodeName));
        }
        synchronized (this.masterRegistrationManager) {
            return masterRegistrationManager.unregisterSubscriber(nodeName, topicName);
        }
    }

    /**
     * Register the caller as a {@link Publisher} of the specified topic.
     *
     * @param nodeName         the {@link GraphName} of the {@link Node} offering the service
     * @param nodeSlaveUri     the {@link URI} of the {@link Node}'s {@link SlaveServer}
     * @param topicName        the {@link GraphName} of the subscribed {@link TopicParticipant}
     * @param topicMessageType the message type of the topic
     * @return a {@link List} of the current {@link Subscriber}s to the
     * {@link Publisher}'s {@link TopicSystemState} in the form of XML-RPC
     * {@link URI}s for each {@link Subscriber}'s {@link SlaveServer}
     */
    public final List<URI> registerPublisher(final GraphName nodeName, final URI nodeSlaveUri, final GraphName topicName,
                                             final String topicMessageType) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format(
                    "Registering publisher %s with message type %s on node %s with URI %s.", topicName,
                    topicMessageType, nodeName, nodeSlaveUri));
        }
        final List<URI> subscriberSlaveUris = new ArrayList<>();
        synchronized (this.masterRegistrationManager) {
            final TopicRegistrationInfo topicInfo = this.masterRegistrationManager.registerPublisher(nodeName, nodeSlaveUri, topicName,
                    topicMessageType);
            for (final NodeRegistrationInfo publisherNodeInfo : topicInfo.getSubscribers()) {
                subscriberSlaveUris.add(publisherNodeInfo.getNodeSlaveUri());
            }

            this.publisherUpdate(topicInfo, subscriberSlaveUris);

            return subscriberSlaveUris;
        }
    }

    /**
     * Something has happened to the publishers for a topic. Tell every subscriber
     * about the current set of publishers.
     *
     * @param topicInfo           the topic information for the update
     * @param subscriberSlaveUris IRIs for all subscribers
     */
    private final void publisherUpdate(final TopicRegistrationInfo topicInfo, final List<URI> subscriberSlaveUris) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Publisher update: " + topicInfo.getTopicName());
        }
        final List<URI> publisherUris = new ArrayList<>();
        for (final NodeRegistrationInfo publisherNodeInfo : topicInfo.getPublishers()) {
            publisherUris.add(publisherNodeInfo.getNodeSlaveUri());
        }

        final GraphName topicName = topicInfo.getTopicName();
        for (final URI subscriberSlaveUri : subscriberSlaveUris) {
            this.contactSubscriberForPublisherUpdate(subscriberSlaveUri, topicName, publisherUris);
        }
    }

    /**
     * Contact a subscriber and send it a publisher update.
     *
     * @param subscriberSlaveUri the slave URI of the subscriber to contact
     * @param topicName          the name of the topic whose publisher URIs are being updated
     * @param publisherUris      the new list of publisher URIs to be sent to the subscriber
     */
    @VisibleForTesting
    protected final void contactSubscriberForPublisherUpdate(final URI subscriberSlaveUri, final GraphName topicName,
                                                             final List<URI> publisherUris) {
        final SlaveClient client = new SlaveClient(MASTER_NODE_NAME, subscriberSlaveUri);
        client.publisherUpdate(topicName, publisherUris);
    }

    /**
     * Unregister a {@link Publisher}.
     *
     * @param nodeName  the {@link GraphName} of the {@link Node} offering the service
     * @param topicName the {@link GraphName} of the subscribed {@link TopicParticipant}
     * @return {@code true} if the {@link Publisher} was unregistered
     */
    public final boolean unregisterPublisher(final GraphName nodeName, final GraphName topicName) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Unregistering publisher for %s on %s.", topicName, nodeName));
        }
        synchronized (this.masterRegistrationManager) {
            return masterRegistrationManager.unregisterPublisher(nodeName, topicName);
        }
    }

    /**
     * Returns a {@link NodeIdentifier} for the {@link Node} with the given name.
     * This API is for looking information about {@link Publisher}s and
     * {@link Subscriber}s. Use {@link #lookupService(GraphName)} instead to
     * lookup ROS-RPC {@link URI}s for {@link ServiceServer}s.
     *
     * @param nodeName name of {@link Node} to lookup
     * @return the {@link URI} for the {@link Node} slave server with the given
     * name, or {@code null} if there is no {@link Node} with the given
     * name
     */
    public final URI lookupNode(final GraphName nodeName) {
        synchronized (this.masterRegistrationManager) {
            final NodeRegistrationInfo node = this.masterRegistrationManager.getNodeRegistrationInfo(nodeName);
            if (node != null) {
                return node.getNodeSlaveUri();
            } else {
                return null;
            }
        }
    }

    /**
     * Get a {@link List} of all {@link TopicSystemState} message types.
     *
     * @param calledId the {@link Node} name of the caller
     * @return a list of the form [[topic 1 name, topic 1 message type], [topic 2
     * name, topic 2 message type], ...]
     */
    public final List<List<String>> getTopicTypes(GraphName calledId) {
        final List<List<String>> result = new ArrayList<>();
        synchronized (this.masterRegistrationManager) {
            for (final TopicRegistrationInfo topic : masterRegistrationManager.getAllTopics()) {
                result.add(List.of(topic.getTopicName().toString(), topic.getMessageType()));
            }
            return result;
        }
    }

    /**
     * Get the state of the ROS graph.
     *
     * <p>
     * This includes information about publishers, subscribers, and services.
     *
     * @return TODO(keith): Fill in.
     */
    public final List<List<Object>> getSystemState() {
        final List<List<Object>> result = new ArrayList<>();
        synchronized (this.masterRegistrationManager) {
            final Set<TopicRegistrationInfo> topics = masterRegistrationManager.getAllTopics();
            result.add(this.getSystemStatePublishers(topics));
            result.add(this.getSystemStateSubscribers(topics));
            result.add(this.getSystemStateServices());
            return result;
        }
    }

    /**
     * Get the system state for {@link Publisher}s.
     *
     * @param topics all topics known by the master
     * @return a {@link List} of the form [ [topic1,
     * [topic1Publisher1...topic1PublisherN]] ... ] where the
     * topicPublisherI instances are {@link Node} names
     */
    private final List<Object> getSystemStatePublishers(final Collection<TopicRegistrationInfo> topics) {
        final List<Object> result = new ArrayList<>();
        for (final TopicRegistrationInfo topic : topics) {
            if (topic.hasPublishers()) {
                final List<String> graphNames=topic.getSubscribers().stream()
                        .map(NodeRegistrationInfo::getNodeName)
                        .map(GraphName::toString)
                        .toList();

                final List<Object> topicInfo = List.of(topic.getTopicName().toString(),graphNames);
                result.add(topicInfo);
            }
        }
        return result;
    }

    /**
     * Get the system state for {@link Subscriber}s.
     *
     * @param topics all topics known by the master
     * @return a {@link List} of the form [ [topic1,
     * [topic1Subscriber1...topic1SubscriberN]] ... ] where the
     * topicSubscriberI instances are {@link Node} names
     */
    private final List<Object> getSystemStateSubscribers(Collection<TopicRegistrationInfo> topics) {
        final List<Object> result = new ArrayList<>();
        for (final TopicRegistrationInfo topic : topics) {
            if (topic.hasSubscribers()) {
                final List<String> graphNames=topic.getSubscribers().stream()
                        .map(NodeRegistrationInfo::getNodeName)
                        .map(GraphName::toString)
                        .toList();

                final List<Object> topicInfo = List.of(topic.getTopicName().toString(),graphNames);
                result.add(topicInfo);
            }
        }
        return result;
    }

    /**
     * Get the system state for {@link ServiceServer}s.
     *
     * @return a {@link List} of the form [ [service1,
     * [serviceProvider1...serviceProviderN]] ... ] where the
     * serviceProviderI instances are {@link Node} names
     */
    private final List<Object> getSystemStateServices() {
        final List<Object> result = new ArrayList<>();
        synchronized (this.masterRegistrationManager) {
            for (final ServiceRegistrationInfo service : this.masterRegistrationManager.getAllServices()) {
                final List<Object> topicInfo = List.of(service.getServiceName().toString()
                        , List.of(service.getServiceName().toString()));
                result.add(topicInfo);
            }
        }

        return result;
    }

    /**
     * Lookup the provider of a particular service.
     *
     * @param serviceName name of service
     * @return {@link URI} of the {@link SlaveServer} with the provided name, or
     * {@code null} if there is no such service.
     */
    public final URI lookupService(final GraphName serviceName) {
        synchronized (this.masterRegistrationManager) {
            final ServiceRegistrationInfo service = this.masterRegistrationManager.getServiceRegistrationInfo(serviceName);
            if (service != null) {
                return service.getServiceUri();
            } else {
                return null;
            }
        }
    }

    /**
     * Get a list of all topics published for the give subgraph.
     *
     * @param caller   name of the caller
     * @param subgraph subgraph containing the requested {@link TopicSystemState}s,
     *                 relative to caller
     * @return a {@link List} of {@link List}s where the nested {@link List}s
     * contain, in order, the {@link TopicSystemState} name and
     * {@link TopicSystemState} message type
     */
    public final List<Object> getPublishedTopics(final GraphName caller, final GraphName subgraph) {
        final List<Object> result = new ArrayList<>();
        synchronized (this.masterRegistrationManager) {
            // TODO(keith): Filter topics according to subgraph.

            for (final TopicRegistrationInfo topic : this.masterRegistrationManager.getAllTopics()) {
                if (topic.hasPublishers()) {
                    result.add(List.of(topic.getTopicName().toString(), topic.getMessageType()));
                }
            }
            return result;
        }
    }

    @Override
    public final void onNodeReplacement(final NodeRegistrationInfo nodeInfo) {
        // A node in the registration manager is being replaced. Contact the node
        // and tell it to shut down.
        if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(String.format("Existing node %s with slave URI %s will be shutdown.",
                    nodeInfo.getNodeName(), nodeInfo.getNodeSlaveUri()));
        }

        final SlaveClient client = new SlaveClient(MASTER_NODE_NAME, nodeInfo.getNodeSlaveUri());
        client.shutdown("Replaced by new slave");
    }

    @Override
    public final void shutdown() {
        this.superShutdown();
        this.shutdownFinalization();
    }
}
