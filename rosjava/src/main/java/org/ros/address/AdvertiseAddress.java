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

package org.ros.address;

import com.google.common.base.Preconditions;

import com.google.common.base.Supplier;
import org.ros.exception.RosRuntimeException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Callable;

/**
 * A wrapper for {@link InetSocketAddress} that emphasizes the difference
 * between an address that should be used for binding a server port and one that
 * should be advertised to external entities.
 * 
 * An {@link AdvertiseAddress} encourages lazy lookups of port information to
 * prevent accidentally storing a bind port (e.g. 0 for OS picked) instead of
 * the advertised port.
 * 
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class AdvertiseAddress {

  private final String host;

  private Supplier<Integer> portSupplier;




  public AdvertiseAddress(String host) {
    Preconditions.checkNotNull(host);
    this.host = host;
  }

  public final String getHost() {
    return host;
  }

  public final void setStaticPort(final int port) {
    this.portSupplier = () -> port;
  }

  public final int getPort() {
    try {
      return portSupplier.get();
    } catch (Exception e) {
      throw new RosRuntimeException(e);
    }
  }

  public final void setPortSupplier(final Supplier<Integer> portSupplier) {
    this.portSupplier = portSupplier;
  }

  public final InetAddress toInetAddress() {
    return InetAddressFactory.newFromHostString(host);
  }

  public final InetSocketAddress toInetSocketAddress() {
    Preconditions.checkNotNull(portSupplier);
    try {
      InetAddress address = toInetAddress();
      return new InetSocketAddress(address, portSupplier.get());
    } catch (Exception e) {
      throw new RosRuntimeException(e);
    }
  }

  public final URI toUri(String scheme) {
    Preconditions.checkNotNull(portSupplier);
    try {
      return new URI(scheme, null, host, portSupplier.get(), "/", null, null);
    } catch (Exception e) {
      throw new RosRuntimeException("Failed to create URI: " + this, e);
    }
  }

  public final boolean isLoopbackAddress() {
    return toInetAddress().isLoopbackAddress();
  }

  @Override
  public final String toString() {
    Preconditions.checkNotNull(portSupplier);
    try {
      return "AdvertiseAddress<" + host + ", " + portSupplier.get() + ">";
    } catch (Exception e) {
      throw new RosRuntimeException(e);
    }
  }

  @Override
  public final int hashCode() {
    Preconditions.checkNotNull(portSupplier);
    final int prime = 31;
    int result = 1;
    result = prime * result + ((host == null) ? 0 : host.hashCode());
    try {
      result = prime * result + portSupplier.get();
    } catch (Exception e) {
      throw new RosRuntimeException(e);
    }
    return result;
  }

  @Override
  public final  boolean equals(Object obj) {
    Preconditions.checkNotNull(portSupplier);
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    AdvertiseAddress other = (AdvertiseAddress) obj;
    if (host == null) {
      if (other.host != null)
        return false;
    } else if (!host.equals(other.host))
      return false;
    try {
      if (portSupplier.get() != other.portSupplier.get())
        return false;
    } catch (Exception e) {
      throw new RosRuntimeException(e);
    }
    return true;
  }

}
