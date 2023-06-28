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

package org.ros.master.client;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Information about a topic.
 *
 * @author Keith M. Hughes
 */
public final class TopicSystemState {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TopicSystemState that = (TopicSystemState) o;
        return getTopicName().equals(that.getTopicName()) && getPublishers().equals(that.getPublishers()) && getSubscribers().equals(that.getSubscribers());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTopicName(), getPublishers(), getSubscribers());
    }

    /**
     * Name of the topic.
     */
    private final String topicName;

    /**
     * Node names of all publishers.
     */
    private final Set<String> publishers = new HashSet<>();

    /**
     * Node names of all subscribers.
     */
    private final Set<String> subscribers = new HashSet<>();

    public TopicSystemState(final String topicName, final Collection<String> publishers,
                            final Collection<String> subscribers) {
        this.topicName = topicName;
        if (publishers != null) {
            this.publishers.addAll(publishers);
        }
        if (subscribers != null) {
            this.subscribers.addAll(subscribers);
        }
    }

    /**
     * @return the topicName
     */
    public String getTopicName() {
        return topicName;
    }

    /**
     * Get the set of all nodes that publish the topic.
     *
     * @return the set of node names
     */
    public Set<String> getPublishers() {
        return publishers;
    }

    /**
     * Get the set of all nodes that subscribe to the topic.
     *
     * @return the set of node names
     */
    public Set<String> getSubscribers() {
        return subscribers;
    }
}
