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

package org.ros.node.service;

import org.junit.jupiter.api.Test;
import org.ros.RosTest;
import org.ros.exception.RemoteException;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.ServiceClientNode;
import rosjava_test_msgs.AddTwoIntsRequest;
import rosjava_test_msgs.AddTwoIntsResponse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

/**
 * Characterization tests for the client-side shutdown/failure path on persistent services.
 *
 * @author Spyros Koukas
 */
public class ServiceClientShutdownIntegrationTest extends RosTest {

    private static final String SERVICE_NAME = "/add_two_ints";
    private static final String SERVER_NAME = "server";
    private static final String CLIENT = "client";

    @Test
    public void testPersistentServiceClientShutdownFailsQueuedRequestsExplicitly() throws Exception {
        final CountDownServiceServerListener<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> countDownServiceServerListener =
                CountDownServiceServerListener.newDefault();
        final CountDownLatch slowRequestEntered = new CountDownLatch(1);
        final CountDownLatch releaseSlowRequest = new CountDownLatch(1);

        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(SERVER_NAME + "_shutdown_race");
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {
                ServiceServer<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceServer =
                        connectedNode.newServiceServer(
                                SERVICE_NAME,
                                rosjava_test_msgs.AddTwoInts._TYPE,
                                (request, response) -> {
                                    if (request.getA() == 1) {
                                        slowRequestEntered.countDown();
                                        try {
                                            releaseSlowRequest.await();
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                    response.setSum(request.getA() + request.getB());
                                });
                serviceServer.addListener(countDownServiceServerListener);
            }
        }, nodeConfiguration);

        assertTrue(countDownServiceServerListener.awaitMasterRegistrationSuccess(1, TimeUnit.SECONDS));

        final ServiceClientNode<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> client =
                new ServiceClientNode<>(SERVER_NAME + CLIENT + "_shutdown_race", SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE);
        nodeMainExecutor.execute(client, nodeConfiguration);
        assertTrue(client.awaitConnection(1, TimeUnit.SECONDS));

        final CountDownLatch callbackLatch = new CountDownLatch(2);
        final AtomicInteger failures = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final AddTwoIntsRequest slowRequest = client.getServiceClient().newMessage();
        slowRequest.setA(1);
        slowRequest.setB(1);
        client.getServiceClient().call(slowRequest, new ServiceResponseListener<>() {
            @Override
            public void onSuccess(AddTwoIntsResponse response) {
                failure.compareAndSet(null,
                        new AssertionError("Expected slow request to fail after client shutdown."));
                callbackLatch.countDown();
            }

            @Override
            public void onFailure(RemoteException e) {
                if (e.getMessage() == null || e.getMessage().isEmpty()) {
                    failure.compareAndSet(null,
                            new AssertionError("Expected shutdown failure message for slow request."));
                }
                failures.incrementAndGet();
                callbackLatch.countDown();
            }
        });

        assertTrue(slowRequestEntered.await(1, TimeUnit.SECONDS));

        final AddTwoIntsRequest queuedRequest = client.getServiceClient().newMessage();
        queuedRequest.setA(2);
        queuedRequest.setB(2);
        client.getServiceClient().call(queuedRequest, new ServiceResponseListener<>() {
            @Override
            public void onSuccess(AddTwoIntsResponse response) {
                failure.compareAndSet(null,
                        new AssertionError("Expected queued request to fail after client shutdown."));
                callbackLatch.countDown();
            }

            @Override
            public void onFailure(RemoteException e) {
                if (e.getMessage() == null || e.getMessage().isEmpty()) {
                    failure.compareAndSet(null,
                            new AssertionError("Expected shutdown failure message for queued request."));
                }
                failures.incrementAndGet();
                callbackLatch.countDown();
            }
        });

        client.getServiceClient().shutdown();
        releaseSlowRequest.countDown();

        assertTrue(callbackLatch.await(2, TimeUnit.SECONDS));
        assertEquals(2, failures.get());
        if (failure.get() != null) {
            fail(failure.get().toString());
        }
    }
}
