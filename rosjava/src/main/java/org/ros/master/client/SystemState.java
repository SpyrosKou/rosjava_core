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

import java.util.*;

/**
 * The state of the ROS graph as understood by the master.
 *
 * @author Keith M. Hughes
 */
public final class SystemState {

    /**
     * All topics known.
     */
    private final Set<TopicSystemState> topics;

    public SystemState(final Collection<TopicSystemState> topics) {
        if (topics != null) {
            this.topics = Set.copyOf(topics);
        } else {
            this.topics = Collections.emptySet();
        }
    }

    /**
     * Get all topics in the system state.
     *
     * @return a collection of topics.
     */
    public final Set<TopicSystemState> getTopics() {
        return topics;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SystemState that = (SystemState) o;
        return getTopics().equals(that.getTopics());
    }



    @Override
    public final int hashCode() {
        return Objects.hash(getTopics());
    }
    @Override
    public final String toString() {
        return "SystemState{" +
                "topics=" + topics +
                '}';
    }

}
