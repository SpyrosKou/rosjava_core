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

package org.ros.internal.node.server;

import com.google.common.base.Preconditions;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl;
import org.ros.address.AdvertiseAddress;
import org.ros.address.BindAddress;
import org.ros.exception.RosRuntimeException;
import org.ros.internal.system.Process;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Base class for an XML-RPC server.
 *
 * @author damonkohler@google.com (Damon Kohler)
 */
public abstract class XmlRpcServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final RosWebServer rosWebServer;
    private final AdvertiseAddress advertiseAddress;
    private final CountDownLatch startLatch = new CountDownLatch(1);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public XmlRpcServer(BindAddress bindAddress, AdvertiseAddress advertiseAddress) {
        final InetSocketAddress address = bindAddress.toInetSocketAddress();
        this.rosWebServer = new RosWebServer(address.getPort(), address.getAddress());
        this.advertiseAddress = advertiseAddress;
        this.advertiseAddress.setPortCallable(() -> rosWebServer.getPort());
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("New WebServer Address:" + address.getAddress() + " port:" + address.getPort());
        }
    }

    /**
     * Start up the remote calling server.
     *
     * @param instance an instance of the remoting server class
     */
    public final <T extends org.ros.internal.node.xmlrpc.XmlRpcEndpoint> void start(final T instance) {
        Preconditions.checkNotNull(instance);
        final org.apache.xmlrpc.server.XmlRpcServer xmlRpcServer = this.rosWebServer.getXmlRpcServer();
        final RosPropertyHandlerMapping phm = new RosPropertyHandlerMapping();
        phm.setRequestProcessorFactoryFactory(new NodeRequestProcessorFactoryFactory<T>(instance));
        try {
            phm.addHandler("", instance.getClass());
        } catch (final XmlRpcException e) {
            throw new RosRuntimeException(e);
        }
        if(LOGGER.isTraceEnabled()){
            try {
                LOGGER.trace("Class:"+this.getClass().getCanonicalName()+": Methods:"+Arrays.toString(phm.getListMethods()));
            }catch (final Exception exception){
                LOGGER.debug("Class:"+this.getClass().getCanonicalName()+": Error while trying to list methods.");
            }

        }
        xmlRpcServer.setHandlerMapping(phm);
        final XmlRpcServerConfigImpl serverConfig = (XmlRpcServerConfigImpl) xmlRpcServer.getConfig();
        serverConfig.setEnabledForExtensions(false);
        serverConfig.setContentLengthOptional(false);
        try {
            this.rosWebServer.start();
        } catch (final IOException e) {
            throw new RosRuntimeException(e);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Bound to: " + getUri());
        }
        this.startLatch.countDown();
    }

    /**
     * Shut down all resources, should call {@link XmlRpcServer#superShutdown()} and {@link XmlRpcServer#shutdownFinalization()}
     */
    public abstract void shutdown();

    /**
     * Shut the remote call server down.
     */
    protected final void superShutdown() {
        this.rosWebServer.shutdown();
    }

    /**
     * Should be the last statement of {@link XmlRpcServer#shutdown()} during resource shutting down.
     * It is required to be called only once.
     */
    protected final void shutdownFinalization() {
        this.shutdownLatch.countDown();
    }

    /**
     * @return the {@link URI} of the server
     */
    //Not final for mocking
    public URI getUri() {
        return this.advertiseAddress.toUri("http");
    }

    public final InetSocketAddress getAddress() {
        return this.advertiseAddress.toInetSocketAddress();
    }

    public final AdvertiseAddress getAdvertiseAddress() {
        return this.advertiseAddress;
    }

    public final void awaitStart() throws InterruptedException {
        this.startLatch.await();
    }

    public final boolean awaitStart(long timeout, TimeUnit unit) throws InterruptedException {
        return this.startLatch.await(timeout, unit);
    }

    public final boolean awaitShutdown(long timeout, TimeUnit unit) throws InterruptedException {
        return this.shutdownLatch.await(timeout, unit);
    }

    /**
     * @return PID of node process if available, throws
     * {@link UnsupportedOperationException} otherwise.
     */
    //Not final for mocking
    public int getPid() {
        return Process.getPid();
    }
}
