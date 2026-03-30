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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ros.exception.RemoteException;
import org.ros.internal.message.Message;
import org.ros.internal.transport.ConnectionHeader;
import org.ros.message.MessageDeserializer;
import org.ros.message.MessageFactory;
import org.ros.message.MessageSerializer;
import org.ros.namespace.GraphName;
import org.ros.node.service.ServiceCaller;
import org.ros.node.service.ServiceResponseListener;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NonPersistentServiceClientTest {

  private ScheduledExecutorService executorService;

  @AfterEach
  void after() {
    if (this.executorService != null) {
      this.executorService.shutdownNow();
    }
  }

  @Test
  void callFailsImmediatelyAfterShutdown() {
    final NonPersistentServiceClient<Message, Message> serviceClient = newClient();
    final AtomicInteger failures = new AtomicInteger();
    final AtomicReference<String> message = new AtomicReference<>();

    serviceClient.shutdown();
    serviceClient.call(mock(Message.class), new ServiceResponseListener<>() {
      @Override
      public void onSuccess(final Message response) {
        throw new AssertionError("Expected shutdown failure.");
      }

      @Override
      public void onFailure(final RemoteException e) {
        failures.incrementAndGet();
        message.set(e.getMessage());
      }
    });

    assertEquals(1, failures.get());
    assertTrue(message.get() != null && message.get().contains("shut down"));
  }

  @Test
  void shutdownNotifiesRemovalOnlyOnce() {
    final AtomicInteger removals = new AtomicInteger();
    final NonPersistentServiceClient<Message, Message> serviceClient = newClient(ignored -> removals.incrementAndGet());

    serviceClient.shutdown();
    serviceClient.shutdown();

    assertEquals(1, removals.get());
  }

  @SuppressWarnings("unchecked")
  private NonPersistentServiceClient<Message, Message> newClient() {
    return newClient(ignored -> {});
  }

  @SuppressWarnings("unchecked")
  private NonPersistentServiceClient<Message, Message> newClient(final Consumer<ServiceCaller<?, ?>> shutdownListener) {
    this.executorService = Executors.newSingleThreadScheduledExecutor();
    final ServiceDeclaration serviceDeclaration = mock(ServiceDeclaration.class);
    final MessageSerializer<Message> serializer = mock(MessageSerializer.class);
    final MessageDeserializer<Message> deserializer = mock(MessageDeserializer.class);
    final MessageFactory messageFactory = mock(MessageFactory.class);
    when(serviceDeclaration.toConnectionHeader()).thenReturn(new ConnectionHeader());
    when(serviceDeclaration.getType()).thenReturn("test/Service");
    doNothing().when(serializer).serialize(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    return NonPersistentServiceClient.newDefault(GraphName.of("/test_node"), serviceDeclaration, serializer,
        deserializer, messageFactory, this.executorService, serviceDeclaration::getUri, shutdownListener);
  }
}
