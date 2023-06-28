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


import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ros.address.AdvertiseAddress;
import org.ros.address.BindAddress;
import org.ros.address.PrivateAdvertiseAddressFactory;
import org.ros.internal.node.client.MasterClient;
import org.ros.internal.node.client.SlaveClient;
import org.ros.internal.node.parameter.ParameterManager;
import org.ros.internal.node.response.Response;
import org.ros.internal.node.server.SlaveServer;
import org.ros.internal.node.server.master.MasterServer;
import org.ros.internal.node.service.ServiceManager;
import org.ros.internal.node.topic.TopicParticipantManager;
import org.ros.namespace.GraphName;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
public class MasterSlaveIntegrationTest {
    private final PrivateAdvertiseAddressFactory privateAdvertiseAddressFactory = new PrivateAdvertiseAddressFactory();
    private MasterServer masterServer;
    private MasterClient masterClient;
    private SlaveServer slaveServer;
    private SlaveClient slaveClient;
    private ExecutorService executorService;

    @BeforeEach
    public void setUp() {
        try {
            this.executorService = Executors.newCachedThreadPool();
            this.masterServer = new MasterServer(BindAddress.newPrivate(), privateAdvertiseAddressFactory.newDefault());
            this.masterServer.start();
            this.masterServer.awaitStart(10, TimeUnit.SECONDS);
            this.masterClient = new MasterClient(masterServer.getUri());
            final TopicParticipantManager topicParticipantManager = new TopicParticipantManager();
            final ServiceManager serviceManager = new ServiceManager();
            final ParameterManager parameterManager = new ParameterManager(executorService);
            this.slaveServer =
                    new SlaveServer(GraphName.of("/foo"), BindAddress.newPrivate(),
                            privateAdvertiseAddressFactory.newDefault(), BindAddress.newPrivate(), privateAdvertiseAddressFactory.newDefault(),
                            masterClient, topicParticipantManager, serviceManager, parameterManager,
                            executorService, null);
            this.slaveServer.start();
            this.slaveServer.awaitStart(10, TimeUnit.SECONDS);
            this.slaveClient = new SlaveClient(GraphName.of("/bar"), slaveServer.getUri());

        } catch (final Exception exception) {
            Assumptions.assumeTrue(false, ExceptionUtils.getStackTrace(exception));
        }
    }

    @AfterEach
    public void tearDown() {
        try {
            this.masterServer.shutdown();
            this.masterServer.awaitShutdown(10, TimeUnit.SECONDS);
        } catch (final Exception exception) {

        }

        try {
            this.executorService.shutdown();
            this.executorService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (final Exception exception) {

        }

    }

    @Test
    public void testGetMasterUri() {
        final Response<URI> response = this.slaveClient.getMasterUri();
        assertEquals(masterServer.getUri(), response.getResult());
    }

    @Test
    public void testGetPid() {
        final Response<Integer> response = slaveClient.getPid();
        assertTrue(response.getResult() > 0);
    }
}
