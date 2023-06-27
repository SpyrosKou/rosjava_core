package org.ros.internal.node.server;

import org.apache.xmlrpc.server.XmlRpcStreamServer;
import org.apache.xmlrpc.webserver.RosConnectionServer;
import org.apache.xmlrpc.webserver.WebServer;

import java.net.InetAddress;

/**
 * @author Spyros Koukas
 */
final class RosWebServer extends WebServer {
    public RosWebServer(int pPort, InetAddress pAddr) {
        super(pPort, pAddr);
    }
    protected final XmlRpcStreamServer newXmlRpcStreamServer(){
        return new RosConnectionServer();
    }

}
