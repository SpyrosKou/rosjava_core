package org.ros.helpers;

import java.util.Map;

/**
 * Thin wrapper for the object returned by Yaml.load().
 * The object returned by Yaml.load() is a LinkedHashMap<String, Object>; this class is to
 * keep the code simple.
 */
final class LoadedResource {
    private Map<?, ?> resource;
    private String namespace;

    final Map<?, ?> getResource() {
        return resource;
    }

    final String getNamespace() {
        return namespace;
    }

    LoadedResource(final Map resource, final  String namespace) {
        this.resource = resource;
        this.namespace = namespace;
    }
}
