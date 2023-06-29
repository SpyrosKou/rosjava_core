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

import com.google.common.base.Preconditions;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.ros.concurrent.ListenerGroup;
import org.ros.internal.message.Message;
import org.ros.internal.node.server.NodeIdentifier;
import org.ros.internal.transport.ConnectionHeader;
import org.ros.internal.transport.ConnectionHeaderFields;
import org.ros.internal.transport.queue.OutgoingMessageQueue;
import org.ros.message.MessageFactory;
import org.ros.message.MessageSerializer;
import org.ros.node.topic.LoggingPublisherListener;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.PublisherListener;
import org.ros.node.topic.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of a {@link Publisher}.
 * 
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class DefaultPublisher<T extends Message> extends DefaultTopicParticipant implements Publisher<T> {


  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /**
   * The maximum delay before shutdown will begin even if all
   * {@link PublisherListener}s have not yet returned from their
   * {@link PublisherListener#onShutdown(Publisher)} callback.
   */
  private static final long DEFAULT_SHUTDOWN_TIMEOUT = 5;
  private static final TimeUnit DEFAULT_SHUTDOWN_TIMEOUT_UNITS = TimeUnit.SECONDS;

  /**
   * Queue of all messages being published by this {@link Publisher}.
   */
  private final OutgoingMessageQueue<T> outgoingMessageQueue;
  private final ListenerGroup<PublisherListener<T>> listenerGroup;
  private final NodeIdentifier nodeIdentifier;
  private final MessageFactory messageFactory;
  private CountDownLatch shutdownLatch;

  public DefaultPublisher(NodeIdentifier nodeIdentifier, TopicDeclaration topicDeclaration,
      MessageSerializer<T> serializer, MessageFactory messageFactory,
      ScheduledExecutorService executorService) {
    super(topicDeclaration);
    this.nodeIdentifier = nodeIdentifier;
    this.messageFactory = messageFactory;
    this.outgoingMessageQueue = new OutgoingMessageQueue<T>(serializer, executorService);
    listenerGroup = new ListenerGroup<>(executorService);
    final LoggingPublisherListener<T> loggingPublisherListener=new LoggingPublisherListener<>();
    listenerGroup.add(loggingPublisherListener);
  }

  @Override
  public final void setLatchMode(boolean enabled) {
    outgoingMessageQueue.setLatchMode(enabled);
  }

  @Override
  public final boolean getLatchMode() {
    return outgoingMessageQueue.getLatchMode();
  }

  /**
   * Sends shutdown signals and awaits for them to be received by
   * {@link DefaultPublisher#signalOnMasterUnregistrationSuccess()} or
   * {@link DefaultPublisher#signalOnMasterUnregistrationFailure()} before continuing shutdown
   */
  @Override
  public final void shutdown(long timeout, TimeUnit unit) {
    this.shutdownLatch = new CountDownLatch(listenerGroup.size());
    this.signalOnShutdown(timeout, unit);
    try {
      this.shutdownLatch.await(timeout, unit);
    } catch (final InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
    }
    this.outgoingMessageQueue.shutdown();
    this.listenerGroup.shutdown();
  }

  @Override
  public final void shutdown() {
    shutdown(DEFAULT_SHUTDOWN_TIMEOUT, DEFAULT_SHUTDOWN_TIMEOUT_UNITS);
  }

  public final PublisherIdentifier getIdentifier() {
    return new PublisherIdentifier(nodeIdentifier, getTopicDeclaration().getIdentifier());
  }

  public final PublisherDeclaration toDeclaration() {
    return PublisherDeclaration.newFromNodeIdentifier(nodeIdentifier, getTopicDeclaration());
  }

  @Override
  public final boolean hasSubscribers() {
    return outgoingMessageQueue.getNumberOfChannels() > 0;
  }

  @Override
  public final int getNumberOfSubscribers() {
    return outgoingMessageQueue.getNumberOfChannels();
  }

  @Override
  public final T newMessage() {
    return messageFactory.newFromType(getTopicDeclaration().getMessageType());
  }

  @Override
  public final void publish(T message) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(String.format("Publishing message %s on topic %s.", message, getTopicName()));
    }
    this.outgoingMessageQueue.add(message);
  }

  /**
   * Complete connection handshake on buffer. This generates the connection
   * header for this publisher to send and also updates the connection state of
   * this publisher.
   * 
   * @return encoded connection header from subscriber
   */
  public final ChannelBuffer finishHandshake(final ConnectionHeader incomingHeader) {
    final ConnectionHeader topicDefinitionHeader = getTopicDeclarationHeader();
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info("Subscriber handshake header: " + incomingHeader+"\n"+"Publisher handshake header: " + topicDefinitionHeader);
    }
    // TODO(damonkohler): Return errors to the subscriber over the wire.
    final String incomingType = incomingHeader.getField(ConnectionHeaderFields.TYPE);
    final String expectedType = topicDefinitionHeader.getField(ConnectionHeaderFields.TYPE);
    final boolean messageTypeMatches =
        incomingType.equals(expectedType)
            || incomingType.equals(Subscriber.TOPIC_MESSAGE_TYPE_WILDCARD);
    Preconditions.checkState(messageTypeMatches, "Unexpected message type " + incomingType + " != "
        + expectedType);
    final String incomingChecksum = incomingHeader.getField(ConnectionHeaderFields.MD5_CHECKSUM);
    final String expectedChecksum = topicDefinitionHeader.getField(ConnectionHeaderFields.MD5_CHECKSUM);
    final boolean checksumMatches =
        incomingChecksum.equals(expectedChecksum)
            || incomingChecksum.equals(Subscriber.TOPIC_MESSAGE_TYPE_WILDCARD);
    Preconditions.checkState(checksumMatches, "Unexpected message MD5 " + incomingChecksum + " != "
        + expectedChecksum);
    final ConnectionHeader outgoingConnectionHeader = toDeclaration().toConnectionHeader();
    // TODO(damonkohler): Force latch mode to be consistent throughout the life
    // of the publisher.
    outgoingConnectionHeader.addField(ConnectionHeaderFields.LATCHING, getLatchMode() ? "1" : "0");
    return outgoingConnectionHeader.encode();
  }

  /**
   * Add a {@link Subscriber} connection to this {@link Publisher}.
   * 
   * @param subscriberIdentifer
   *          the {@link SubscriberIdentifier} of the new subscriber
   * @param channel
   *          the communication {@link Channel} to the {@link Subscriber}
   */
  public final void addSubscriber(SubscriberIdentifier subscriberIdentifer, Channel channel) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(String.format("Adding subscriber %s channel %s to publisher %s.",
          subscriberIdentifer, channel, this));
    }
    this.outgoingMessageQueue.addChannel(channel);
    signalOnNewSubscriber(subscriberIdentifer);
  }

  @Override
  public final void addListener(PublisherListener<T> listener) {
    listenerGroup.add(listener);
  }

  /**
   * Signal all {@link PublisherListener}s that the {@link Publisher} has
   * successfully registered with the master.
   * <p>
   * Each listener is called in a separate thread.
   */
  @Override
  public final void signalOnMasterRegistrationSuccess() {
    final Publisher<T> publisher = this;
    listenerGroup.signal(listener -> listener.onMasterRegistrationSuccess(publisher));
  }

  /**
   * Signal all {@link PublisherListener}s that the {@link Publisher} has failed
   * to register with the master.
   * <p>
   * Each listener is called in a separate thread.
   */
  @Override
  public final void signalOnMasterRegistrationFailure() {
    final Publisher<T> publisher = this;
    listenerGroup.signal(listener -> listener.onMasterRegistrationFailure(publisher));
  }

  /**
   * Signal all {@link PublisherListener}s that the {@link Publisher} has
   * successfully unregistered with the master.
   * <p>
   * Each listener is called in a separate thread.
   */
  @Override
  public final void signalOnMasterUnregistrationSuccess() {
    final Publisher<T> publisher = this;
    listenerGroup.signal(listener -> {
      listener.onMasterUnregistrationSuccess(publisher);
      shutdownLatch.countDown();
    });
  }

  /**
   * Signal all {@link PublisherListener}s that the {@link Publisher} has failed
   * to unregister with the master.
   * <p>
   * Each listener is called in a separate thread.
   */
  @Override
  public final void signalOnMasterUnregistrationFailure() {
    final Publisher<T> publisher = this;
    listenerGroup.signal(listener -> {
      listener.onMasterUnregistrationFailure(publisher);
      shutdownLatch.countDown();
    });
  }

  /**
   * Signal all {@link PublisherListener}s that the {@link Publisher} has a new
   * {@link Subscriber}.
   * <p>
   * Each listener is called in a separate thread.
   * 
   * @param subscriberIdentifier
   *          the {@link SubscriberIdentifier} of the new {@link Subscriber}
   */
  private final void signalOnNewSubscriber(final SubscriberIdentifier subscriberIdentifier) {
    final Publisher<T> publisher = this;
    listenerGroup.signal(listener -> listener.onNewSubscriber(publisher, subscriberIdentifier));
  }

  /**
   * Signal all {@link PublisherListener}s that the {@link Publisher} is being
   * shut down. Listeners should exit quickly since they may block shut down.
   * <p>
   * Each listener is called in a separate thread.
   * 
   * @param timeout
   * @param unit
   */
  private final void signalOnShutdown(long timeout, TimeUnit unit) {
    final Publisher<T> publisher = this;
    try {
      listenerGroup.signal(listener -> listener.onShutdown(publisher), timeout, unit);
    } catch (InterruptedException e) {
      // Ignored since we do not guarantee that all listeners will finish before
      // shutdown begins.
    }
  }

  @Override
  public final String toString() {
    return "Publisher<" + toDeclaration() + ">";
  }
}
