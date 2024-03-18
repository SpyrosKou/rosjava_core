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

import com.google.common.annotations.VisibleForTesting;

import org.ros.address.AdvertiseAddress;
import org.ros.address.BindAddress;
import org.ros.address.PrivateAdvertiseAddressFactory;
import org.ros.address.PublicAdvertiseAddressFactory;
import org.ros.internal.node.server.master.MasterServer;

import java.net.URI;
import java.util.concurrent.TimeUnit;

// TODO(damonkohler): Add /rosout node.

/**
 * {@link RosCore} is a collection of nodes and programs that are pre-requisites
 * of a ROS-based system. You must have a {@link RosCore} (either this
 * implementation or the default Python implementation distributed with ROS)
 * running in order for ROS nodes to communicate.
 *
 * @author damonkohler@google.com (Damon Kohler)
 * @see <a href="http://www.ros.org/wiki/roscore">roscore documentation</a>
 */
public final class RosCore {
    private static final PrivateAdvertiseAddressFactory PRIVATE_ADVERTISE_ADDRESS_FACTORY = new PrivateAdvertiseAddressFactory();
    private static final PublicAdvertiseAddressFactory PUBLIC_ADVERTISE_ADDRESS_FACTORY = new PublicAdvertiseAddressFactory();

    private final MasterServer masterServer;

    /**
     *
     * @param host should be public for public usage
     * @param port
     * @return
     */
    public static RosCore newPublic(String host, int port) {
        final PublicAdvertiseAddressFactory publicAdvertiseAddressFactory = new PublicAdvertiseAddressFactory(host);
        final AdvertiseAddress advertiseAddress = publicAdvertiseAddressFactory.newDefault();
        return new RosCore(BindAddress.newPublic(port), advertiseAddress);
    }

    public static RosCore newPublic(int port) {
        return new RosCore(BindAddress.newPublic(port), PUBLIC_ADVERTISE_ADDRESS_FACTORY.newDefault());
    }

    public static final RosCore newPublic() {
        return new RosCore(BindAddress.newPublic(), PUBLIC_ADVERTISE_ADDRESS_FACTORY.newDefault());
    }

    public static final RosCore newPrivate(int port) {

        return new RosCore(BindAddress.newPrivate(port), PRIVATE_ADVERTISE_ADDRESS_FACTORY.newDefault());
    }

    public static final RosCore newPrivate() {
        return new RosCore(BindAddress.newPrivate(), PRIVATE_ADVERTISE_ADDRESS_FACTORY.newDefault());
    }

    private RosCore(BindAddress bindAddress, AdvertiseAddress advertiseAddress) {
        this.masterServer = new MasterServer(bindAddress, advertiseAddress);
    }

    public void start() {
        this.masterServer.start();
    }

    public URI getUri() {
        return masterServer.getUri();
    }

    public final void awaitStart() throws InterruptedException {
        this.masterServer.awaitStart();
    }

    public final boolean awaitStart(long timeout, TimeUnit unit) throws InterruptedException {
        return this.masterServer.awaitStart(timeout, unit);
    }

    public final void shutdown() {
        this.masterServer.shutdown();
    }

    public final boolean awaitShutdown(long timeout, TimeUnit unit) throws InterruptedException {
        return this.masterServer.awaitShutdown(timeout, unit);
    }

    @VisibleForTesting
    public MasterServer getMasterServer() {
        return this.masterServer;
    }
}
