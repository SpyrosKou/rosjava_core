package org.apache.xmlrpc.webserver;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcRequest;
import org.apache.xmlrpc.XmlRpcRequestConfig;
import org.apache.xmlrpc.common.ServerStreamConnection;
import org.apache.xmlrpc.common.XmlRpcStreamRequestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author Spyros Koukas
 */
public final class RosConnectionServer extends ConnectionServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * Returns, whether the
     * /** Processes a "connection". The "connection" is an opaque object, which is
     * being handled by the subclasses.
     *
     * @param pConfig     The request configuration.
     * @param pConnection The "connection" being processed.
     * @throws XmlRpcException Processing the request failed.
     */
    public final void execute(
            final XmlRpcStreamRequestConfig pConfig
            , final ServerStreamConnection pConnection)
            throws XmlRpcException {
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Starting...");
            }
            Object result = null;
            Throwable error = null;

            try (final InputStream inputStream = this.getInputStream(pConfig, pConnection)) {
                final XmlRpcRequest request = this.getRequest(pConfig, inputStream);
                if (request.getMethodName().equals("system.multicall")) {
                    result = this.executeMulticall(request);
                } else {
                    result = this.execute(request);
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("execute: Request performed successfully");
                }
            } catch (final Throwable throwable) {
                this.logError(throwable);
                error = throwable;
            }
            final boolean contentLengthRequired = this.isContentLengthRequired(pConfig);
            try (final OutputStream intermediateOutputStream = this.createIntermediateOutputStream(contentLengthRequired, pConnection);
                 final OutputStream outputStream = this.getOutputStream(pConnection, pConfig, intermediateOutputStream);) {
                if (error == null) {
                    this.writeResponse(pConfig, outputStream, result);
                } else {
                    this.writeError(pConfig, outputStream, error);
                }
                if (contentLengthRequired) {
                    final ByteArrayOutputStream byteArrayOutputStream = (ByteArrayOutputStream) intermediateOutputStream;
                    try (final OutputStream destinationOutputStream = super.getOutputStream(pConfig, pConnection, byteArrayOutputStream.size())) {
                        byteArrayOutputStream.writeTo(destinationOutputStream);
                    }
                }
            }


        } catch (final IOException ioException) {
            final XmlRpcException xmlRpcException = new XmlRpcException("I/O error while processing request: "
                    + ioException.getMessage(), ioException);
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("XmlRpcException from IOException:" + ExceptionUtils.getStackTrace(xmlRpcException));
            }
            throw xmlRpcException;
        } finally {

            try {
                pConnection.close();
            } catch (final Throwable throwable) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Ignorable Throwable while trying to close connection:" + ExceptionUtils.getStackTrace(throwable));
                }
            }

        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Ended.");
        }
    }

    /**
     * @param contentLengthRequired
     * @param pConnection           non closed connection, this method does not close it
     * @return
     */
    private final OutputStream createIntermediateOutputStream(
            final boolean contentLengthRequired
            , final ServerStreamConnection pConnection) throws IOException {
        final OutputStream result;
        if (contentLengthRequired) {
            result = new ByteArrayOutputStream();
        } else {
            result = pConnection.newOutputStream();
        }
        return result;
    }

    private final Object[] executeMulticall(final XmlRpcRequest pRequest) {
        if (pRequest.getParameterCount() != 1) {
            return null;
        }

        final Object[] reqs = (Object[]) pRequest.getParameter(0); // call requests
        final ArrayList<Object> results = new ArrayList<>(); // call results
        final XmlRpcRequestConfig pConfig = pRequest.getConfig();
        // TODO: make concurrent calls?
        for (int i = 0; i < reqs.length; i++) {
            Object result = null;
            try {
                @SuppressWarnings("unchecked") final HashMap<String, Object> req = (HashMap<String, Object>) reqs[i];
                final String methodName = (String) req.get("methodName");
                final Object[] params = (Object[]) req.get("params");
                result = this.execute(new XmlRpcRequest() {
                    @Override
                    public final XmlRpcRequestConfig getConfig() {
                        return pConfig;
                    }

                    @Override
                    public final String getMethodName() {
                        return methodName;
                    }

                    @Override
                    public final int getParameterCount() {
                        return params == null ? 0 : params.length;
                    }

                    @Override
                    public final Object getParameter(int pIndex) {
                        return params[pIndex];
                    }
                });
            } catch (final Throwable throwable) {
                this.logError(throwable);
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("Ignorable:" + ExceptionUtils.getStackTrace(throwable));
                }
                // TODO: should this return an XmlRpc fault?
                result = null;
            }
            results.add(result);
        }
        final Object[] retobj = new Object[]{results};
        return retobj;
    }
}
