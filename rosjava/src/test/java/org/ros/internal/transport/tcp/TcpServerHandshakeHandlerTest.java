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

package org.ros.internal.transport.tcp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.MessageEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ros.internal.node.topic.DefaultPublisher;
import org.ros.internal.node.topic.TopicParticipantManager;
import org.ros.internal.node.service.ServiceManager;
import org.ros.internal.transport.ConnectionHeader;
import org.ros.internal.transport.ConnectionHeaderFields;
import org.ros.namespace.GraphName;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Spyros Koukas
 */
class TcpServerHandshakeHandlerTest {

  @Test
  void subscriberHandshakeRegistersListenerInsteadOfBlockingInline() throws Exception {
    TopicParticipantManager topicParticipantManager = mock(TopicParticipantManager.class);
    ServiceManager serviceManager = mock(ServiceManager.class);
    DefaultPublisher publisher = mock(DefaultPublisher.class);
    ChannelHandlerContext context = mock(ChannelHandlerContext.class);
    MessageEvent messageEvent = mock(MessageEvent.class);
    Channel channel = mock(Channel.class);
    ChannelConfig channelConfig = mock(ChannelConfig.class);
    ChannelPipeline pipeline = mock(ChannelPipeline.class);
    ChannelFuture writeFuture = mock(ChannelFuture.class);
    ChannelBuffer outgoingBuffer = mock(ChannelBuffer.class);

    GraphName topicName = GraphName.of("/chatter");
    ConnectionHeader incomingHeader = new ConnectionHeader();
    incomingHeader.addField(ConnectionHeaderFields.TOPIC, topicName.toString());
    incomingHeader.addField(ConnectionHeaderFields.CALLER_ID, "/listener");
    incomingHeader.addField(ConnectionHeaderFields.TYPE, "*");
    incomingHeader.addField(ConnectionHeaderFields.MD5_CHECKSUM, "*");
    incomingHeader.addField(ConnectionHeaderFields.TCP_NODELAY, "1");

    when(topicParticipantManager.hasPublisher(topicName)).thenReturn(true);
    doReturn(publisher).when(topicParticipantManager).getPublisher(topicName);
    when(publisher.finishHandshake(any(ConnectionHeader.class))).thenReturn(outgoingBuffer);
    when(context.getChannel()).thenReturn(channel);
    when(messageEvent.getMessage()).thenReturn(incomingHeader.encode());
    when(messageEvent.getChannel()).thenReturn(channel);
    when(channel.getPipeline()).thenReturn(pipeline);
    when(channel.getConfig()).thenReturn(channelConfig);
    when(channel.write(outgoingBuffer)).thenReturn(writeFuture);
    when(writeFuture.isSuccess()).thenReturn(true);

    TcpServerHandshakeHandler handler =
        new TcpServerHandshakeHandler(topicParticipantManager, serviceManager);

    assertDoesNotThrow(() -> handler.messageReceived(context, messageEvent));

    ArgumentCaptor<ChannelFutureListener> listenerCaptor =
        ArgumentCaptor.forClass(ChannelFutureListener.class);
    verify(writeFuture).addListener(listenerCaptor.capture());
    verify(publisher, never()).addSubscriber(any(), eq(channel));
    verify(pipeline, never()).replace(eq(handler), eq("DiscardHandler"), any());
    assertNotNull(listenerCaptor.getValue());

    listenerCaptor.getValue().operationComplete(writeFuture);

    verify(publisher).addSubscriber(any(), eq(channel));
    verify(pipeline).replace(eq(handler), eq("DiscardHandler"), any());
  }
}
