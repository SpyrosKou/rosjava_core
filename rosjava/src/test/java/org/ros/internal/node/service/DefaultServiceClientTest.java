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

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ros.exception.RemoteException;
import org.ros.internal.message.Message;
import org.ros.internal.transport.ConnectionHeader;
import org.ros.internal.transport.tcp.TcpClient;
import org.ros.message.MessageDeserializer;
import org.ros.message.MessageFactory;
import org.ros.message.MessageSerializer;
import org.ros.namespace.GraphName;
import org.ros.node.service.ServiceResponseListener;

import java.lang.reflect.Field;
import java.nio.channels.ClosedChannelException;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Spyros Koukas
 */
class DefaultServiceClientTest {

  private ScheduledExecutorService executorService;

  @AfterEach
  void after() {
    if (this.executorService != null) {
      this.executorService.shutdownNow();
    }
  }

  @Test
  void callFailsListenerAndClearsQueueWhenWriteFutureFails() throws Exception {
    final DefaultServiceClient<Message, Message> serviceClient = newClient();
    final TcpClient tcpClient = mock(TcpClient.class);
    final Channel channel = mock(Channel.class);
    final ChannelFuture future = mock(ChannelFuture.class);
    final AtomicInteger failures = new AtomicInteger();
    final AtomicReference<String> message = new AtomicReference<>();

    when(tcpClient.getChannel()).thenReturn(channel);
    when(channel.isConnected()).thenReturn(true);
    when(tcpClient.write(any())).thenReturn(future);
    when(future.awaitUninterruptibly()).thenReturn(future);
    when(future.isSuccess()).thenReturn(false);
    when(future.getCause()).thenReturn(new ClosedChannelException());
    setPrivateField(serviceClient, "tcpClient", tcpClient);

    serviceClient.call(mock(Message.class), new ServiceResponseListener<>() {
      @Override
      public void onSuccess(final Message response) {
        throw new AssertionError("Expected write failure.");
      }

      @Override
      public void onFailure(final RemoteException e) {
        failures.incrementAndGet();
        message.set(e.getMessage());
      }
    });

    assertEquals(1, failures.get());
    assertTrue(message.get() != null && !message.get().isEmpty());
    assertTrue(responseListeners(serviceClient).isEmpty());
  }

  @Test
  void callFailsListenerImmediatelyWhenClientIsNotConnected() throws Exception {
    final DefaultServiceClient<Message, Message> serviceClient = newClient();
    final TcpClient tcpClient = mock(TcpClient.class);
    final Channel channel = mock(Channel.class);
    final AtomicInteger failures = new AtomicInteger();
    final AtomicReference<String> message = new AtomicReference<>();

    when(tcpClient.getChannel()).thenReturn(channel);
    when(channel.isConnected()).thenReturn(false);
    setPrivateField(serviceClient, "tcpClient", tcpClient);

    serviceClient.call(mock(Message.class), new ServiceResponseListener<>() {
      @Override
      public void onSuccess(final Message response) {
        throw new AssertionError("Expected disconnected client failure.");
      }

      @Override
      public void onFailure(final RemoteException e) {
        failures.incrementAndGet();
        message.set(e.getMessage());
      }
    });

    assertEquals(1, failures.get());
    assertTrue(message.get() != null && message.get().contains("not connected"));
    assertTrue(responseListeners(serviceClient).isEmpty());
  }

  @Test
  void callWriteFailureDoesNotCorruptPendingQueueForEqualListeners() throws Exception {
    final DefaultServiceClient<Message, Message> serviceClient = newClient();
    final TcpClient tcpClient = mock(TcpClient.class);
    final Channel channel = mock(Channel.class);
    final ChannelFuture firstFuture = mock(ChannelFuture.class);
    final ChannelFuture secondFuture = mock(ChannelFuture.class);
    final EqualListener firstListener = new EqualListener("first");
    final EqualListener secondListener = new EqualListener("second");

    when(tcpClient.getChannel()).thenReturn(channel);
    when(channel.isConnected()).thenReturn(true);
    when(tcpClient.write(any())).thenReturn(firstFuture, secondFuture);
    when(firstFuture.awaitUninterruptibly()).thenReturn(firstFuture);
    when(firstFuture.isSuccess()).thenReturn(true);
    when(secondFuture.awaitUninterruptibly()).thenReturn(secondFuture);
    when(secondFuture.isSuccess()).thenReturn(false);
    when(secondFuture.getCause()).thenReturn(new ClosedChannelException());
    setPrivateField(serviceClient, "tcpClient", tcpClient);

    serviceClient.call(mock(Message.class), firstListener);
    serviceClient.call(mock(Message.class), secondListener);

    assertEquals(0, firstListener.failureCount.get());
    assertEquals(1, secondListener.failureCount.get());

    final ServiceResponseListener<Message> queuedListener = responseListeners(serviceClient).poll();
    queuedListener.onSuccess(mock(Message.class));

    assertEquals(1, firstListener.successCount.get());
    assertEquals(0, secondListener.successCount.get());
  }

