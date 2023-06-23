package org.ros.node.topic;

import org.ros.node.ConnectedNode;


/**
 * Provides a way of specifying network transport hints to
 * {@link ConnectedNode#newSubscriber(org.ros.namespace.GraphName, String, TransportHints)} and
 * {@link ConnectedNode#newSubscriber(String, String, TransportHints)}.
 *
 * @author stefan.glaser@hs-offenburg.de (Stefan Glaser)
 */
public final class TransportHints {

  private final boolean tcpNoDelay;

  public TransportHints() {
    this(false);
  }

  public TransportHints(boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
  }


  public final boolean getTcpNoDelay() {
    return this.tcpNoDelay;
  }
}
