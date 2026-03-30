package org.ros.node.topic;

import org.ros.node.ConnectedNode;


/**
 * Provides a way of specifying network transport hints to
 * {@link ConnectedNode#newSubscriber(org.ros.namespace.GraphName, String, TransportHints)} and
 * {@link ConnectedNode#newSubscriber(String, String, TransportHints)}.
 *
 * <p>
 * Transport hints are optional preferences. They do not guarantee that a remote
 * publisher or transport path will honor the requested behavior.
 *
 * <p>
 * The current public hint is {@code tcpNoDelay}, which requests low-latency TCP
 * behavior for subscriber connections.
 *
 * @author stefan.glaser@hs-offenburg.de (Stefan Glaser)
 */
public final class TransportHints {

  private final boolean tcpNoDelay;

  public TransportHints() {
    this(false);
  }

  /**
   * @param tcpNoDelay whether subscriber connections should request TCP_NODELAY
   */
  public TransportHints(boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
  }


  /**
   * @return {@code true} if subscriber connections should request TCP_NODELAY
   */
  public final boolean getTcpNoDelay() {
    return this.tcpNoDelay;
  }
}
