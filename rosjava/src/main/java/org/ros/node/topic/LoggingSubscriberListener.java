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

package org.ros.node.topic;

import org.ros.internal.message.Message;
import org.ros.internal.node.topic.DefaultSubscriber;
import org.ros.internal.node.topic.PublisherIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * A {@link SubscriberListener} which logs all calls at info level.
 *
 * @author Spyros Koukas
 */
public final class LoggingSubscriberListener<T extends Message> extends LoggingRegistrantListener<Subscriber<T>> implements SubscriberListener<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public LoggingSubscriberListener() {
        super(LOGGER);
    }


    @Override
    public final void onNewPublisher(final Subscriber<T> subscriber, final PublisherIdentifier publisherIdentifier) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("New Publisher for topic: " + publisherIdentifier.getTopicName() + " of Node: " + publisherIdentifier.getNodeName());
        }
    }

    @Override
    public final void onShutdown(final Subscriber<T> subscriber) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Shutting: " + subscriber.getTopicName() + " of Type: " + subscriber.getTopicMessageType());
        }
    }
}
