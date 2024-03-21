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

package org.ros.node;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.concurrent.DefaultScheduledExecutorService;
import org.ros.internal.node.DefaultNodeFactory;
import org.ros.namespace.GraphName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Executes {@link NodeMain}s in separate threads.
 *
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class DefaultNodeMainExecutor implements NodeMainExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final NodeFactory nodeFactory;
    private final ScheduledExecutorService scheduledExecutorService;
    private final ConcurrentHashMap<GraphName, ConnectedNode> connectedNodes;
    private final BiMap<Node, NodeMain> nodeMains;

    private final class RegistrationListener implements NodeListener {
        @Override
        public final void onStart(final ConnectedNode connectedNode) {
            DefaultNodeMainExecutor.this.registerNode(connectedNode);
        }

        @Override
        public final void onShutdown(final Node node) {
        }

        @Override
        public final void onShutdownComplete(final Node node) {
            DefaultNodeMainExecutor.this.unregisterNode(node);
        }

        @Override
        public final void onError(final Node node, final Throwable throwable) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Node error.:" + ExceptionUtils.getStackTrace(throwable));
            }
            DefaultNodeMainExecutor.this.unregisterNode(node);
        }
    }


    /**
     * @return an instance of {@link DefaultNodeMainExecutor} that uses a
     * {@link ScheduledExecutorService} that is suitable for both
     * executing tasks immediately and scheduling tasks to execute in the
     * future
     */
    public static NodeMainExecutor newDefault() {
        return newDefault(new DefaultScheduledExecutorService());
    }

    public static NodeMainExecutor newDefault(final String executorServiceName,final String scheduledExecutorServiceName) {
        return newDefault(new DefaultScheduledExecutorService(executorServiceName,scheduledExecutorServiceName));
    }


    /**
     * @return an instance of {@link DefaultNodeMainExecutor} that uses the
     * supplied {@link ExecutorService}
     */
    public static final NodeMainExecutor newDefault(final ScheduledExecutorService executorService) {
        Objects.requireNonNull(executorService);
        return new DefaultNodeMainExecutor(new DefaultNodeFactory(executorService), executorService);
    }


    /**
     * @param nodeFactory              {@link NodeFactory} to use for node creation.
     * @param scheduledExecutorService {@link NodeMain}s will be executed using this
     */
    DefaultNodeMainExecutor(final NodeFactory nodeFactory,
                            final ScheduledExecutorService scheduledExecutorService) {
        this.nodeFactory = nodeFactory;
        this.scheduledExecutorService = scheduledExecutorService;
        this.connectedNodes = new ConcurrentHashMap<>();
        this.nodeMains = Maps.synchronizedBiMap(HashBiMap.<Node, NodeMain>create());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> DefaultNodeMainExecutor.this.shutdown()));
    }

    @Override
    public final ScheduledExecutorService getScheduledExecutorService() {
        return this.scheduledExecutorService;
    }

    @Override
    public final void execute(final NodeMain nodeMain, final NodeConfiguration nodeConfiguration,
                              final Collection<NodeListener> nodeListeners) {
        // NOTE(damonkohler): To avoid a race condition, we have to make our copy
        // of the NodeConfiguration in the current thread.
        final NodeConfiguration nodeConfigurationCopy = NodeConfiguration.copyOf(nodeConfiguration);
        nodeConfigurationCopy.setDefaultNodeName(nodeMain.getDefaultNodeName());
        Preconditions.checkNotNull(nodeConfigurationCopy.getNodeName(), "Node name not specified.");
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Starting node: " + nodeConfigurationCopy.getNodeName());
        }
        this.scheduledExecutorService.execute(() -> {
            final List<NodeListener> nodeListenersCopy = Lists.newArrayList();
            nodeListenersCopy.add(new RegistrationListener());
            nodeListenersCopy.add(nodeMain);
            if (nodeListeners != null) {
                nodeListenersCopy.addAll(nodeListeners);
            }
            // The new Node will call onStart().
            final Node node = nodeFactory.newNode(nodeConfigurationCopy, nodeListenersCopy);
            this.nodeMains.put(node, nodeMain);
        });
    }

    @Override
    public final void execute(final NodeMain nodeMain, final NodeConfiguration nodeConfiguration) {
        this.execute(nodeMain, nodeConfiguration, null);
    }

    @Override
    public final void shutdownNodeMain(final NodeMain nodeMain) {
        final Node node = this.nodeMains.inverse().get(nodeMain);
        if (node != null) {
            this.safelyShutdownNode(node);
        }
    }


    @Override
    public final void shutdown() {
        synchronized (this.connectedNodes) {
            final List<ConnectedNode> connectedNodesList = new ArrayList<>(this.connectedNodes.values());
            for (final ConnectedNode connectedNode : connectedNodesList) {
                this.safelyShutdownNode(connectedNode);
            }
        }
    }

    /**
     * Trap and log any exceptions while shutting down the supplied {@link Node}.
     *
     * @param node the {@link Node} to shut down
     */
    private final void safelyShutdownNode(final Node node) {

        if (Objects.nonNull(node)) {
            try {
                node.shutdown();

                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Shutdown successful:");
                }
            } catch (final Exception exception) {
                // Ignore spurious errors during shutdown.
                if (LOGGER.isErrorEnabled()) {
                    try {
                        LOGGER.error(String.format("Exception thrown while shutting down node : %s (%s) : Exception: %s", node.getName(), node.getUri(), ExceptionUtils.getStackTrace(exception)));
                    } catch (final Exception exceptionNested) {
                    }
                }
                // We don't expect any more callbacks from a node that throws an exception
                // while shutting down. So, we unregister it immediately.
                this.unregisterNode(node);
            }

        } else {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Attempt to shutdown null node.");
            }
        }


    }

    /**
     * Register a {@link ConnectedNode} with the {@link NodeMainExecutor}.
     * If a {@link ConnectedNode} with the same {@link GraphName} exists it will be shutdown and unregistered.
     *
     * @param connectedNode the {@link ConnectedNode} to register
     */
    private final void registerNode(final ConnectedNode connectedNode) {
        final GraphName nodeName = connectedNode.getName();

        synchronized (this.connectedNodes) {
            final ConnectedNode existingConnectedNode = connectedNodes.put(nodeName, connectedNode);

            if (Objects.nonNull(existingConnectedNode)) {
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error(String.format("Node name collision. Existing node %s (%s) will be shutdown.", nodeName, existingConnectedNode.getUri()));
                }
                try {
                    existingConnectedNode.shutdown();

                } catch (final Exception exception) {
                    if (LOGGER.isErrorEnabled()) {
                        LOGGER.error(String.format("Exception while shutting down node with  name collision. Existing node %s (%s).", nodeName, existingConnectedNode.getUri()));
                    }
                }
            }
        }
    }

    /**
     * Unregister a {@link Node} with the {@link NodeMainExecutor}.
     *
     * @param node the {@link Node} to unregister
     */
    private final void unregisterNode(final Node node) {
        if (node != null) {
            node.removeListeners();
            this.connectedNodes.remove(node.getName());
            this.nodeMains.remove(node);
        }
    }
}
