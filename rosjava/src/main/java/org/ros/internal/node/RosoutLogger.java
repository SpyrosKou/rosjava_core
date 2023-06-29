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

package org.ros.internal.node;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.Topics;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import rosgraph_msgs.Log;

import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;

/**
 * Logger that logs to both an underlying {@link org.slf4j.Logger} as well as /rosout.
 * The graph name of the node is added as {@link Marker} after the connection.
 * The logging in general can be configured by configuring the {@link RosoutLogger}
 * @author kwc@willowgarage.com (Ken Conley)
 * @author damonkohler@google.com (Damon Kohler)
 * @author Spyros Koukas
 */
final class RosoutLogger implements org.ros.node.RosLog {

    private ConnectedNode connectedNode;
    private Publisher<rosgraph_msgs.Log> publisher;
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private Marker marker = null;

    final void connect(final ConnectedNode connectedNode) {
        Preconditions.checkNotNull(connectedNode, "default node should not be null");
        this.connectedNode = connectedNode;
        this.publisher = this.connectedNode.newPublisher(Topics.ROSOUT, rosgraph_msgs.Log._TYPE);
        this.marker = MarkerFactory.getMarker(connectedNode.getName().toString());
    }


    /**
     * Starts logging disconnected from ROS, using only {@link Logger}
     * It needs to be connected using
     */
    public RosoutLogger() {
    }

    /**
     * Starts logging connected to ROS, if usePublisher is not null it is applied
     *
     * @param connectedNode
     * @param usePublisher
     */
    RosoutLogger(final ConnectedNode connectedNode, final Consumer<Publisher<Log>> usePublisher) {
        this();
        this.connect(connectedNode);
        if (usePublisher != null) {
            usePublisher.accept(this.publisher);
        }
    }

    private final void publish(final byte level, final String message, final Throwable throwable) {
        final String throwableStack = ExceptionUtils.getStackTrace(throwable);
        this.publish(level, message + '\n' + throwableStack);
    }

    private final void publish(final byte level, final String message) {

        final rosgraph_msgs.Log logMessage = this.publisher.newMessage();
        logMessage.getHeader().setStamp(this.connectedNode.getCurrentTime());
        logMessage.setLevel(level);
        logMessage.setName(this.connectedNode.getName().toString());
        logMessage.setMsg(message);


        // TODO(damonkohler): Should update the topics field with a list of all
        // published and subscribed topics for the node that created thisthis.logger.
        // This helps filter the rosoutconsole.
        this.publisher.publish(logMessage);
    }


    @Override
    public final String getName() {
        return LOGGER.getName();
    }


    @Override
    public final boolean isDebugEnabled() {
        return LOGGER.isDebugEnabled();
    }


    @Override
    public final boolean isInfoEnabled() {
        return LOGGER.isInfoEnabled();
    }


    @Override
    public final boolean isWarnEnabled() {
        return LOGGER.isWarnEnabled();
    }



    @Override
    public final boolean isErrorEnabled() {
        return LOGGER.isErrorEnabled();
    }


    @Override
    public final boolean isFatalEnabled() {
        return LOGGER.isErrorEnabled();
    }



    @Override
    public final void debug(final String message) {
        if (this.isDebugEnabled()) {
            if (this.marker == null) {
                LOGGER.debug(message);
            } else {
                LOGGER.debug(this.marker, message);
            }
            if (this.publisher != null) {
                this.publish(rosgraph_msgs.Log.DEBUG, message);
            }
        }
    }

    @Override
    public final void debug(final String message, final Throwable throwable) {
        if (this.isDebugEnabled()) {
            if (this.marker == null) {
                LOGGER.debug(message, throwable);
            } else {
                LOGGER.debug(this.marker, message, throwable);
            }
            if (this.publisher != null) {
                this.publish(rosgraph_msgs.Log.DEBUG, message, throwable);
            }
        }
    }

    @Override
    public final void info(final String message) {
        if (this.isInfoEnabled()) {
            if (this.marker == null) {
                LOGGER.info(message);
            } else {
                LOGGER.info(this.marker, message);
            }
            if (this.publisher != null) {
                this.publish(rosgraph_msgs.Log.INFO, message);
            }
        }
    }

    @Override
    public final void info(final String message, final Throwable throwable) {
        if (this.isInfoEnabled()) {
            if (this.marker == null) {
                LOGGER.info(message, throwable);
            } else {
                LOGGER.info(this.marker, message, throwable);
            }
            if (this.publisher != null) {
                this.publish(rosgraph_msgs.Log.INFO, message, throwable);
            }
        }
    }

    @Override
    public final void warn(final String message) {
        if (this.isWarnEnabled()) {
            if (this.marker == null) {
                LOGGER.warn(message);
            } else {
                LOGGER.warn(this.marker, message);
            }
            if (this.publisher != null) {
                this.publish(rosgraph_msgs.Log.WARN, message);
            }
        }
    }

    @Override
    public final void warn(final String message, final Throwable throwable) {
        if (this.isWarnEnabled()) {
            if (this.marker == null) {
                LOGGER.warn(message, throwable);
            } else {
                LOGGER.warn(this.marker, message, throwable);
            }
            if (this.publisher != null) {
                this.publish(rosgraph_msgs.Log.WARN, message, throwable);
            }
        }
    }

    @Override
    public final void error(final String message) {
        if (this.isErrorEnabled()) {
            if (this.marker == null) {
                LOGGER.error(message);
            } else {
                LOGGER.error(this.marker, message);
            }
            if (this.publisher != null) {
                this.publish(rosgraph_msgs.Log.ERROR, message);
            }
        }
    }

    @Override
    public final void error(final String message, final Throwable throwable) {
        if (this.isErrorEnabled()) {
            if (this.marker == null) {
                LOGGER.error(message, throwable);
            } else {
                LOGGER.error(this.marker, message, throwable);
            }
            if (this.publisher != null) {
                this.publish(rosgraph_msgs.Log.ERROR, message, throwable);
            }
        }
    }

    @Override
    public final void fatal(final String message) {
        if (this.isFatalEnabled()) {
            if (this.marker == null) {
                LOGGER.error(message);
            } else {
                LOGGER.error(this.marker, message);
            }
            if (this.publisher != null) {
                this.publish(Log.FATAL, message);
            }
        }
    }

    @Override
    public final void fatal(final String message, final Throwable throwable) {
        if (this.isFatalEnabled()) {
            if (this.marker == null) {
                LOGGER.error(message, throwable);
            } else {
                LOGGER.error(this.marker, message, throwable);
            }
            if (this.publisher != null) {
                this.publish(Log.FATAL, message, throwable);
            }
        }
    }
}


