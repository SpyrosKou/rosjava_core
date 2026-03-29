/*
 * Copyright (C) 2011 Google Inc.
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

import junit.framework.Assert;
import org.junit.jupiter.api.Test;
import org.ros.RosTest;
import org.ros.exception.*;
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

import static junit.framework.Assert.*;



/**
 * @author damonkohler@google.com (Damon Kohler)
 * @author Spyros Koukas
 */
public class ServiceIntegrationTest extends RosTest {

    private static final String SERVICE_NAME = "/add_two_ints";
    private static final String SERVER_NAME = "server";
    private static final String CLIENT = "client";

    @Test
    public void testPesistentServiceConnection() throws Exception {
        final CountDownServiceServerListener<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> countDownServiceServerListener =
                CountDownServiceServerListener.newDefault();
        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(SERVER_NAME);
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {
                final ServiceServer<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceServer =
                        connectedNode
                                .newServiceServer(
                                        SERVICE_NAME,
                                        rosjava_test_msgs.AddTwoInts._TYPE,
                                        (request, response) -> response.setSum(request.getA() + request.getB()));
                try {
                    connectedNode.newServiceServer(SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE, (a, b) -> {
                    });
                    fail();
                } catch (final DuplicateServiceException e) {
                    // Only one ServiceServer with a given name can be created.
                }
                serviceServer.addListener(countDownServiceServerListener);
            }
        }, nodeConfiguration);

        assertTrue(countDownServiceServerListener.awaitMasterRegistrationSuccess(1, TimeUnit.SECONDS));

        final CountDownLatch latch = new CountDownLatch(2);
        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(CLIENT);
            }

            @Override
            public void onStart(ConnectedNode connectedNode) {
                final ServiceClient<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceClient;
                try {
                    serviceClient = connectedNode.newServiceClient(SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE);
                    // Test that requesting another client for the same service returns
                    // the same instance.
                    ServiceClient<?, ?> duplicate =
                            connectedNode.newServiceClient(SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE);
                    assertEquals(serviceClient, duplicate);
                } catch (final ServiceNotFoundException e) {
                    throw new RosRuntimeException(e);
                }
                final rosjava_test_msgs.AddTwoIntsRequest request = serviceClient.newMessage();
                {
//                   final rosjava_test_msgs.AddTwoIntsRequest request = serviceClient.newMessage();
                    request.setA(2);
                    request.setB(2);
                    serviceClient.call(request, new ServiceResponseListener<>() {
                        @Override
                        public void onSuccess(rosjava_test_msgs.AddTwoIntsResponse response) {
                            assertEquals(response.getSum(), 4);
                            latch.countDown();
                        }

                        @Override
                        public void onFailure(final RemoteException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
                {
                    // Regression test for issue 122.
//                    final rosjava_test_msgs.AddTwoIntsRequest request = serviceClient.newMessage();
                    request.setA(3);
                    request.setB(3);
                    serviceClient.call(request, new ServiceResponseListener<>() {
                        @Override
                        public void onSuccess(rosjava_test_msgs.AddTwoIntsResponse response) {
                            assertEquals(response.getSum(), 6);
                            latch.countDown();
                        }

                        @Override
                        public void onFailure(final RemoteException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        }, nodeConfiguration);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testPersistentServiceConnectionPreservesOrderingDeterministically() throws Exception {
        final CountDownServiceServerListener<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> countDownServiceServerListener =
                CountDownServiceServerListener.newDefault();
        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(SERVER_NAME + "_ordered");
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {
                final ServiceServer<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceServer =
                        connectedNode.newServiceServer(
                                SERVICE_NAME,
                                rosjava_test_msgs.AddTwoInts._TYPE,
                                (request, response) -> {
                                    if (request.getA() == 1) {
                                        try {
                                            Thread.sleep(200);
                                        } catch (final InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                    response.setSum(request.getA() + request.getB());
                                });
                serviceServer.addListener(countDownServiceServerListener);
            }
        }, nodeConfiguration);

        assertTrue(countDownServiceServerListener.awaitMasterRegistrationSuccess(1, TimeUnit.SECONDS));

        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(CLIENT + "_ordered");
            }

            @Override
            public void onStart(ConnectedNode connectedNode) {
                final ServiceClient<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceClient;
                try {
                    serviceClient = connectedNode.newServiceClient(SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE);
                } catch (final ServiceNotFoundException e) {
                    failure.compareAndSet(null, e);
                    while (latch.getCount() > 0) {
                        latch.countDown();
                    }
                    return;
                }

                final AddTwoIntsRequest firstRequest = serviceClient.newMessage();
                firstRequest.setA(1);
                firstRequest.setB(1);
                serviceClient.call(firstRequest, new ServiceResponseListener<>() {
                    @Override
                    public void onSuccess(AddTwoIntsResponse response) {
                        if (response.getSum() != 2) {
                            failure.compareAndSet(null,
                                    new AssertionError("Expected first response sum 2 but was " + response.getSum()));
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(final RemoteException e) {
                        failure.compareAndSet(null, e);
                        latch.countDown();
                    }
                });

                final AddTwoIntsRequest secondRequest = serviceClient.newMessage();
                secondRequest.setA(2);
                secondRequest.setB(2);
                serviceClient.call(secondRequest, new ServiceResponseListener<>() {
                    @Override
                    public void onSuccess(AddTwoIntsResponse response) {
                        if (response.getSum() != 4) {
                            failure.compareAndSet(null,
                                    new AssertionError("Expected second response sum 4 but was " + response.getSum()));
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(final RemoteException e) {
                        failure.compareAndSet(null, e);
                        latch.countDown();
                    }
                });
            }
        }, nodeConfiguration);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        if (failure.get() != null) {
            fail(failure.get().toString());
        }
    }

    @Test
    public void testSeparatePersistentServiceClientsRemainIndependent() throws Exception {
        final CountDownServiceServerListener<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> countDownServiceServerListener =
                CountDownServiceServerListener.newDefault();
        final CountDownLatch slowRequestEntered = new CountDownLatch(1);
        final CountDownLatch releaseSlowRequest = new CountDownLatch(1);

        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(SERVER_NAME + "_parallel");
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
                                        } catch (final InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                    response.setSum(request.getA() + request.getB());
                                });
                serviceServer.addListener(countDownServiceServerListener);
            }
        }, nodeConfiguration);

        assertTrue(countDownServiceServerListener.awaitMasterRegistrationSuccess(1, TimeUnit.SECONDS));

        final ServiceClientNode<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> client1 =
                new ServiceClientNode<>(SERVER_NAME + CLIENT + "_parallel_1", SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE);
        final ServiceClientNode<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> client2 =
                new ServiceClientNode<>(SERVER_NAME + CLIENT + "_parallel_2", SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE);

        nodeMainExecutor.execute(client1, nodeConfiguration);
        nodeMainExecutor.execute(client2, nodeConfiguration);
        assertTrue(client1.awaitConnection(1, TimeUnit.SECONDS));
        assertTrue(client2.awaitConnection(1, TimeUnit.SECONDS));

        final CountDownLatch fastCompleted = new CountDownLatch(1);
        final CountDownLatch slowCompleted = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final AddTwoIntsRequest slowRequest = client1.getServiceClient().newMessage();
        slowRequest.setA(1);
        slowRequest.setB(1);
        client1.getServiceClient().call(slowRequest, new ServiceResponseListener<>() {
            @Override
            public void onSuccess(AddTwoIntsResponse response) {
                if (response.getSum() != 2) {
                    failure.compareAndSet(null,
                            new AssertionError("Expected slow response sum 2 but was " + response.getSum()));
                }
                slowCompleted.countDown();
            }

            @Override
            public void onFailure(RemoteException e) {
                failure.compareAndSet(null, e);
                slowCompleted.countDown();
            }
        });

        assertTrue(slowRequestEntered.await(1, TimeUnit.SECONDS));

        final AddTwoIntsRequest fastRequest = client2.getServiceClient().newMessage();
        fastRequest.setA(2);
        fastRequest.setB(2);
        client2.getServiceClient().call(fastRequest, new ServiceResponseListener<>() {
            @Override
            public void onSuccess(AddTwoIntsResponse response) {
                if (response.getSum() != 4) {
                    failure.compareAndSet(null,
                            new AssertionError("Expected fast response sum 4 but was " + response.getSum()));
                }
                fastCompleted.countDown();
            }

            @Override
            public void onFailure(RemoteException e) {
                failure.compareAndSet(null, e);
                fastCompleted.countDown();
            }
        });

        assertTrue(fastCompleted.await(1, TimeUnit.SECONDS));
        releaseSlowRequest.countDown();
        assertTrue(slowCompleted.await(1, TimeUnit.SECONDS));
        if (failure.get() != null) {
            fail(failure.get().toString());
        }
    }

    @Test
    public void testPersistentServiceConnectionRecoversFromUncheckedServerFailure() throws Exception {
        final CountDownServiceServerListener<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> countDownServiceServerListener =
                CountDownServiceServerListener.newDefault();
        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(SERVER_NAME + "_runtime_failure");
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {
                ServiceServer<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceServer =
                        connectedNode.newServiceServer(
                                SERVICE_NAME,
                                rosjava_test_msgs.AddTwoInts._TYPE,
                                (request, response) -> {
                                    if (request.getA() == 1) {
                                        throw new IllegalStateException("boom");
                                    }
                                    response.setSum(request.getA() + request.getB());
                                });
                serviceServer.addListener(countDownServiceServerListener);
            }
        }, nodeConfiguration);

        assertTrue(countDownServiceServerListener.awaitMasterRegistrationSuccess(1, TimeUnit.SECONDS));

        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(CLIENT + "_runtime_failure");
            }

            @Override
            public void onStart(ConnectedNode connectedNode) {
                final ServiceClient<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceClient;
                try {
                    serviceClient = connectedNode.newServiceClient(SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE);
                } catch (final ServiceNotFoundException e) {
                    failure.compareAndSet(null, e);
                    while (latch.getCount() > 0) {
                        latch.countDown();
                    }
                    return;
                }

                final AddTwoIntsRequest failingRequest = serviceClient.newMessage();
                failingRequest.setA(1);
                failingRequest.setB(1);
                serviceClient.call(failingRequest, new ServiceResponseListener<>() {
                    @Override
                    public void onSuccess(AddTwoIntsResponse response) {
                        failure.compareAndSet(null,
                                new AssertionError("Expected unchecked server failure to produce onFailure."));
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(RemoteException e) {
                        if (e.getMessage() == null || !e.getMessage().contains("boom")) {
                            failure.compareAndSet(null,
                                    new AssertionError("Expected failure message to contain boom but was: " + e.getMessage()));
                        }
                        latch.countDown();
                    }
                });

                final AddTwoIntsRequest succeedingRequest = serviceClient.newMessage();
                succeedingRequest.setA(2);
                succeedingRequest.setB(2);
                serviceClient.call(succeedingRequest, new ServiceResponseListener<>() {
                    @Override
                    public void onSuccess(AddTwoIntsResponse response) {
                        if (response.getSum() != 4) {
                            failure.compareAndSet(null,
                                    new AssertionError("Expected second response sum 4 but was " + response.getSum()));
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(RemoteException e) {
                        failure.compareAndSet(null, e);
                        latch.countDown();
                    }
                });
            }
        }, nodeConfiguration);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        if (failure.get() != null) {
            fail(failure.get().toString());
        }
    }

    /**
     * Test the behaviour discussed in https://github.com/rosjava/rosjava_core/issues/272
     * Creating two service servers with the same name from the same nodes is prevented with a {@link DuplicateServiceException}
     *
     * See also this comment https://github.com/rosjava/rosjava_core/issues/272#issuecomment-1159455438
     * @throws Exception
     */
    @Test
    public void testMultipleServiceDeclarationSameNode() throws Exception {
        final AtomicInteger dualServiceDeclarationDetected = new AtomicInteger(0);
        final CountDownServiceServerListener<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> countDownServiceServerListener =
                CountDownServiceServerListener.newFromCounts(1, 0, 0, 2);

        final AbstractNodeMain node = new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(SERVER_NAME);
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {

                try {
                    final ServiceServer<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceServer1 =
                            connectedNode.newServiceServer(
                                    SERVICE_NAME,
                                    rosjava_test_msgs.AddTwoInts._TYPE,
                                    (request, response) -> response.setSum(request.getA() + request.getB()));
                    serviceServer1.addListener(countDownServiceServerListener);
                } catch (final DuplicateServiceException e) {
                    // Only one ServiceServer with a given name can be created.
                    dualServiceDeclarationDetected.incrementAndGet();
                }
                try {
                    final ServiceServer<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceServer1 =
                            connectedNode.newServiceServer(
                                    SERVICE_NAME,
                                    rosjava_test_msgs.AddTwoInts._TYPE,
                                    (request, response) -> response.setSum(request.getA() + request.getB()));
                    serviceServer1.addListener(countDownServiceServerListener);
                } catch (final DuplicateServiceException e) {
                    // Only one ServiceServer with a given name can be created.
                    dualServiceDeclarationDetected.incrementAndGet();
                }


            }
        };
        this.nodeMainExecutor.execute(node, nodeConfiguration);

        assertTrue(countDownServiceServerListener.awaitMasterRegistrationSuccess(2, TimeUnit.SECONDS));
        if (dualServiceDeclarationDetected.get() != 1) {
            fail("Dual registration not detected");
        }

    }


    /**
     * Test the behaviour discussed in https://github.com/rosjava/rosjava_core/issues/272
     * Creating two service servers with the same name from two different nodes results in the first service server being called even from new clients.
     *
     * See also this comment https://github.com/rosjava/rosjava_core/issues/272#issuecomment-1159455438
     * @throws Exception
     */
    @Test
    public void testMultipleServiceDeclarationDifferentNodes() throws Exception {
        final CountDownLatch clientsCompleted = new CountDownLatch(4);
        final CountDownLatch  server1started=new CountDownLatch(1);
        final CountDownLatch  server2started=new CountDownLatch(1);
        final CountDownServiceServerListener<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> countDownServiceServerListener =
                CountDownServiceServerListener.newFromCounts(1, 0, 0, 2);

        final AbstractNodeMain node1 = new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(SERVER_NAME + 1);
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {
                try {
                    final ServiceServer<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceServer1 =
                            connectedNode.newServiceServer(
                                    SERVICE_NAME,
                                    rosjava_test_msgs.AddTwoInts._TYPE,
                                    (request, response) -> response.setSum(1));
                    serviceServer1.addListener(countDownServiceServerListener);
                } catch (final DuplicateServiceException e) {
                    // Only one ServiceServer with a given name can be created.

                }
                server1started.countDown();
            }


        };
        this.nodeMainExecutor.execute(node1, nodeConfiguration);
        server1started.await();
        final ServiceClientNode<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> client1 = new ServiceClientNode<>(SERVER_NAME + CLIENT + 1, SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE);

        this.nodeMainExecutor.execute(client1, nodeConfiguration);
        client1.awaitConnection();
        client1.getServiceClient().call(client1.getServiceClient().newMessage(), new ServiceResponseListener<>() {
            @Override
            public void onSuccess(AddTwoIntsResponse response) {
                Assert.assertEquals(response.getSum(), 1);
                clientsCompleted.countDown();
            }

            @Override
            public void onFailure(RemoteException e) {

            }
        });


        final AbstractNodeMain node2 = new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(SERVER_NAME + 2);
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {

                try {
                    final ServiceServer<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceServer1 =
                            connectedNode.newServiceServer(
                                    SERVICE_NAME,
                                    rosjava_test_msgs.AddTwoInts._TYPE,
                                    (request, response) -> response.setSum(2));
                    serviceServer1.addListener(countDownServiceServerListener);
                } catch (final DuplicateServiceException e) {
                    // Only one ServiceServer with a given name can be created.

                }
                server2started.countDown();
            }
        };

        this.nodeMainExecutor.execute(node2, nodeConfiguration);
        server2started.await();
        client1.getServiceClient().call(client1.getServiceClient().newMessage(), new ServiceResponseListener<>() {
            @Override
            public void onSuccess(AddTwoIntsResponse response) {
                Assert.assertEquals(response.getSum(), 1);
                clientsCompleted.countDown();
            }

            @Override
            public void onFailure(RemoteException e) {

            }
        });


        final ServiceClientNode<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> client2 = new ServiceClientNode<>(SERVER_NAME + CLIENT + 2, SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE);

        this.nodeMainExecutor.execute(client2, nodeConfiguration);
        client2.awaitConnection();
        client2.getServiceClient().call(client2.getServiceClient().newMessage(), new ServiceResponseListener<>() {
            @Override
            public void onSuccess(AddTwoIntsResponse response) {
                Assert.assertEquals(response.getSum(), 2);
                clientsCompleted.countDown();
            }

            @Override
            public void onFailure(RemoteException e) {

            }
        });
        client1.getServiceClient().call(client1.getServiceClient().newMessage(), new ServiceResponseListener<>() {
            @Override
            public void onSuccess(AddTwoIntsResponse response) {
                Assert.assertEquals(response.getSum(), 1);
                clientsCompleted.countDown();
            }

            @Override
            public void onFailure(RemoteException e) {

            }
        });
        clientsCompleted.await();
    }


    @Test
    public void testRequestFailure() throws Exception {
        final String errorMessage = "Error!";
        final CountDownServiceServerListener<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> countDownServiceServerListener =
                CountDownServiceServerListener.newDefault();
        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(SERVER_NAME);
            }

            @Override
            public void onStart(ConnectedNode connectedNode) {
                ServiceServer<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceServer =
                        connectedNode
                                .newServiceServer(
                                        SERVICE_NAME,
                                        rosjava_test_msgs.AddTwoInts._TYPE,
                                        (request, response) -> {
                                            throw new ServiceException(errorMessage);
                                        });
                serviceServer.addListener(countDownServiceServerListener);
            }
        }, nodeConfiguration);

        assertTrue(countDownServiceServerListener.awaitMasterRegistrationSuccess(1, TimeUnit.SECONDS));

        final CountDownLatch latch = new CountDownLatch(1);
        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(CLIENT);
            }

            @Override
            public void onStart(ConnectedNode connectedNode) {
                ServiceClient<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceClient;
                try {
                    serviceClient = connectedNode.newServiceClient(SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE);
                } catch (final ServiceNotFoundException e) {
                    throw new RosRuntimeException(e);
                }
                rosjava_test_msgs.AddTwoIntsRequest request = serviceClient.newMessage();
                serviceClient.call(request, new ServiceResponseListener<rosjava_test_msgs.AddTwoIntsResponse>() {
                    @Override
                    public void onSuccess(rosjava_test_msgs.AddTwoIntsResponse message) {
                        fail();
                    }

                    @Override
                    public void onFailure(RemoteException e) {
                        assertEquals(e.getMessage(), errorMessage);
                        latch.countDown();
                    }
                });
            }
        }, nodeConfiguration);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }
}
