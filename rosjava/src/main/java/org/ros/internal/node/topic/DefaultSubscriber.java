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

package org.ros.internal.node.topic;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;



import org.ros.concurrent.ListenerGroup;
import org.ros.concurrent.SignalRunnable;
import org.ros.internal.message.Message;
import org.ros.internal.node.server.NodeIdentifier;
import org.ros.internal.transport.ProtocolNames;
import org.ros.internal.transport.queue.IncomingMessageQueue;
import org.ros.internal.transport.tcp.TcpClientManager;
import org.ros.message.MessageDeserializer;
import org.ros.message.MessageListener;
import org.ros.node.topic.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of a {@link Subscriber}.
 *
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class DefaultSubscriber<T extends Message> extends DefaultTopicParticipant implements Subscriber<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /**
   * The maximum delay before shutdown will begin even if all
   * {@link SubscriberListener}s have not yet returned from their
   * {@link SubscriberListener#onShutdown(Subscriber)} callback.
   */
  private static final int DEFAULT_SHUTDOWN_TIMEOUT = 5;
  private static final TimeUnit DEFAULT_SHUTDOWN_TIMEOUT_UNITS = TimeUnit.SECONDS;

  private final NodeIdentifier nodeIdentifier;
  private final ScheduledExecutorService executorService;
  private final IncomingMessageQueue<T> incomingMessageQueue;
  private final Set<PublisherIdentifier> knownPublishers = Sets.newHashSet();
  private final TcpClientManager tcpClientManager;
  private final Object mutex = new Object();;

  /**
   * Manages the {@link SubscriberListener}s for this {@link Subscriber}.
   */
  private final ListenerGroup<SubscriberListener<T>> subscriberListeners;

  public static <S extends Message> DefaultSubscriber<S> newDefault(NodeIdentifier nodeIdentifier,
      TopicDeclaration description, ScheduledExecutorService executorService,
      MessageDeserializer<S> deserializer) {
    return new DefaultSubscriber<S>(nodeIdentifier, description, deserializer, executorService);
  }

  private DefaultSubscriber(final NodeIdentifier nodeIdentifier,final  TopicDeclaration topicDeclaration,
                            final MessageDeserializer<T> deserializer, final ScheduledExecutorService executorService) {
    super(topicDeclaration);
    this.nodeIdentifier = nodeIdentifier;
    this.executorService = executorService;
    this.incomingMessageQueue = new IncomingMessageQueue<T>(deserializer, executorService);

    this.tcpClientManager = new TcpClientManager(executorService);

    final SubscriberHandshakeHandler<T> subscriberHandshakeHandler =
        new SubscriberHandshakeHandler<T>(toDeclaration().toConnectionHeader(),
            incomingMessageQueue, executorService);
    this.tcpClientManager.addNamedChannelHandler(subscriberHandshakeHandler);
    this.subscriberListeners = new ListenerGroup<>(executorService);
    final LoggingSubscriberListener<T> loggingSubscriberListener=new LoggingSubscriberListener();
    this.subscriberListeners.add(loggingSubscriberListener);
  }

  public SubscriberIdentifier toIdentifier() {
    return new SubscriberIdentifier(nodeIdentifier, getTopicDeclaration().getIdentifier());
  }

  public SubscriberDeclaration toDeclaration() {
    return new SubscriberDeclaration(toIdentifier(), getTopicDeclaration());
  }

  public final Set<String> getSupportedProtocols() {
    return ProtocolNames.SUPPORTED;
  }

  @Override
  public boolean getLatchMode() {
    return incomingMessageQueue.getLatchMode();
  }

  @Override
  public void addMessageListener(MessageListener<T> messageListener, int limit) {
    incomingMessageQueue.addListener(messageListener, limit);
  }

  @Override
  public void addMessageListener(MessageListener<T> messageListener) {
    addMessageListener(messageListener, 1);
  }

  @Override
  public boolean removeMessageListener(MessageListener<T> messageListener) {
    return incomingMessageQueue.removeListener(messageListener);
  }

  @Override
  public void removeAllMessageListeners() {
    incomingMessageQueue.removeAllListeners();
  }

  @VisibleForTesting
  public void addPublisher(PublisherIdentifier publisherIdentifier, InetSocketAddress address) {
    synchronized (mutex) {
      // TODO(damonkohler): If the connection is dropped, knownPublishers should
      // be updated.
      if (this.knownPublishers.contains(publisherIdentifier)) {
        return;
      }
      this.tcpClientManager.connect(toString(), address);
      // TODO(damonkohler): knownPublishers is duplicate information that is
      // already available to the TopicParticipantManager.
      this.knownPublishers.add(publisherIdentifier);
      this.signalOnNewPublisher(publisherIdentifier);
    }
  }

  /**
   * Updates the list of {@link Publisher}s for the topic that this
   * {@link Subscriber} is interested in.
   *
   * @param publisherIdentifiers
   *          {@link Collection} of {@link PublisherIdentifier}s for the
   *          subscribed topic
   */
  public void updatePublishers(Collection<PublisherIdentifier> publisherIdentifiers) {
    for (final PublisherIdentifier publisherIdentifier : publisherIdentifiers) {
      executorService.execute(new UpdatePublisherRunnable<T>(this, nodeIdentifier,
          publisherIdentifier));
    }
  }

  @Override
  public void shutdown(long timeout, TimeUnit unit) {
    signalOnShutdown(timeout, unit);
    incomingMessageQueue.shutdown();
    tcpClientManager.shutdown();
    subscriberListeners.shutdown();
  }

  @Override
  public void shutdown() {
    shutdown(DEFAULT_SHUTDOWN_TIMEOUT, DEFAULT_SHUTDOWN_TIMEOUT_UNITS);
  }

  @Override
  public void addSubscriberListener(SubscriberListener<T> listener) {
    subscriberListeners.add(listener);
  }

  /**
   * Signal all {@link SubscriberListener}s that the {@link Subscriber} has
   * successfully registered with the master.
   * <p>
   * Each listener is called in a separate thread.
   */
  @Override
  public void signalOnMasterRegistrationSuccess() {
    final Subscriber<T> subscriber = this;
    subscriberListeners.signal(new SignalRunnable<SubscriberListener<T>>() {
      @Override
      public void run(SubscriberListener<T> listener) {
        listener.onMasterRegistrationSuccess(subscriber);
      }
    });
  }

  /**
   * Signal all {@link SubscriberListener}s that the {@link Subscriber} has
   * failed to register with the master.
   *
   * <p>
   * Each listener is called in a separate thread.
   */
  @Override
  public void signalOnMasterRegistrationFailure() {
    final Subscriber<T> subscriber = this;
    subscriberListeners.signal(listener -> listener.onMasterRegistrationFailure(subscriber));
  }

  /**
   * Signal all {@link SubscriberListener}s that the {@link Subscriber} has
   * successfully unregistered with the master.
   * <p>
   * Each listener is called in a separate thread.
   */
  @Override
  public void signalOnMasterUnregistrationSuccess() {
    final Subscriber<T> subscriber = this;
    subscriberListeners.signal(listener -> listener.onMasterUnregistrationSuccess(subscriber));
  }

  /**
   * Signal all {@link SubscriberListener}s that the {@link Subscriber} has
   * failed to unregister with the master.
   * <p>
   * Each listener is called in a separate thread.
   */
  @Override
  public void signalOnMasterUnregistrationFailure() {
    final Subscriber<T> subscriber = this;
    this.subscriberListeners.signal(listener -> listener.onMasterUnregistrationFailure(subscriber));
  }

  /**
   * Signal all {@link SubscriberListener}s that a new {@link Publisher} has
   * connected.
   * <p>
   * Each listener is called in a separate thread.
   */
  private final void signalOnNewPublisher(final PublisherIdentifier publisherIdentifier) {
    final Subscriber<T> subscriber = this;
    this.subscriberListeners.signal(listener -> listener.onNewPublisher(subscriber, publisherIdentifier));
  }

  /**
   * Signal all {@link SubscriberListener}s that the {@link Subscriber} has shut
   * down.
   * <p>
   * Each listener is called in a separate thread.
   */
  private final void signalOnShutdown(long timeout, TimeUnit unit) {
    final Subscriber<T> subscriber = this;
    try {
      this.subscriberListeners.signal(listener -> listener.onShutdown(subscriber), timeout, unit);
    } catch (InterruptedException e) {
      // Ignored since we do not guarantee that all listeners will finish before
      // shutdown begins.
    }
  }

  @Override
  public final String toString() {
    return "Subscriber<" + this.getTopicDeclaration() + ">";
  }
}
