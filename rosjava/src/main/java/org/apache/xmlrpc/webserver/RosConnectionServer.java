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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

/**
 * @author Spyros Koukas
 */
public final class RosConnectionServer extends ConnectionServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String METHOD_NAME = "methodName";
    private static final String PARAMS = "params";
    private static final String SYSTEM_MULTICALL = "system.multicall";

    /**
     * Returns, whether the
     * /** Processes a "connection". The "connection" is an opaque object, which is
     * being handled by the subclasses.
     *
     * @param xmlRpcStreamRequestConfig     The request configuration.
     * @param serverStreamConnection The "connection" being processed.
     * @throws XmlRpcException Processing the request failed.
     */
    public final void execute(
            final XmlRpcStreamRequestConfig xmlRpcStreamRequestConfig
            , final ServerStreamConnection serverStreamConnection)
            throws XmlRpcException {
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Execution started.");
            }
            Object result = null;
            Throwable error = null;

            try (final InputStream inputStream = this.getInputStream(xmlRpcStreamRequestConfig, serverStreamConnection)) {
                final XmlRpcRequest request = this.getRequest(xmlRpcStreamRequestConfig, inputStream);
                if (SYSTEM_MULTICALL.equals(request.getMethodName())) {
                    result = this.executeMulticall(request);
                } else {
                    result = this.execute(request);
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Execute: Request performed successfully");
                }
            } catch (final Throwable throwable) {
                this.logError(throwable);
                error = throwable;
            }
            final boolean contentLengthRequired = this.isContentLengthRequired(xmlRpcStreamRequestConfig);
            try (final OutputStream intermediateOutputStream = this.createIntermediateOutputStream(contentLengthRequired, serverStreamConnection);
                 final OutputStream outputStream = this.getOutputStream(serverStreamConnection, xmlRpcStreamRequestConfig, intermediateOutputStream);) {
                if (error == null) {
                    this.writeResponse(xmlRpcStreamRequestConfig, outputStream, result);
                } else {
                    this.writeError(xmlRpcStreamRequestConfig, outputStream, error);
                }
                if (contentLengthRequired) {
                    final ByteArrayOutputStream byteArrayOutputStream = (ByteArrayOutputStream) intermediateOutputStream;
                    try (final OutputStream destinationOutputStream = super.getOutputStream(xmlRpcStreamRequestConfig, serverStreamConnection, byteArrayOutputStream.size())) {
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
                serverStreamConnection.close();
            } catch (final Throwable throwable) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Ignorable Throwable while trying to close connection:" + ExceptionUtils.getStackTrace(throwable));
                }
            }

        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Execution ended.");
        }
    }

    /**
     * @param contentLengthRequired
     * @param serverStreamConnection A non closed connection, this method does not close it
     * @return
     */
    private final OutputStream createIntermediateOutputStream(
            final boolean contentLengthRequired
            , final ServerStreamConnection serverStreamConnection) throws IOException {
        final OutputStream result;
        if (contentLengthRequired) {
            result = new ByteArrayOutputStream();
        } else {
            result = serverStreamConnection.newOutputStream();
        }
        return result;

    }

    private final Object[] executeMulticall(final XmlRpcRequest pRequest) {
        if (pRequest.getParameterCount() != 1) {
            return null;
        }


        final Object[] reqs = (Object[]) pRequest.getParameter(0); // call requests
        final Object[] results = new Object[reqs.length]; // call results
        final XmlRpcRequestConfig pConfig = pRequest.getConfig();
        // TODO: make concurrent calls?
        for (int i = 0; i < reqs.length; i++) {

            try {
                @SuppressWarnings("unchecked") final HashMap<String, Object> req = (HashMap<String, Object>) reqs[i];
                final String methodName = (String) req.get(METHOD_NAME);
                final Object[] params = (Object[]) req.get(PARAMS);
                final RosConnectionXmlRpcRequest rosConnectionXmlRpcRequest = new RosConnectionXmlRpcRequest(methodName, pConfig, params);
                results[i] = this.execute(rosConnectionXmlRpcRequest);

            } catch (final Throwable throwable) {
                this.logError(throwable);
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("Ignorable:" + ExceptionUtils.getStackTrace(throwable));
                }
                // TODO: should this return an XmlRpc fault?
                results[i] = null;
            }

        }
        return results;
    }

    private static final class RosConnectionXmlRpcRequest implements XmlRpcRequest {
        private final String methodName;
        private final XmlRpcRequestConfig pConfig;



        private final Object[] params;

        RosConnectionXmlRpcRequest(final String methodName, final XmlRpcRequestConfig pConfig, final Object[] params) {
            this.methodName = methodName;
            this.pConfig = pConfig;
            this.params = params;
        }

        @Override
        public final XmlRpcRequestConfig getConfig() {
            return this.pConfig;
        }

        @Override
        public final String getMethodName() {
            return this.methodName;
        }

        @Override
        public final int getParameterCount() {
            return this.params == null ? 0 : this.params.length;
        }

        @Override
        public final Object getParameter(final int parameterIndex) {
            return this.params[parameterIndex];
        }

        @Override
        public final boolean equals(Object object) {
            if (this == object) return true;
            if (object == null || getClass() != object.getClass()) return false;
            final RosConnectionXmlRpcRequest that = (RosConnectionXmlRpcRequest) object;
            return Objects.equals(methodName, that.methodName) && Objects.equals(pConfig, that.pConfig) && Arrays.equals(params, that.params);
        }

        @Override
        public final int hashCode() {
            int result = Objects.hash(methodName, pConfig);
            result = 31 * result + Arrays.hashCode(params);
            return result;
        }

        @Override
        public final String toString() {
            return "RosConnectionXmlRpcRequest{" +
                    "methodName='" + methodName + '\'' +
                    ", pConfig=" + pConfig +
                    ", params=" + Arrays.toString(params) +
                    '}';
        }
    }
}
