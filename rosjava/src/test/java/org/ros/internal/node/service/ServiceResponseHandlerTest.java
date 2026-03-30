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

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.junit.jupiter.api.Test;
import org.ros.exception.RemoteException;
import org.ros.internal.node.response.StatusCode;
import org.ros.message.MessageDeserializer;
import org.ros.node.service.ServiceResponseListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Spyros Koukas
 */
class ServiceResponseHandlerTest {

  @Test
  void channelClosedFailsAllPendingResponses() throws Exception {
    final ConcurrentLinkedQueue<ServiceResponseListener<String>> listeners = new ConcurrentLinkedQueue<>();
    final List<String> failures = new ArrayList<>();
    listeners.add(failureListener(failures));
    listeners.add(failureListener(failures));

    final ServiceResponseHandler<String> handler =
        new ServiceResponseHandler<>(listeners, mock(MessageDeserializer.class), new DirectExecutorService());

    handler.channelClosed(mock(ChannelHandlerContext.class), mock(ChannelStateEvent.class));

    assertEquals(2, failures.size());
    assertTrue(failures.get(0).contains("closed"));
    assertTrue(failures.get(1).contains("closed"));
  }

  @Test
  void successfulResponseThenChannelCloseFailsOnlyRemainingListeners() throws Exception {
    final ConcurrentLinkedQueue<ServiceResponseListener<String>> listeners = new ConcurrentLinkedQueue<>();
    final AtomicReference<String> success = new AtomicReference<>();
    final List<String> failures = new ArrayList<>();
    final MessageDeserializer<String> deserializer = mock(MessageDeserializer.class);
    when(deserializer.deserialize(ChannelBuffers.EMPTY_BUFFER)).thenReturn("ok");

    listeners.add(new ServiceResponseListener<>() {
      @Override
      public void onSuccess(final String response) {
        success.set(response);
      }

      @Override
      public void onFailure(final RemoteException e) {
        throw new AssertionError("Expected first listener success.");
      }
    });
    listeners.add(failureListener(failures));

    final ServiceResponseHandler<String> handler =
        new ServiceResponseHandler<>(listeners, deserializer, new DirectExecutorService());

    final ServiceServerResponse response = new ServiceServerResponse();
    response.setErrorCode(StatusCode.SUCCESS.toInt());
    response.setMessage(ChannelBuffers.EMPTY_BUFFER);

    final MessageEvent messageEvent = mock(MessageEvent.class);
    when(messageEvent.getMessage()).thenReturn(response);

    handler.messageReceived(mock(ChannelHandlerContext.class), messageEvent);
    handler.channelClosed(mock(ChannelHandlerContext.class), mock(ChannelStateEvent.class));

    assertEquals("ok", success.get());
    assertEquals(1, failures.size());
    assertTrue(failures.get(0).contains("closed"));
  }

  @Test
  void rejectedExecutorStillFailsPendingResponses() throws Exception {
    final ConcurrentLinkedQueue<ServiceResponseListener<String>> listeners = new ConcurrentLinkedQueue<>();
    final List<String> failures = new ArrayList<>();
    listeners.add(failureListener(failures));

    final ServiceResponseHandler<String> handler =
        new ServiceResponseHandler<>(listeners, mock(MessageDeserializer.class),
            new RejectingExecutorService());

    handler.channelClosed(mock(ChannelHandlerContext.class), mock(ChannelStateEvent.class));

    assertEquals(1, failures.size());
    assertTrue(failures.get(0).contains("closed"));
  }

  @Test
  void rejectedExecutorStillDeliversSuccessfulResponses() {
    final ConcurrentLinkedQueue<ServiceResponseListener<String>> listeners = new ConcurrentLinkedQueue<>();
    final AtomicReference<String> success = new AtomicReference<>();
    final MessageDeserializer<String> deserializer = mock(MessageDeserializer.class);
    when(deserializer.deserialize(ChannelBuffers.EMPTY_BUFFER)).thenReturn("ok");
    listeners.add(new ServiceResponseListener<>() {
      @Override
      public void onSuccess(final String response) {
        success.set(response);
      }

      @Override
      public void onFailure(final RemoteException e) {
        throw new AssertionError("Expected success.");
      }
    });

    final ServiceResponseHandler<String> handler =
        new ServiceResponseHandler<>(listeners, deserializer, new RejectingExecutorService());

    final ServiceServerResponse response = new ServiceServerResponse();
    response.setErrorCode(StatusCode.SUCCESS.toInt());
    response.setMessage(ChannelBuffers.EMPTY_BUFFER);
    final MessageEvent messageEvent = mock(MessageEvent.class);
    when(messageEvent.getMessage()).thenReturn(response);

    handler.messageReceived(mock(ChannelHandlerContext.class), messageEvent);

    assertEquals("ok", success.get());
  }

  @Test
  void repeatedTerminalEventsDoNotFailListenersTwice() throws Exception {
    final ConcurrentLinkedQueue<ServiceResponseListener<String>> listeners = new ConcurrentLinkedQueue<>();
    final List<String> failures = new ArrayList<>();
    listeners.add(failureListener(failures));

    final ServiceResponseHandler<String> handler =
        new ServiceResponseHandler<>(listeners, mock(MessageDeserializer.class), new DirectExecutorService());

    handler.channelClosed(mock(ChannelHandlerContext.class), mock(ChannelStateEvent.class));
    handler.channelDisconnected(mock(ChannelHandlerContext.class), mock(ChannelStateEvent.class));

    assertEquals(1, failures.size());
  }

