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

package org.ros.time;

import org.ros.Topics;
import org.ros.message.MessageListener;
import org.ros.message.Time;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Subscriber;
import rosgraph_msgs.Clock;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link TimeProvider} for use when the ROS graph is configured for
 * simulation.
 *
 * @author damonkohler@google.com (Damon Kohler)
 */
public class ClockTopicTimeProvider implements TimeProvider {

    private final Subscriber<rosgraph_msgs.Clock> subscriber;

    private final Object mutex = new Object();
    private AtomicReference<rosgraph_msgs.Clock> clockReference = new AtomicReference<>();

    public ClockTopicTimeProvider(final ConnectedNode connectedNode) {
        this.subscriber = connectedNode.newSubscriber(Topics.CLOCK, rosgraph_msgs.Clock._TYPE);

        subscriber.addMessageListener(this.clockReference::set);
    }

    public final Subscriber<rosgraph_msgs.Clock> getSubscriber() {
        return this.subscriber;
    }

    @Override
    public final Time getCurrentTime() {
        // When using simulation time, the ROS Time API will return time=0 until it has received a
        // message from the /clock topic.
        if (clockReference.get() == null) {
            return new Time();
        } else {
            return new Time(this.clockReference.get().getClock());
        }

    }
}
