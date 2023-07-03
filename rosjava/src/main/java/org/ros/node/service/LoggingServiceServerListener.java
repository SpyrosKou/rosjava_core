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

package org.ros.node.service;

import org.ros.internal.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;

/**
 * @author Spyros Koukas
 */
public final class LoggingServiceServerListener<T extends Message, S extends Message> implements ServiceServerListener<T, S> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final Logger logger;

    public LoggingServiceServerListener() {
        logger = LOGGER;
    }

    public LoggingServiceServerListener(final Logger logger) {
        Objects.requireNonNull(logger);
        this.logger = logger;
    }


    @Override
    public final void onMasterRegistrationSuccess(ServiceServer<T, S> registrant) {
        logger.info("Service registered: " + registrant.getName() + " uri:" + registrant.getUri());
    }

    @Override
    public final void onMasterRegistrationFailure(ServiceServer<T, S> registrant) {
        logger.info("Service registration failed: " + registrant.getName() + " uri:" + registrant.getUri());
    }

    @Override
    public final void onMasterUnregistrationSuccess(ServiceServer<T, S> registrant) {
        logger.info("Service unregistered: " + registrant.getName() + " uri:" + registrant.getUri());
    }

    @Override
    public final void onMasterUnregistrationFailure(ServiceServer<T, S> registrant) {
        logger.info("Service unregistration failed: " + registrant.getName() + " uri:" + registrant.getUri());
    }

    @Override
    public final void onShutdown(ServiceServer<T, S> serviceServer) {
        logger.info("Shutdown: " + serviceServer.getName() + " uri:" + serviceServer.getUri());
    }
}
