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

package org.ros;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.ros.node.DefaultNodeMainExecutor;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertTrue;

/**
 * This is a base class for tests that sets up and tears down a {@link RosCore}
 * and a {@link NodeMainExecutor}.
 *
 * @author damonkohler@google.com (Damon Kohler)
 */
@Disabled
public abstract class RosTest {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    protected RosCore rosCore;
    protected NodeConfiguration nodeConfiguration;
    protected NodeMainExecutor nodeMainExecutor;

    @BeforeEach
    public void setUp() throws InterruptedException {
        this.rosCore = RosCore.newPrivate();
        this.rosCore.start();
        assertTrue(this.rosCore.awaitStart(1, TimeUnit.SECONDS));
        this.nodeMainExecutor = DefaultNodeMainExecutor.newDefault();
        this.nodeConfiguration = NodeConfiguration.newPrivate(rosCore.getUri());
    }

    @AfterEach
    public void tearDown() {
        this.nodeMainExecutor.shutdown();

        this.rosCore.shutdown();
        try {
            this.rosCore.awaitShutdown(30, TimeUnit.SECONDS);
            this.logger.info("Shutdown roscore ok");
        } catch (final Exception exception) {
            this.logger.info("Error while shutting down roscore: {}", ExceptionUtils.getStackTrace(exception));
        }

    }
}
