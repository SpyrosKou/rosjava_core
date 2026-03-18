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

package org.ros.internal.transport.queue;

import com.google.common.annotations.VisibleForTesting;


import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.ChannelGroupFutureListener;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.ros.concurrent.CancellableLoop;
import org.ros.concurrent.CircularBlockingDeque;
import org.ros.internal.message.Message;
import org.ros.internal.message.MessageBufferPool;
import org.ros.internal.message.MessageBuffers;
import org.ros.message.MessageSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author damonkohler@google.com (Damon Kohler)
 * @author Spyros Koukas
 */
public final class OutgoingMessageQueue<T extends Message> {


    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final int DEQUE_CAPACITY = 32;
    private final MessageSerializer<T> serializer;
    private final CircularBlockingDeque<T> deque = new CircularBlockingDeque<T>(DEQUE_CAPACITY);
    private final ChannelGroup channelGroup = new DefaultChannelGroup();
    private final Writer writer = new Writer();
    private final MessageBufferPool messageBufferPool = new MessageBufferPool();
    private final ChannelBuffer latchedBuffer = MessageBuffers.dynamicBuffer();
    private final Object mutex = new Object();
    //Can be changed
    private boolean latchMode = false;
    private T latchedMessage;

    private final class Writer extends CancellableLoop {
        @Override
        public void loop() throws InterruptedException {
            final T message = deque.takeFirst();
            final ChannelBuffer buffer = messageBufferPool.acquire();
            serializer.serialize(message, buffer);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(String.format("Writing %d bytes to %d channels.", buffer.readableBytes(),
                        channelGroup.size()));
            }
            // Note that the buffer is automatically "duplicated" by Netty to avoid
            // race conditions. However, the duplicated buffer and the original buffer
            // share the same backing array. So, we have to wait until the write
            // operation is complete before returning the buffer to the pool.
            channelGroup.write(buffer).addListener(future -> messageBufferPool.release(buffer));
        }
    }//end inner class

    public OutgoingMessageQueue(MessageSerializer<T> serializer, ExecutorService executorService) {
        this.serializer = serializer;
        executorService.execute(writer);
    }

    public final void setLatchMode(final boolean enabled) {
        this.latchMode = enabled;
    }

    public final boolean getLatchMode() {
        return this.latchMode;
    }

    /**
     * @param message the message to add to the queue
     */
    public final void add(T message) {
        this.deque.addLast(message);
        this.setLatchedMessage(message);
    }

    private final void setLatchedMessage(final T message) {
        synchronized (this.mutex) {
            this.latchedMessage = message;
        }
    }

    /**
     *
     * @param timeout
     * @param unit
     * @throws InterruptedException
     */
    public final void shutdown(long timeout, TimeUnit unit) throws InterruptedException {
        writer.cancel();
        channelGroup.close().await(timeout,unit);
    }

    /**
     * @param channel added to this {@link OutgoingMessageQueue}'s {@link ChannelGroup}
     */
    public final void addChannel(final Channel channel) {
        if (!this.writer.isRunning()) {
            LOGGER.warn("Failed to add channel. Cannot add channels after shutdown.");
            return;
        } else {
            if (this.latchMode && this.latchedMessage != null) {
                writeLatchedMessage(channel);
            }
            this.channelGroup.add(channel);
        }
    }

    // TODO(damonkohler): Avoid re-serializing the latched message if it hasn't
    // changed.
    private final void writeLatchedMessage(final Channel channel) {
        synchronized (this.mutex) {
            this.latchedBuffer.clear();
            this.serializer.serialize(latchedMessage, latchedBuffer);
            channel.write(latchedBuffer);
        }
    }

    /**
     * @return the number of {@link Channel}s which have been added to this queue
     */
    public final int getNumberOfChannels() {
        return this.channelGroup.size();
    }

    @VisibleForTesting
    public final ChannelGroup getChannelGroup() {
        return this.channelGroup;
    }
}
