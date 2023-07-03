package org.ros.helpers;

import java.io.InputStream;

/**
 * Resource to load to Parameter Server, consisting of an InputStream and its corresponding namespace.
 */
public final class Resource {
    public final InputStream inputStream;
    public final String namespace;

    public Resource(final InputStream inputStream, final String namespace) {
        this.inputStream = inputStream;
        this.namespace = namespace;
    }
}
