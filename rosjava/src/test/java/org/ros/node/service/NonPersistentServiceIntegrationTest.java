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
import org.ros.address.BindAddress;
import org.ros.exception.RemoteException;
import org.ros.exception.RosRuntimeException;
import org.ros.exception.ServiceNotFoundException;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.NodeConfiguration;
import rosjava_test_msgs.AddTwoIntsRequest;
import rosjava_test_msgs.AddTwoIntsResponse;

import java.io.IOException;
import java.net.URI;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class NonPersistentServiceIntegrationTest extends RosTest {

    private static final String SERVICE_NAME = "/add_two_ints";
    private static final String SERVER_NAME = "server";
    private static final String CLIENT = "client";

    @Test
    public void testNonPersistentServiceClientsAreNotCached() throws Exception {
        final CountDownServiceServerListener<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> listener =
                CountDownServiceServerListener.newDefault();
        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(SERVER_NAME + "_nonpersistent_cache");
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {
                final ServiceServer<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceServer =
                        connectedNode.newServiceServer(SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE,
                                (request, response) -> response.setSum(request.getA() + request.getB()));
                serviceServer.addListener(listener);
            }
        }, nodeConfiguration);

        assertTrue(listener.awaitMasterRegistrationSuccess(1, TimeUnit.SECONDS));

        final CountDownLatch assertionLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(CLIENT + "_nonpersistent_cache");
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {
                try {
                    final ServiceClient<AddTwoIntsRequest, AddTwoIntsResponse> persistent =
                            connectedNode.newServiceClient(SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE);
                    final ServiceClient<AddTwoIntsRequest, AddTwoIntsResponse> persistentDuplicate =
                            connectedNode.newServiceClient(SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE);
                    final ServiceCaller<AddTwoIntsRequest, AddTwoIntsResponse> nonPersistent =
                            connectedNode.newNonPersistentServiceClient(SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE);
                    final ServiceCaller<AddTwoIntsRequest, AddTwoIntsResponse> nonPersistentDuplicate =
                            connectedNode.newNonPersistentServiceClient(SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE);
                    assertSame(persistent, persistentDuplicate);
                    assertNotSame(nonPersistent, nonPersistentDuplicate);
                } catch (final Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                } finally {
                    assertionLatch.countDown();
                }
            }
        }, nodeConfiguration);

        assertTrue(assertionLatch.await(1, TimeUnit.SECONDS));
        if (failure.get() != null) {
            fail(failure.get().toString());
        }
    }

    @Test
    public void testNonPersistentServiceClientUsesIndependentConnectionsPerCall() throws Exception {
        final CountDownServiceServerListener<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> listener =
                CountDownServiceServerListener.newDefault();
        final CountDownLatch slowRequestEntered = new CountDownLatch(1);
        final CountDownLatch releaseSlowRequest = new CountDownLatch(1);

        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(SERVER_NAME + "_nonpersistent_parallel");
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {
                final ServiceServer<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceServer =
                        connectedNode.newServiceServer(SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE,
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
                serviceServer.addListener(listener);
            }
        }, nodeConfiguration);

        assertTrue(listener.awaitMasterRegistrationSuccess(1, TimeUnit.SECONDS));

        final CountDownLatch callerReady = new CountDownLatch(1);
        final AtomicReference<ServiceCaller<AddTwoIntsRequest, AddTwoIntsResponse>> callerRef = new AtomicReference<>();
        final AtomicReference<Throwable> setupFailure = new AtomicReference<>();
        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(CLIENT + "_nonpersistent_parallel");
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {
                try {
                    callerRef.set(connectedNode.newNonPersistentServiceClient(SERVICE_NAME,
                            rosjava_test_msgs.AddTwoInts._TYPE));
                } catch (final ServiceNotFoundException e) {
                    setupFailure.compareAndSet(null, e);
                } finally {
                    callerReady.countDown();
                }
            }
        }, nodeConfiguration);

        assertTrue(callerReady.await(1, TimeUnit.SECONDS));
        if (setupFailure.get() != null) {
            throw new RosRuntimeException(setupFailure.get());
        }

        final ServiceCaller<AddTwoIntsRequest, AddTwoIntsResponse> caller = callerRef.get();
        final CountDownLatch fastCompleted = new CountDownLatch(1);
        final CountDownLatch slowCompleted = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final AddTwoIntsRequest slowRequest = caller.newMessage();
        slowRequest.setA(1);
        slowRequest.setB(1);
        caller.call(slowRequest, new ServiceResponseListener<>() {
            @Override
            public void onSuccess(final AddTwoIntsResponse response) {
                if (response.getSum() != 2) {
                    failure.compareAndSet(null,
                            new AssertionError("Expected slow response sum 2 but was " + response.getSum()));
                }
                slowCompleted.countDown();
            }

            @Override
            public void onFailure(final RemoteException e) {
                failure.compareAndSet(null, e);
                slowCompleted.countDown();
            }
        });

        assertTrue(slowRequestEntered.await(1, TimeUnit.SECONDS));

        final AddTwoIntsRequest fastRequest = caller.newMessage();
        fastRequest.setA(2);
        fastRequest.setB(2);
        caller.call(fastRequest, new ServiceResponseListener<>() {
            @Override
            public void onSuccess(final AddTwoIntsResponse response) {
                if (response.getSum() != 4) {
                    failure.compareAndSet(null,
                            new AssertionError("Expected fast response sum 4 but was " + response.getSum()));
                }
                fastCompleted.countDown();
            }

            @Override
            public void onFailure(final RemoteException e) {
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
    public void testNonPersistentServiceClientShutdownFailsInFlightRequest() throws Exception {
        final CountDownServiceServerListener<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> listener =
                CountDownServiceServerListener.newDefault();
        final CountDownLatch slowRequestEntered = new CountDownLatch(1);
        final CountDownLatch releaseSlowRequest = new CountDownLatch(1);

        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(SERVER_NAME + "_nonpersistent_shutdown");
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {
                final ServiceServer<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceServer =
                        connectedNode.newServiceServer(SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE,
                                (request, response) -> {
                                    slowRequestEntered.countDown();
                                    try {
                                        releaseSlowRequest.await();
                                    } catch (final InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                    response.setSum(request.getA() + request.getB());
                                });
                serviceServer.addListener(listener);
            }
        }, nodeConfiguration);

        assertTrue(listener.awaitMasterRegistrationSuccess(1, TimeUnit.SECONDS));

        final CountDownLatch callerReady = new CountDownLatch(1);
        final AtomicReference<ServiceCaller<AddTwoIntsRequest, AddTwoIntsResponse>> callerRef = new AtomicReference<>();
        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(CLIENT + "_nonpersistent_shutdown");
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {
                try {
                    callerRef.set(connectedNode.newNonPersistentServiceClient(SERVICE_NAME,
                            rosjava_test_msgs.AddTwoInts._TYPE));
                } catch (final ServiceNotFoundException e) {
                    throw new RosRuntimeException(e);
                } finally {
                    callerReady.countDown();
                }
            }
        }, nodeConfiguration);

        assertTrue(callerReady.await(1, TimeUnit.SECONDS));
        final ServiceCaller<AddTwoIntsRequest, AddTwoIntsResponse> caller = callerRef.get();

        final CountDownLatch callbackLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AddTwoIntsRequest slowRequest = caller.newMessage();
        slowRequest.setA(1);
        slowRequest.setB(1);
        caller.call(slowRequest, new ServiceResponseListener<>() {
            @Override
            public void onSuccess(final AddTwoIntsResponse response) {
                failure.compareAndSet(null,
                        new AssertionError("Expected in-flight request to fail after shutdown."));
                callbackLatch.countDown();
            }

            @Override
            public void onFailure(final RemoteException e) {
                if (e.getMessage() == null || e.getMessage().isEmpty()) {
                    failure.compareAndSet(null,
                            new AssertionError("Expected shutdown failure message for in-flight request."));
                }
                callbackLatch.countDown();
            }
        });

        assertTrue(slowRequestEntered.await(1, TimeUnit.SECONDS));
        caller.shutdown();
        releaseSlowRequest.countDown();

        assertTrue(callbackLatch.await(2, TimeUnit.SECONDS));
        if (failure.get() != null) {
            fail(failure.get().toString());
        }
    }

    @Test
    public void testNonPersistentServiceClientRecoversFromUncheckedServerFailure() throws Exception {
        final CountDownServiceServerListener<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> listener =
                CountDownServiceServerListener.newDefault();
        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(SERVER_NAME + "_nonpersistent_runtime_failure");
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {
                final ServiceServer<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceServer =
                        connectedNode.newServiceServer(SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE,
                                (request, response) -> {
                                    if (request.getA() == 1) {
                                        throw new IllegalStateException("boom");
                                    }
                                    response.setSum(request.getA() + request.getB());
                                });
                serviceServer.addListener(listener);
            }
        }, nodeConfiguration);

        assertTrue(listener.awaitMasterRegistrationSuccess(1, TimeUnit.SECONDS));

        final CountDownLatch callerReady = new CountDownLatch(1);
        final AtomicReference<ServiceCaller<AddTwoIntsRequest, AddTwoIntsResponse>> callerRef = new AtomicReference<>();
        final AtomicReference<Throwable> setupFailure = new AtomicReference<>();
        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(CLIENT + "_nonpersistent_runtime_failure");
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {
                try {
                    callerRef.set(connectedNode.newNonPersistentServiceClient(SERVICE_NAME,
                            rosjava_test_msgs.AddTwoInts._TYPE));
                } catch (final Throwable throwable) {
                    setupFailure.compareAndSet(null, throwable);
                } finally {
                    callerReady.countDown();
                }
            }
        }, nodeConfiguration);

        assertTrue(callerReady.await(1, TimeUnit.SECONDS));
        if (setupFailure.get() != null) {
            fail(setupFailure.get().toString());
        }

        final ServiceCaller<AddTwoIntsRequest, AddTwoIntsResponse> caller = callerRef.get();
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final AddTwoIntsRequest failingRequest = caller.newMessage();
        failingRequest.setA(1);
        failingRequest.setB(1);
        caller.call(failingRequest, new ServiceResponseListener<>() {
            @Override
            public void onSuccess(final AddTwoIntsResponse response) {
                failure.compareAndSet(null,
                        new AssertionError("Expected unchecked server failure to produce onFailure."));
                latch.countDown();
            }

            @Override
            public void onFailure(final RemoteException e) {
                if (e.getMessage() == null || !e.getMessage().contains("boom")) {
                    failure.compareAndSet(null,
                            new AssertionError("Expected failure message to contain boom but was: " + e.getMessage()));
                }
                latch.countDown();
            }
        });

        final AddTwoIntsRequest succeedingRequest = caller.newMessage();
        succeedingRequest.setA(2);
        succeedingRequest.setB(2);
        caller.call(succeedingRequest, new ServiceResponseListener<>() {
            @Override
            public void onSuccess(final AddTwoIntsResponse response) {
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

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        if (failure.get() != null) {
            fail(failure.get().toString());
        }
    }

    @Test
    public void testNonPersistentServiceClientRefreshesServiceUriAfterServerRestart() throws Exception {
        final NodeConfiguration firstServerConfiguration = NodeConfiguration.copyOf(nodeConfiguration)
                .setTcpRosBindAddress(BindAddress.newPrivate(allocateFreePort()));
        final NodeConfiguration secondServerConfiguration = NodeConfiguration.copyOf(nodeConfiguration)
                .setTcpRosBindAddress(BindAddress.newPrivate(allocateFreePort()));
        final CountDownServiceServerListener<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> firstListener =
                CountDownServiceServerListener.newDefault();
        final AbstractNodeMain firstServer = new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(SERVER_NAME + "_nonpersistent_restart_1");
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {
                final ServiceServer<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceServer =
                        connectedNode.newServiceServer(SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE,
                                (request, response) -> response.setSum(1));
                serviceServer.addListener(firstListener);
            }
        };
        nodeMainExecutor.execute(firstServer, firstServerConfiguration);

        assertTrue(firstListener.awaitMasterRegistrationSuccess(1, TimeUnit.SECONDS));

        final CountDownLatch callerReady = new CountDownLatch(1);
        final AtomicReference<ServiceCaller<AddTwoIntsRequest, AddTwoIntsResponse>> callerRef = new AtomicReference<>();
        final AtomicReference<ConnectedNode> connectedNodeRef = new AtomicReference<>();
        final AtomicReference<Throwable> setupFailure = new AtomicReference<>();
        nodeMainExecutor.execute(new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(CLIENT + "_nonpersistent_restart");
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {
                try {
                    connectedNodeRef.set(connectedNode);
                    callerRef.set(connectedNode.newNonPersistentServiceClient(SERVICE_NAME,
                            rosjava_test_msgs.AddTwoInts._TYPE));
                } catch (final Throwable throwable) {
                    setupFailure.compareAndSet(null, throwable);
                } finally {
                    callerReady.countDown();
                }
            }
        }, nodeConfiguration);

        assertTrue(callerReady.await(1, TimeUnit.SECONDS));
        if (setupFailure.get() != null) {
            fail(setupFailure.get().toString());
        }

        final ConnectedNode clientNode = connectedNodeRef.get();
        final ServiceCaller<AddTwoIntsRequest, AddTwoIntsResponse> caller = callerRef.get();
        final URI firstUri = clientNode.lookupServiceUri(SERVICE_NAME);
        assertTrue(firstUri != null);
        assertServiceCallReturns(caller, 1);

        nodeMainExecutor.shutdownNodeMain(firstServer);

        final CountDownServiceServerListener<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> secondListener =
                CountDownServiceServerListener.newDefault();
        final AbstractNodeMain secondServer = new AbstractNodeMain() {
            @Override
            public GraphName getDefaultNodeName() {
                return GraphName.of(SERVER_NAME + "_nonpersistent_restart_2");
            }

            @Override
            public void onStart(final ConnectedNode connectedNode) {
                final ServiceServer<rosjava_test_msgs.AddTwoIntsRequest, rosjava_test_msgs.AddTwoIntsResponse> serviceServer =
                        connectedNode.newServiceServer(SERVICE_NAME, rosjava_test_msgs.AddTwoInts._TYPE,
                                (request, response) -> response.setSum(2));
                serviceServer.addListener(secondListener);
            }
        };
        nodeMainExecutor.execute(secondServer, secondServerConfiguration);

        assertTrue(secondListener.awaitMasterRegistrationSuccess(1, TimeUnit.SECONDS));
        final URI secondUri = awaitDifferentServiceUri(clientNode, firstUri, 2, TimeUnit.SECONDS);
        assertTrue(secondUri != null);
        assertNotEquals(firstUri, secondUri);
        assertServiceCallReturns(caller, 2);
    }

    private static void assertServiceCallReturns(final ServiceCaller<AddTwoIntsRequest, AddTwoIntsResponse> caller,
                                                 final long expectedSum) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AddTwoIntsRequest request = caller.newMessage();
        caller.call(request, new ServiceResponseListener<>() {
            @Override
            public void onSuccess(final AddTwoIntsResponse response) {
                if (response.getSum() != expectedSum) {
                    failure.compareAndSet(null,
                            new AssertionError("Expected response sum " + expectedSum + " but was " + response.getSum()));
                }
                latch.countDown();
            }

            @Override
            public void onFailure(final RemoteException e) {
                failure.compareAndSet(null, e);
                latch.countDown();
            }
        });
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        if (failure.get() != null) {
            fail(failure.get().toString());
        }
    }

    private static URI awaitDifferentServiceUri(final ConnectedNode connectedNode, final URI previousUri,
                                                final long timeout, final TimeUnit unit) throws InterruptedException {
        final long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        URI currentUri = connectedNode.lookupServiceUri(SERVICE_NAME);
        while (System.nanoTime() < deadlineNanos) {
            if (currentUri != null && !currentUri.equals(previousUri)) {
                return currentUri;
            }
            Thread.sleep(50);
            currentUri = connectedNode.lookupServiceUri(SERVICE_NAME);
        }
        return currentUri;
    }

    private static int allocateFreePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }
}
