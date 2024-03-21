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

package org.ros.internal.transport.tcp;

import com.google.common.base.Preconditions;



import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.HeapChannelBufferFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.ros.address.AdvertiseAddress;
import org.ros.address.BindAddress;
import org.ros.internal.node.service.ServiceManager;
import org.ros.internal.node.topic.TopicParticipantManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The TCP server which is used for data communication between publishers and
 * subscribers or between a service and a service client.
 *
 * <p>
 * This server is used after publishers, subscribers, services and service
 * clients have been told about each other by the master.
 *
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class TcpRosServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final BindAddress bindAddress;
    private final AdvertiseAddress advertiseAddress;
    private final TopicParticipantManager topicParticipantManager;
    private final ServiceManager serviceManager;
    private final ExecutorService executorService;

    private ChannelFactory channelFactory;
    private ServerBootstrap bootstrap;
    private Channel outgoingChannel;
    private ChannelGroup incomingChannelGroup;

    /**
     *
     * @param bindAddress
     * @param advertiseAddress
     * @param topicParticipantManager
     * @param serviceManager
     * @param executorService {@link ScheduledExecutorService} is preferred, {@link java.util.concurrent.Executors#newCachedThreadPool()} is another alternative.
     */
    public TcpRosServer(
            final BindAddress bindAddress
            ,final  AdvertiseAddress advertiseAddress
            ,final TopicParticipantManager topicParticipantManager
            ,final ServiceManager serviceManager
            ,final ExecutorService executorService) {
        this.bindAddress = bindAddress;
        this.advertiseAddress = advertiseAddress;
        this.topicParticipantManager = topicParticipantManager;
        this.serviceManager = serviceManager;
        this.executorService = executorService;
    }

    public void start() {
        Preconditions.checkState(outgoingChannel == null);
        this.channelFactory = new NioServerSocketChannelFactory(executorService, executorService);
        this.bootstrap = new ServerBootstrap(channelFactory);
        this.bootstrap.setOption("child.bufferFactory",
                new HeapChannelBufferFactory(ByteOrder.LITTLE_ENDIAN));
        this.bootstrap.setOption("child.keepAlive", true);
        this.incomingChannelGroup = new DefaultChannelGroup();
        this.bootstrap.setPipelineFactory(new TcpServerPipelineFactory(incomingChannelGroup,
                topicParticipantManager, serviceManager));

        this.outgoingChannel = bootstrap.bind(bindAddress.toInetSocketAddress());
        this.advertiseAddress.setPortSupplier(() -> ((InetSocketAddress) outgoingChannel.getLocalAddress()).getPort());
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Bound to: " + bindAddress + " Advertising: " + advertiseAddress);
        }
    }

    /**
     * Close all incoming connections and the server socket.
     *
     * <p>
     * Calling this method more than once has no effect.
     */
    public final void shutdown() {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Shutting down: " + getAddress());
        }

        if (this.outgoingChannel != null) {
            this.outgoingChannel.close().awaitUninterruptibly();
        }
        if (this.incomingChannelGroup != null) {
            this.incomingChannelGroup.close().awaitUninterruptibly();
        }
        // NOTE(damonkohler): We are purposely not calling
        // channelFactory.releaseExternalResources() or
        // bootstrap.releaseExternalResources() since only external resources are
        // the ExecutorService and control of that must remain with the overall
        // application.
        this.outgoingChannel = null;
        this.incomingChannelGroup = null;
    }

    /**
     * @return the advertise-able {@link InetSocketAddress} of this
     * {@link TcpRosServer}
     */
    public InetSocketAddress getAddress() {
        return advertiseAddress.toInetSocketAddress();
    }

    /**
     * @return the {@link AdvertiseAddress} of this {@link TcpRosServer}
     */
    public AdvertiseAddress getAdvertiseAddress() {
        return advertiseAddress;
    }
}
