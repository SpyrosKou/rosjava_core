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

package org.ros.internal.transport;


import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.ros.exception.RosRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Channels;

/**
 * Adds new {@link Channels} to the provided {@link ChannelGroup}.
 *
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class ConnectionTrackingHandler extends SimpleChannelHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * The channel group the connection is to be part of.
     */
    private final ChannelGroup channelGroup;

    public ConnectionTrackingHandler(final ChannelGroup channelGroup) {
        this.channelGroup = channelGroup;
    }

    @Override
    public final void channelOpen(final ChannelHandlerContext ctx, final ChannelStateEvent e) throws Exception {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Channel opened: {}", e.getChannel());
        }
        this.channelGroup.add(e.getChannel());
        super.channelOpen(ctx, e);
    }

    @Override
    public final void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) throws Exception {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Channel closed: {}", e.getChannel());
        }
        super.channelClosed(ctx, e);
    }

    @Override
    public final void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) throws Exception {
        ctx.getChannel().close();
        if (e.getCause() instanceof IOException) {
            // NOTE(damonkohler): We ignore exceptions here because they are common
            // (e.g. network failure, connection reset by peer, shutting down, etc.)
            // and should not be fatal. However, in all cases the channel should be
            // closed.

            if (e.getCause() instanceof ClosedChannelException) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Channel closed during I/O: {}", ctx.getChannel(), e.getCause());
                }
            } else {
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("Channel exception: {}", ctx.getChannel(), e.getCause());
                }
            }

        } else {
            throw new RosRuntimeException(e.getCause());
        }
    }
}
