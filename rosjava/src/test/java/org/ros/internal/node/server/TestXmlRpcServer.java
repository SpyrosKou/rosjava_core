package org.ros.internal.node.server;

import org.ros.address.AdvertiseAddress;
import org.ros.address.BindAddress;

public final class TestXmlRpcServer extends XmlRpcServer {
    public TestXmlRpcServer(BindAddress bindAddress, AdvertiseAddress advertiseAddress) {
        super(bindAddress, advertiseAddress);
    }

    @Override
    public final void shutdown() {
        super.shutdown();
        super.shutdownFinalization();
    }
}
