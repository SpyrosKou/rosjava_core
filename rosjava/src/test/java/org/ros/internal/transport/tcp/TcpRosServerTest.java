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

package org.ros.internal.transport.tcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ros.address.*;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static junit.framework.Assert.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author kwc@willowgarage.com (Ken Conley)
 * @author damonkohler@google.com (Damon Kohler)
 * @author Spyros Koukas
 */
public class TcpRosServerTest {
  private final PublicAdvertiseAddressFactory publicAdvertiseAddressFactory = new PublicAdvertiseAddressFactory();
  private ExecutorService executorService;

  @BeforeEach
  public void setup() {
    executorService = Executors.newCachedThreadPool();
  }

  @AfterEach
  public void tearDown() {
    executorService.shutdown();
  }

  @Test
  public void testGetAddressFailsIfServerNotRunning() {
    TcpRosServer tcpRosServer =
            new TcpRosServer(BindAddress.newPublic(),publicAdvertiseAddressFactory.newDefault(), null, null,
                    executorService);
    try {

      tcpRosServer.getAddress();
      fail();
    } catch (RuntimeException e) {
      // getAddress() must fail when the server is not running.
    }

    tcpRosServer.start();
    InetSocketAddress address = tcpRosServer.getAddress();
    assertTrue(address.getPort() > 0);
    assertEquals(InetAddressFactory.newNonLoopback().getCanonicalHostName(), address.getAddress()
        .getHostName());
    tcpRosServer.shutdown();

    try {
      tcpRosServer.getAddress();
      fail();
    } catch (RuntimeException e) {
      // getAddress() must fail when the server is not running.
    }
  }

  @Test
  public void testFailIfPortTaken() {
    TcpRosServer firstServer =
        new TcpRosServer(BindAddress.newPublic(), publicAdvertiseAddressFactory.newDefault(), null, null,
            executorService);
    firstServer.start();
    try {
      TcpRosServer secondServer =
          new TcpRosServer(BindAddress.newPublic(firstServer.getAddress().getPort()),
                  publicAdvertiseAddressFactory.newDefault(), null, null, executorService);
      secondServer.start();
      fail();
    } catch (RuntimeException e) {
      // Starting a server on an already used port must fail.
    }
    firstServer.shutdown();
  }

  @Test
  public void testFailIfStartedWhileRunning() {
    TcpRosServer tcpRosServer =
        new TcpRosServer(BindAddress.newPublic(), publicAdvertiseAddressFactory.newDefault(), null, null,
            executorService);
    tcpRosServer.start();
    try {
      tcpRosServer.start();
      fail();
    } catch (RuntimeException e) {
      // Starting the server twice must fail.
    }
    tcpRosServer.shutdown();
  }

  @Test
  public void testCanRestartAfterShutdown() {
    TcpRosServer tcpRosServer =
        new TcpRosServer(BindAddress.newPublic(), publicAdvertiseAddressFactory.newDefault(), null, null,
            executorService);
    tcpRosServer.start();
    tcpRosServer.shutdown();

    tcpRosServer.start();
    InetSocketAddress restartedAddress = tcpRosServer.getAddress();
    assertTrue(restartedAddress.getPort() > 0);
    tcpRosServer.shutdown();
  }

  @Test
  public void testShutdownDoesNotShutdownProvidedExecutor() throws ExecutionException, InterruptedException {
    TcpRosServer tcpRosServer =
        new TcpRosServer(BindAddress.newPublic(), publicAdvertiseAddressFactory.newDefault(), null, null,
            executorService);

    tcpRosServer.start();
    tcpRosServer.shutdown();

    assertEquals("still-running", executorService.submit(() -> "still-running").get());
    assertFalse(executorService.isShutdown());
  }
}
