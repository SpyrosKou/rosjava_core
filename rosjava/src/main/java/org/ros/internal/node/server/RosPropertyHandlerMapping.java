package org.ros.internal.node.server;

import org.apache.commons.lang3.StringUtils;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcHandler;
import org.apache.xmlrpc.server.PropertyHandlerMapping;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Extends {@link org.apache.xmlrpc.server.PropertyHandlerMapping} and provides custom naming for methods
 *
 * @author Spyros Koukas
 */
final class RosPropertyHandlerMapping extends PropertyHandlerMapping {
    @Override
    protected final void registerPublicMethods(
            final String pKey
            , final Class pType) throws XmlRpcException {
        final Map<String, Method[]> map = new HashMap();
        final Method[] methods = pType.getMethods();
        for (int i = 0; i < methods.length; i++) {
            final Method method = methods[i];
            if (!this.isHandlerMethod(method)) {
                continue;
            }
            final String name = (StringUtils.isNotBlank(pKey) ? pKey + "." : "") + method.getName();
            final Method[] mArray;
            final Method[] oldMArray = map.get(name);
            if (oldMArray == null) {
                mArray = new Method[]{method};
            } else {
                mArray = new Method[oldMArray.length + 1];
                System.arraycopy(oldMArray, 0, mArray, 0, oldMArray.length);
                mArray[oldMArray.length] = method;
            }
            map.put(name, mArray);
        }

        for (final Map.Entry<String, Method[]> entry : map.entrySet()) {
            final String name = entry.getKey();
            final Method[] mArray = entry.getValue();
            this.handlerMap.put(name, newXmlRpcHandler(pType, mArray));
        }
    }
}
