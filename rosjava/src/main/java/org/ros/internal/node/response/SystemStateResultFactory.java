/*
 * Copyright (C) 2012 Google Inc.
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

package org.ros.internal.node.response;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.ros.master.client.SystemState;
import org.ros.master.client.TopicSystemState;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A {@link ResultFactory} to take an object and turn it into a
 * {@link SystemState} instance.
 *
 * @author Keith M. Hughes
 */
public final class SystemStateResultFactory implements ResultFactory<SystemState> {

    @Override
    public final SystemState apply(Object value) {
        return applyStatic(value);
    }

    public static final SystemState applyStatic(Object value) {
        final Object[] values = (Object[]) value;

        final Map<String, Set<String>> publisherMap = getPublishers(values[0]);
        final Map<String, Set<String>> subscriberMap = getSubscribers(values[1]);

        final Map<String, TopicSystemState> topics = Maps.newHashMap();

        for (final Entry<String, Set<String>> publisherData : publisherMap.entrySet()) {
            final String topicName = publisherData.getKey();

            Set<String> subscriberNodes = subscriberMap.remove(topicName);

            // Return empty lists if no subscribers
            if (subscriberNodes == null) {
                subscriberNodes = Sets.newHashSet();
            }

            topics.put(topicName,
                    new TopicSystemState(topicName, publisherData.getValue(),
                            subscriberNodes));
        }

        for (final Entry<String, Set<String>> subscriberData : subscriberMap
                .entrySet()) {
            // At this point there are no publishers with the same topic name
            final HashSet<String> noPublishers = Sets.newHashSet();
            final String topicName = subscriberData.getKey();
            topics.put(topicName, new TopicSystemState(topicName,
                    noPublishers, subscriberData.getValue()));
        }

        // TODO(keith): Get service state in here.

        return new SystemState(topics.values());
    }

    /**
     * Extract out the publisher data.
     *
     * @param pubPairs the list of lists containing both a topic name and a list of
     *                 publisher nodes for that topic
     * @return a mapping from topic name to the set of publishers for that topic
     */
    private static final Map<String, Set<String>> getPublishers(Object pubPairs) {
        final Map<String, Set<String>> topicToPublishers = Maps.newHashMap();

        for (final Object topicData : Arrays.asList((Object[]) pubPairs)) {
            final String topicName = (String) ((Object[]) topicData)[0];

            final Set<String> publishers = Sets.newHashSet();
            final Object[] publisherData = (Object[]) ((Object[]) topicData)[1];
            for (final Object publisher : publisherData) {
                publishers.add(publisher.toString());
            }

            topicToPublishers.put(topicName, publishers);
        }

        return topicToPublishers;
    }

    /**
     * Extract out the subscriber data.
     *
     * @param subPairs the list of lists containing both a topic name and a list of
     *                 subscriber nodes for that topic
     * @return a mapping from topic name to the set of subscribers for that
     * topic
     */
    private static final Map<String, Set<String>> getSubscribers(Object subPairs) {
        final Map<String, Set<String>> topicToSubscribers = Maps.newHashMap();

        for (final Object topicData : Arrays.asList((Object[]) subPairs)) {
            final String topicName = (String) ((Object[]) topicData)[0];

            final Set<String> subscribers = Sets.newHashSet();
            final Object[] subscriberData = (Object[]) ((Object[]) topicData)[1];
            for (final Object subscriber : subscriberData) {
                subscribers.add(subscriber.toString());
            }

            topicToSubscribers.put(topicName, subscribers);
        }

        return topicToSubscribers;
    }
}