  @Test
  void callSerializerFailureFailsListenerImmediately() throws Exception {
    final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    this.executorService = executor;
    final ServiceDeclaration serviceDeclaration = mock(ServiceDeclaration.class);
    final MessageSerializer<Message> serializer = mock(MessageSerializer.class);
    final MessageDeserializer<Message> deserializer = mock(MessageDeserializer.class);
    final MessageFactory messageFactory = mock(MessageFactory.class);
    when(serviceDeclaration.toConnectionHeader()).thenReturn(new ConnectionHeader());
    final DefaultServiceClient<Message, Message> serviceClient =
        DefaultServiceClient.newDefault(GraphName.of("/test_node"), serviceDeclaration, serializer,
            deserializer, messageFactory, executor);
    final TcpClient tcpClient = mock(TcpClient.class);
    final Channel channel = mock(Channel.class);
    final AtomicInteger failures = new AtomicInteger();
    final AtomicReference<String> message = new AtomicReference<>();

    when(tcpClient.getChannel()).thenReturn(channel);
    when(channel.isConnected()).thenReturn(true);
    doThrow(new IllegalStateException("serialize failed")).when(serializer).serialize(any(), any());
    setPrivateField(serviceClient, "tcpClient", tcpClient);

    serviceClient.call(mock(Message.class), new ServiceResponseListener<>() {
      @Override
      public void onSuccess(final Message response) {
        throw new AssertionError("Expected serializer failure.");
      }

      @Override
      public void onFailure(final RemoteException e) {
        failures.incrementAndGet();
        message.set(e.getMessage());
      }
    });

    assertEquals(1, failures.get());
    assertTrue(message.get() != null && message.get().contains("serialize failed"));
    assertTrue(responseListeners(serviceClient).isEmpty());
  }

  @SuppressWarnings("unchecked")
  private DefaultServiceClient<Message, Message> newClient() {
    this.executorService = Executors.newSingleThreadScheduledExecutor();
    final ServiceDeclaration serviceDeclaration = mock(ServiceDeclaration.class);
    final MessageSerializer<Message> serializer = mock(MessageSerializer.class);
    final MessageDeserializer<Message> deserializer = mock(MessageDeserializer.class);
    final MessageFactory messageFactory = mock(MessageFactory.class);
    when(serviceDeclaration.toConnectionHeader()).thenReturn(new ConnectionHeader());
    doNothing().when(serializer).serialize(any(), any());
    return DefaultServiceClient.newDefault(GraphName.of("/test_node"), serviceDeclaration, serializer,
        deserializer, messageFactory, this.executorService);
  }

  @SuppressWarnings("unchecked")
  private static Queue<ServiceResponseListener<Message>> responseListeners(
      final DefaultServiceClient<Message, Message> serviceClient) throws Exception {
    final Field field = DefaultServiceClient.class.getDeclaredField("responseListeners");
    field.setAccessible(true);
    return (Queue<ServiceResponseListener<Message>>) field.get(serviceClient);
  }

  private static void setPrivateField(final Object target, final String fieldName, final Object value)
      throws Exception {
    final Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static final class EqualListener implements ServiceResponseListener<Message> {
    private final String id;
    private final AtomicInteger successCount = new AtomicInteger();
    private final AtomicInteger failureCount = new AtomicInteger();

    private EqualListener(final String id) {
      this.id = id;
    }

    @Override
    public void onSuccess(final Message response) {
      this.successCount.incrementAndGet();
    }

    @Override
    public void onFailure(final RemoteException e) {
      this.failureCount.incrementAndGet();
    }

    @Override
    public boolean equals(final Object obj) {
      return obj instanceof EqualListener;
    }

    @Override
    public int hashCode() {
      return 1;
    }

    @Override
    public String toString() {
      return this.id;
    }
  }
}