  @Test
  void deserializationFailureFailsThePendingResponse() {
    final ConcurrentLinkedQueue<ServiceResponseListener<String>> listeners = new ConcurrentLinkedQueue<>();
    final List<String> failures = new ArrayList<>();
    final MessageDeserializer<String> deserializer = mock(MessageDeserializer.class);
    when(deserializer.deserialize(ChannelBuffers.EMPTY_BUFFER))
        .thenThrow(new IllegalStateException("malformed response"));
    listeners.add(failureListener(failures));

    final ServiceResponseHandler<String> handler =
        new ServiceResponseHandler<>(listeners, deserializer, new DirectExecutorService());

    final ServiceServerResponse response = new ServiceServerResponse();
    response.setErrorCode(StatusCode.SUCCESS.toInt());
    response.setMessage(ChannelBuffers.EMPTY_BUFFER);
    final MessageEvent messageEvent = mock(MessageEvent.class);
    when(messageEvent.getMessage()).thenReturn(response);

    handler.messageReceived(mock(ChannelHandlerContext.class), messageEvent);

    assertEquals(1, failures.size());
    assertTrue(failures.get(0).contains("malformed response"));
  }

  @Test
  void listenerSuccessExceptionIsNotConvertedIntoFailureCallback() {
    final ConcurrentLinkedQueue<ServiceResponseListener<String>> listeners = new ConcurrentLinkedQueue<>();
    final AtomicReference<String> failure = new AtomicReference<>();
    final MessageDeserializer<String> deserializer = mock(MessageDeserializer.class);
    when(deserializer.deserialize(ChannelBuffers.EMPTY_BUFFER)).thenReturn("ok");
    listeners.add(new ServiceResponseListener<>() {
      @Override
      public void onSuccess(final String response) {
        throw new IllegalStateException("listener boom");
      }

      @Override
      public void onFailure(final RemoteException e) {
        failure.set(e.getMessage());
      }
    });

    final ServiceResponseHandler<String> handler =
        new ServiceResponseHandler<>(listeners, deserializer, new DirectExecutorService());

    final ServiceServerResponse response = new ServiceServerResponse();
    response.setErrorCode(StatusCode.SUCCESS.toInt());
    response.setMessage(ChannelBuffers.EMPTY_BUFFER);
    final MessageEvent messageEvent = mock(MessageEvent.class);
    when(messageEvent.getMessage()).thenReturn(response);

    final IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> handler.messageReceived(mock(ChannelHandlerContext.class), messageEvent));

    assertEquals("listener boom", exception.getMessage());
    assertEquals(null, failure.get());
  }

  @Test
  void rejectedExecutorFailureCallbackExceptionDoesNotBlockLaterFailures() throws Exception {
    final ConcurrentLinkedQueue<ServiceResponseListener<String>> listeners = new ConcurrentLinkedQueue<>();
    final AtomicReference<String> secondFailure = new AtomicReference<>();
    listeners.add(new ServiceResponseListener<>() {
      @Override
      public void onSuccess(final String response) {
        throw new AssertionError("Expected failure.");
      }

      @Override
      public void onFailure(final RemoteException e) {
        throw new IllegalStateException("first failure listener boom");
      }
    });
    listeners.add(new ServiceResponseListener<>() {
      @Override
      public void onSuccess(final String response) {
        throw new AssertionError("Expected failure.");
      }

      @Override
      public void onFailure(final RemoteException e) {
        secondFailure.set(e.getMessage());
      }
    });

    final ServiceResponseHandler<String> handler =
        new ServiceResponseHandler<>(listeners, mock(MessageDeserializer.class),
            new RejectingExecutorService());

    handler.channelClosed(mock(ChannelHandlerContext.class), mock(ChannelStateEvent.class));

    assertTrue(secondFailure.get() != null && secondFailure.get().contains("closed"));
  }

  private static ServiceResponseListener<String> failureListener(final List<String> failures) {
    return new ServiceResponseListener<>() {
      @Override
      public void onSuccess(final String response) {
        throw new AssertionError("Expected failure.");
      }

      @Override
      public void onFailure(final RemoteException e) {
        failures.add(e.getMessage());
      }
    };
  }

  private static final class DirectExecutorService extends AbstractExecutorService {
    private volatile boolean shutdown;

    @Override
    public void shutdown() {
      this.shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      this.shutdown = true;
      return new ArrayList<>();
    }

    @Override
    public boolean isShutdown() {
      return this.shutdown;
    }

    @Override
    public boolean isTerminated() {
      return this.shutdown;
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) {
      return this.shutdown;
    }

    @Override
    public void execute(final Runnable command) {
      command.run();
    }
  }

  private static final class RejectingExecutorService extends AbstractExecutorService {
    @Override
    public void shutdown() {
    }

    @Override
    public List<Runnable> shutdownNow() {
      return new ArrayList<>();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) {
      return false;
    }

    @Override
    public void execute(final Runnable command) {
      throw new RejectedExecutionException("rejected");
    }
  }
}
