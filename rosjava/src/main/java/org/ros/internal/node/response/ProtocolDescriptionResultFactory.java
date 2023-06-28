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

package org.ros.internal.node.response;

import com.google.common.base.Preconditions;
import org.ros.address.AdvertiseAddress;
import org.ros.address.PublicAdvertiseAddressFactory;
import org.ros.internal.transport.ProtocolDescription;
import org.ros.internal.transport.ProtocolNames;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class ProtocolDescriptionResultFactory implements Function<Object,ProtocolDescription> {

    @Override
    public final ProtocolDescription apply(Object value) {
        return applyStatic(value);
    }

    public static final ProtocolDescription applyStatic(Object value) {
        final List<Object> protocolParameters = Arrays.asList((Object[]) value);
        Preconditions.checkState(protocolParameters.size() == 3);
        Preconditions.checkState(protocolParameters.get(0).equals(ProtocolNames.TCPROS));
        final String host = (String) protocolParameters.get(1);
        final PublicAdvertiseAddressFactory publicAdvertiseAddressFactory = new PublicAdvertiseAddressFactory(host);
        final AdvertiseAddress advertiseAddress = publicAdvertiseAddressFactory.newDefault();
        advertiseAddress.setStaticPort((Integer) protocolParameters.get(2));
        return ProtocolDescription.createTcpRosProtocolDescription(advertiseAddress);
    }
}
