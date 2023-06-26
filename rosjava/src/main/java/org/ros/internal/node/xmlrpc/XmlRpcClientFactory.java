/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ros.internal.node.xmlrpc;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.TimingOutCallback;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.common.TypeConverter;
import org.apache.xmlrpc.common.TypeConverterFactory;
import org.apache.xmlrpc.common.TypeConverterFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;

/**
 * Modified version of {@link org.apache.xmlrpc.client.util.ClientFactory} that
 * requires timeouts in calls.
 *
 * @param <T> the type of {@link XmlRpcEndpoint} to create clients for
 * @author kwc@willowgarage.com (Ken Conley)
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class XmlRpcClientFactory<T extends org.ros.internal.node.xmlrpc.XmlRpcEndpoint> {
    public static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final XmlRpcClient client;
    private final TypeConverterFactory typeConverterFactory;
    //uninitialized boolean defaults to false
    private boolean objectMethodLocal = false;

    /**
     * Creates a new instance.
     *
     * @param pClient               A fully configured XML-RPC client, which is used internally to
     *                              perform XML-RPC calls.
     * @param pTypeConverterFactory Creates instances of {@link TypeConverterFactory}, which are used
     *                              to transform the result object in its target representation.
     */
    public XmlRpcClientFactory(final XmlRpcClient pClient,final TypeConverterFactory pTypeConverterFactory) {
        this.typeConverterFactory = pTypeConverterFactory;
        this.client = pClient;
    }

    /**
     * Creates a new instance. Shortcut for
     *
     * <pre>
     * new ClientFactory(pClient, new TypeConverterFactoryImpl());
     * </pre>
     *
     * @param pClient A fully configured XML-RPC client, which is used internally to
     *                perform XML-RPC calls.
     * @see TypeConverterFactoryImpl
     */
    public XmlRpcClientFactory(final XmlRpcClient pClient) {
        this(pClient, new TypeConverterFactoryImpl());
    }

    /**
     * Returns the factories client.
     */
    public XmlRpcClient getClient() {
        return client;
    }

    /**
     * Returns, whether a method declared by the {@link Object Object class} is
     * performed by the local object, rather than by the server. Defaults to true.
     */
    public final boolean isObjectMethodLocal() {
        return objectMethodLocal;
    }

    /**
     * Sets, whether a method declared by the {@link Object Object class} is
     * performed by the local object, rather than by the server. Defaults to true.
     */
    public void setObjectMethodLocal(boolean pObjectMethodLocal) {
        objectMethodLocal = pObjectMethodLocal;
    }

    /**
     * Creates an object, which is implementing the given interface. The objects
     * methods are internally calling an XML-RPC server by using the factories
     * client.
     *
     * @param pClassLoader The class loader, which is being used for loading classes, if
     *                     required.
     * @param pClass       Interface, which is being implemented.
     * @param pRemoteName  Handler name, which is being used when calling the server. This is
     *                     used for composing the method name. For example, if
     *                     <code>pRemoteName</code> is "Foo" and you want to invoke the
     *                     method "bar" in the handler, then the full method name would be
     *                     "Foo.bar".
     */
    public final Object newInstance(
            final ClassLoader pClassLoader
            , final Class<T> pClass
            , final String pRemoteName
            , final long timeout) {
        try {
            final LocalInvocationHandler localInvocationHandler = new LocalInvocationHandler(pRemoteName, timeout);
            final Object proxyInstance = Proxy.newProxyInstance(pClassLoader, new Class[]{pClass}, localInvocationHandler);
            return proxyInstance;
        } catch (final Exception exception) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error(ExceptionUtils.getStackTrace(exception));
            }
            throw new RuntimeException("Could not create new instance", exception);
        }
    }

    public final class LocalInvocationHandler implements InvocationHandler {
        public final Logger logger = LoggerFactory.getLogger(LocalInvocationHandler.class);
        private final String pRemoteName;
        private final long timeout;
        private final boolean includePremoteName;

        public LocalInvocationHandler(final String pRemoteName, final long timeout) {
            Preconditions.checkNotNull(pRemoteName);
            this.pRemoteName = pRemoteName;
            this.timeout = timeout;
            this.includePremoteName = pRemoteName == null || pRemoteName.length() == 0;
        }

        @Override
        public final Object invoke(final Object pProxy, final Method pMethod, final Object[] pArgs) throws Throwable {
            if (logger.isTraceEnabled()) {
                logger.trace("Trying to invoke pMethod:" + pMethod + " args" + (pArgs == null ? ": " + null : "size: " + pArgs.length));
            }
            try {
                if (isObjectMethodLocal() && pMethod.getDeclaringClass().equals(Object.class)) {
                    try {
                        return pMethod.invoke(pProxy, pArgs);
                    } catch (final IllegalAccessException illegalAccessException) {
                        if (logger.isErrorEnabled()) {
                            logger.error("pMethod:" + pMethod + " " + ExceptionUtils.getStackTrace(illegalAccessException));
                        }
                        throw new RuntimeException(illegalAccessException);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    if (logger.isTraceEnabled()) {
                        logger.trace("pMethod:" + pMethod + " Non directly invoked");
                    }
                }
                final String methodName;
                if (this.includePremoteName) {
                    methodName = pMethod.getName();
                } else {
                    methodName = pRemoteName + "." + pMethod.getName();
                }
                Object result;
                try {
                    final TimingOutCallback callback = new TimingOutCallback(timeout);
                    client.executeAsync(methodName, pArgs, callback);
                    result = callback.waitForResponse();
                } catch (final TimingOutCallback.TimeoutException timeoutException ) {
                    LOGGER.error("pRemoteName:" + pRemoteName + " " + ExceptionUtils.getStackTrace(timeoutException));
                    throw new XmlRpcTimeoutException(timeoutException);
                } catch (final InterruptedException interruptedException) {
                    LOGGER.error("pRemoteName:" + pRemoteName + " " + ExceptionUtils.getStackTrace(interruptedException));
                    throw new XmlRpcTimeoutException(interruptedException);
                } catch (final UndeclaredThrowableException undeclaredThrowableException) {
                    LOGGER.error("pRemoteName:" + pRemoteName + " " + ExceptionUtils.getStackTrace(undeclaredThrowableException));
                    throw new RuntimeException(undeclaredThrowableException);
                } catch (final XmlRpcException xmlRpcException) {
                    LOGGER.error("pRemoteName:" + pRemoteName + " " + ExceptionUtils.getStackTrace(xmlRpcException));
                    final Throwable linkedException = xmlRpcException.linkedException;
                    if (linkedException == null) {
                        throw new RuntimeException(xmlRpcException);
                    }
                    final Class<?>[] exceptionTypes = pMethod.getExceptionTypes();
                    for (int i = 0; i < exceptionTypes.length; i++) {
                        final Class<?> c = exceptionTypes[i];
                        if (c.isAssignableFrom(linkedException.getClass())) {
                            LOGGER.error("pRemoteName:" + pRemoteName + " " + ExceptionUtils.getStackTrace(xmlRpcException));
                            throw linkedException;
                        }
                    }
                    throw new RuntimeException(linkedException);
                } catch (Throwable e) {
                    LOGGER.error("pRemoteName:" + pRemoteName + " " + ExceptionUtils.getStackTrace(e));
                    throw e;
                }
                final TypeConverter typeConverter = typeConverterFactory.getTypeConverter(pMethod.getReturnType());
                return typeConverter.convert(result);
            } catch (final Exception exception) {
                LOGGER.error(ExceptionUtils.getStackTrace(exception));
                throw exception;
            }
        }
    }
}
