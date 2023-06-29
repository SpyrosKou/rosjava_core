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
import org.ros.internal.node.RegistrantListener;
import org.ros.internal.node.topic.TopicParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;

/**
 * A {@link RegistrantListener} which logs all calls at info level.
 *
 * @author Spyros Koukas
 */
abstract class LoggingRegistrantListener<T extends TopicParticipant> implements RegistrantListener<T> {

    private final Logger logger;

    LoggingRegistrantListener(final Logger logger) {
        Objects.requireNonNull(logger);
        this.logger = logger;
    }

    @Override
    public final void onMasterRegistrationSuccess(final T topicParticipant) {
        if (logger.isInfoEnabled()) {
            logger.info("MasterRegistrationSuccess for topic: " + topicParticipant.getTopicName() + " of type: " + topicParticipant.getTopicMessageType());
        }
    }

    @Override
    public final void onMasterRegistrationFailure(final T topicParticipant) {
        if (logger.isInfoEnabled()) {
            logger.info("MasterRegistrationFailure for topic: " + topicParticipant.getTopicName() + " of type: " + topicParticipant.getTopicMessageType());
        }
    }

    @Override
    public final void onMasterUnregistrationSuccess(final T topicParticipant) {
        if (logger.isInfoEnabled()) {
            logger.info("MasterUnregistrationSuccess for topic: " + topicParticipant.getTopicName() + " of type: " + topicParticipant.getTopicMessageType());
        }
    }

    @Override
    public final void onMasterUnregistrationFailure(final T topicParticipant) {
        if (logger.isInfoEnabled()) {
            logger.info("MasterUnregistrationFailure for topic: " + topicParticipant.getTopicName() + " of type: " + topicParticipant.getTopicMessageType());
        }
    }


}
