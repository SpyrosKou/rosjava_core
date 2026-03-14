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
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.ros.exception.RosRuntimeException;
import org.ros.internal.message.Message;
import org.ros.internal.node.server.NodeIdentifier;
import org.ros.internal.node.service.ServiceManager;
import org.ros.internal.node.service.ServiceResponseEncoder;
import org.ros.internal.node.topic.DefaultPublisher;
import org.ros.internal.node.topic.SubscriberIdentifier;
import org.ros.internal.node.topic.TopicIdentifier;
import org.ros.internal.node.topic.TopicParticipantManager;
import org.ros.internal.transport.ConnectionHeader;
import org.ros.internal.transport.ConnectionHeaderFields;
import org.ros.namespace.GraphName;
import org.ros.node.service.ChannelBufferServiceServer;
import org.ros.node.service.ServiceServer;

/**
 * A {@link ChannelHandler} which will process the TCP server handshake.
 *
 * @author damonkohler@google.com (Damon Kohler)
 * @author kwc@willowgarage.com (Ken Conley)
 */
final class TcpServerHandshakeHandler extends SimpleChannelHandler {

    private final TopicParticipantManager topicParticipantManager;
    private final ServiceManager serviceManager;

    TcpServerHandshakeHandler(
            final TopicParticipantManager topicParticipantManager
            ,final ServiceManager serviceManager) {
        this.topicParticipantManager = topicParticipantManager;
        this.serviceManager = serviceManager;
    }

    @Override
    public final void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ChannelBuffer incomingBuffer = (ChannelBuffer) e.getMessage();
        ChannelPipeline pipeline = e.getChannel().getPipeline();
        ConnectionHeader incomingHeader = ConnectionHeader.decode(incomingBuffer);
        if (incomingHeader.hasField(ConnectionHeaderFields.SERVICE)) {
            handleServiceHandshake(e, pipeline, incomingHeader);
        } else {
            handleSubscriberHandshake(ctx, pipeline, incomingHeader);
        }
    }

    private final void handleServiceHandshake(
            final MessageEvent messageEvent
            ,final ChannelPipeline pipeline
            ,final ConnectionHeader incomingHeader) {
        final GraphName serviceName = GraphName.of(incomingHeader.getField(ConnectionHeaderFields.SERVICE));
        Preconditions.checkState(this.serviceManager.hasServer(serviceName));
        final ChannelBufferServiceServer<? extends Message, ? extends Message> serviceServer = serviceManager.getServer(serviceName);
        messageEvent.getChannel().write(serviceServer.finishHandshake(incomingHeader));
        final String probe = incomingHeader.getField(ConnectionHeaderFields.PROBE);
        if ("1".equals(probe)) {
            messageEvent.getChannel().close();
        } else {
            pipeline.replace(TcpServerPipelineFactory.LENGTH_FIELD_PREPENDER, "ServiceResponseEncoder", new ServiceResponseEncoder());
            pipeline.replace(this, "ServiceRequestHandler", serviceServer.newRequestHandler());
        }
    }

    private final void handleSubscriberHandshake(
            final ChannelHandlerContext channelHandlerContext
            ,final ChannelPipeline pipeline
            ,final ConnectionHeader incomingConnectionHeader) {
        Preconditions.checkState(incomingConnectionHeader.hasField(ConnectionHeaderFields.TOPIC),
                "Handshake header missing field: " + ConnectionHeaderFields.TOPIC);
        final GraphName topicName =
                GraphName.of(incomingConnectionHeader.getField(ConnectionHeaderFields.TOPIC));
        Preconditions.checkState(topicParticipantManager.hasPublisher(topicName),
                "No publisher for topic: " + topicName);
        final DefaultPublisher<?> publisher = topicParticipantManager.getPublisher(topicName);
        final ChannelBuffer outgoingBuffer = publisher.finishHandshake(incomingConnectionHeader);
        final Channel channel = channelHandlerContext.getChannel();
        if (incomingConnectionHeader.hasField(ConnectionHeaderFields.TCP_NODELAY)) {
            final boolean tcpNoDelay = "1".equals(incomingConnectionHeader.getField(ConnectionHeaderFields.TCP_NODELAY));
            channel.getConfig().setOption("tcpNoDelay", tcpNoDelay);
        }
        final String nodeName = incomingConnectionHeader.getField(ConnectionHeaderFields.CALLER_ID);
        final SubscriberIdentifier subscriberIdentifier =
                new SubscriberIdentifier(NodeIdentifier.forName(nodeName), new TopicIdentifier(topicName));
        channel.write(outgoingBuffer).addListener((ChannelFuture future) -> {
            if (!future.isSuccess()) {
                Channels.fireExceptionCaught(channelHandlerContext,
                        new RosRuntimeException(future.getCause()));
                return;
            }
            publisher.addSubscriber(subscriberIdentifier, channel);

            // Once the handshake is complete, there will be nothing incoming on the
            // channel. So, we replace the handshake handler with a handler which will
            // drop everything.
            pipeline.replace(TcpServerHandshakeHandler.this, "DiscardHandler", new SimpleChannelHandler());
        });
    }
}
