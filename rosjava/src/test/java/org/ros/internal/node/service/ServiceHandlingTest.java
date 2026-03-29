/*
 * Copyright (C) 2026 Spyros Koukas
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

package org.ros.internal.node.service;


import org.junit.jupiter.api.Test;
import org.ros.RosTest;
import org.ros.exception.RemoteException;
import org.ros.message.MessageFactory;
import org.ros.namespace.GraphName;
import org.ros.node.*;
import org.ros.node.service.ServiceClient;
import org.ros.node.service.ServiceResponseBuilder;
import org.ros.node.service.ServiceResponseListener;
import org.ros.node.service.ServiceServer;
import rosjava_test_msgs.AddTwoInts;
import rosjava_test_msgs.AddTwoIntsRequest;
import rosjava_test_msgs.AddTwoIntsResponse;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.helpers.MessageFormatter;

import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

/**
 * Class for running a multi-threaded service request
 *
 * @author Spyros Koukas
 */
public class ServiceHandlingTest extends RosTest {

    /**
     * Test if a multi-threaded node can safely call services from another node
     */
    @Test public void testServiceResponeOrder() throws Exception {
        final NodeConfiguration secondConfig = NodeConfiguration.newPrivate(rosCore.getUri());
        final MessageFactory messageFactory = secondConfig.getMessageFactory();
        final String SERVICE_NAME = "test_service";
        final int NUM_THREADS = 10;
        final CountDownLatch countDownLatch = new CountDownLatch(NUM_THREADS);

        // Create the anonymous node to test the server
        final AbstractNodeMain serverNode = new AbstractNodeMain() {
            @Override public GraphName getDefaultNodeName() {
                return GraphName.of("server_node");
            }

            @Override public void onStart(final ConnectedNode connectedNode) {

                // Setup Server
                final ServiceServer<AddTwoIntsRequest, AddTwoIntsResponse> testServer =
                        connectedNode.newServiceServer(SERVICE_NAME, AddTwoInts._TYPE,
                                new ServiceResponseBuilder<AddTwoIntsRequest, AddTwoIntsResponse>() {

                                    @Override
                                    public void build(final AddTwoIntsRequest req, final AddTwoIntsResponse res) {
                                        res.setSum(req.getA() + req.getB());
                                    }
                                });
            }
        };

        final List<ClientThread> threads = new LinkedList<>();

        // Create the anonymous node to test the server
        final AbstractNodeMain clientNode = new AbstractNodeMain() {

            @Override public GraphName getDefaultNodeName() {
                return GraphName.of("client_node");
            }

            @Override public void onStart(final ConnectedNode connectedNode) {

                // Assert that the service was created

                final ServiceClient<AddTwoIntsRequest, AddTwoIntsResponse> serviceClient;
                try {
                    serviceClient = connectedNode.newServiceClient(SERVICE_NAME, AddTwoInts._TYPE);
                    for (int i = 0; i < NUM_THREADS; i++) {
                        final ClientThread newThread = new ClientThread(i, i, countDownLatch, messageFactory,
                                SERVICE_NAME, serviceClient, connectedNode.getLog());
                        newThread.start();
                        threads.add(newThread);
                    }
                } catch (final org.ros.exception.ServiceNotFoundException e) {
                    fail("Couldn't find service " + SERVICE_NAME);
                }
            }

            @Override
            public void onShutdown(final Node node) {
                for (final ClientThread thread : threads) {
                    thread.interrupt();
                }
            }
        };

        // Start the transform server node
        nodeMainExecutor.execute(serverNode, nodeConfiguration);
        // Give time for service to be available
        Thread.sleep(1000);
        // Start the anonymous node to test the server
        nodeMainExecutor.execute(clientNode, secondConfig);

        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));

        for (final ClientThread a : threads) {
            assertTrue(a.correct[0]);
        }
        // // Shutdown nodes
        // nodeMainExecutor.shutdownNodeMain(clientNode);
        // // Shutting down the transform server from this test results in a exception on printing the service address
        // nodeMainExecutor.shutdownNodeMain(serverNode);
        // Stack trace is automatically logged
        // ROS is shutdown automatically in cleanup from ROS Test
    }

    /**
     * Class for making service requests
     */
    public static class ClientThread extends Thread {
        final int a;
        final int b;
        final CountDownLatch countDownLatch;
        final MessageFactory messageFactory;
        final ServiceClient<AddTwoIntsRequest, AddTwoIntsResponse> serviceClient;
        final String service;
        final boolean[] done = new boolean[1];
        final RosLog log;
        final boolean[] correct = new boolean[1];

        ClientThread(final int reqA, final int reqB, final CountDownLatch countDownLatch,
                     final MessageFactory messageFactory, final String service,
                     final ServiceClient<AddTwoIntsRequest, AddTwoIntsResponse> serviceClient,
                     final RosLog log) {
            this.a = reqA;
            this.b = reqB;
            this.countDownLatch = countDownLatch;
            this.messageFactory = messageFactory;
            this.service = service;
            this.serviceClient = serviceClient;
            this.done[0] = true;
            this.log = log;
            this.correct[0] = false;
        }

        @Override
        public void run() {
            // Build ros messages
            if (done[0]) {

                done[0] = false;
                final AddTwoIntsRequest req = messageFactory.newFromType(AddTwoIntsRequest._TYPE);


                req.setA(a);
                req.setB(b);

                serviceClient.call(req, new ServiceResponseListener<AddTwoIntsResponse>() {
                    @Override public void onSuccess(final AddTwoIntsResponse response) {
                        log.info(MessageFormatter.arrayFormat("Request: {}+{} Result: {}",
                                new Object[] {req.getA(), req.getB(), response.getSum()}).getMessage());
                        correct[0] = response.getSum() == (req.getA() + req.getB());
                        countDownLatch.countDown();
                        done[0] = true;
                    }

                    @Override
                    public void onFailure(final RemoteException e) {
                        fail("Service request failed for request " + a + "+" + b);
                    }
                });
            }
        }
    }
}
