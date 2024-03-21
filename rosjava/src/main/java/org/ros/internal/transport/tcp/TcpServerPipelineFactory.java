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

package org.ros.internal.transport.tcp;

import com.google.common.base.Preconditions;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.frame.LengthFieldPrepender;
import org.ros.internal.node.service.ServiceManager;
import org.ros.internal.node.topic.TopicParticipantManager;

import java.util.function.Consumer;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class TcpServerPipelineFactory extends ConnectionTrackingChannelPipelineFactory {

    static final String LENGTH_FIELD_BASED_FRAME_DECODER = "LengthFieldBasedFrameDecoder";
    public static final String LENGTH_FIELD_PREPENDER = "LengthFieldPrepender";
    public static final String HANDSHAKE_HANDLER = "HandshakeHandler";
    /**
     * Will process and modify a new, just created {@link ChannelPipeline} before returned by the {@link TcpServerPipelineFactory#getPipeline()} method
     */
    private final Consumer<ChannelPipeline> newChannelPipelinePostprocessor;

    private final TopicParticipantManager topicParticipantManager;
    private final ServiceManager serviceManager;

    public TcpServerPipelineFactory(final ChannelGroup channelGroup
            , final TopicParticipantManager topicParticipantManager
            , final ServiceManager serviceManager) {
        super(channelGroup);
        this.topicParticipantManager = topicParticipantManager;
        this.serviceManager = serviceManager;
        this.newChannelPipelinePostprocessor = this::doNothing;
    }

    public TcpServerPipelineFactory(final ChannelGroup channelGroup
            , final TopicParticipantManager topicParticipantManager
            , final ServiceManager serviceManager
            , final Consumer<ChannelPipeline> newChannelPipelinePostprocessor) {
        super(channelGroup);
        Preconditions.checkNotNull(newChannelPipelinePostprocessor);
        this.topicParticipantManager = topicParticipantManager;
        this.serviceManager = serviceManager;
        this.newChannelPipelinePostprocessor = newChannelPipelinePostprocessor;
    }

    private final void doNothing(final ChannelPipeline channelPipeline) {
    }

    @Override
    public final ChannelPipeline getPipeline() {
        final ChannelPipeline pipeline = super.getPipeline();
        pipeline.addLast(LENGTH_FIELD_PREPENDER, new LengthFieldPrepender(4));
        pipeline.addLast(LENGTH_FIELD_BASED_FRAME_DECODER, new LengthFieldBasedFrameDecoder(
                Integer.MAX_VALUE, 0, 4, 0, 4));
        pipeline.addLast(HANDSHAKE_HANDLER, new TcpServerHandshakeHandler(topicParticipantManager,
                serviceManager));
        //postProcess
        this.newChannelPipelinePostprocessor.accept(pipeline);
        return pipeline;
    }
}
