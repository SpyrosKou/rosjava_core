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

package org.ros.internal.node.client;

import com.google.common.collect.Lists;
import org.ros.internal.node.topic.TopicDeclaration;
import org.ros.internal.node.xmlrpc.SlaveXmlRpcEndpoint;
import org.ros.internal.transport.ProtocolDescription;
import org.ros.namespace.GraphName;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class SlaveClient extends Client<SlaveXmlRpcEndpoint> {

    private final GraphName nodeName;

    public SlaveClient(final GraphName nodeName, final URI uri) {
        super(uri, SlaveXmlRpcEndpoint.class);
        this.nodeName = nodeName;
    }

    public List<Object> getBusStats() {
        throw new UnsupportedOperationException();
    }

    public List<Object> getBusInfo() {
        throw new UnsupportedOperationException();
    }

    public Response<URI> getMasterUri() {
        return Response.fromListChecked(xmlRpcEndpoint.getMasterUri(nodeName.toString()), UriResultFactory::applyStatic);
    }

    public Response<Void> shutdown(String message) {
        return Response.fromListChecked(xmlRpcEndpoint.shutdown("/master", message), VoidResultFactory::applyStatic);
    }

    public Response<Integer> getPid() {
        return Response.fromListChecked(xmlRpcEndpoint.getPid(nodeName.toString()), IntegerResultFactory::applyStatic);
    }

    public Response<List<TopicDeclaration>> getSubscriptions() {
        return Response.fromListChecked(xmlRpcEndpoint.getSubscriptions(nodeName.toString()),
                TopicListResultFactory::applyStatic);
    }

    public Response<List<TopicDeclaration>> getPublications() {
        return Response.fromListChecked(xmlRpcEndpoint.getPublications(nodeName.toString()),
                TopicListResultFactory::applyStatic);
    }

    public Response<Void> paramUpdate(GraphName name, boolean value) {
        return Response.fromListChecked(xmlRpcEndpoint.paramUpdate(nodeName.toString(), name.toString(), value),
                VoidResultFactory::applyStatic);
    }

    public Response<Void> paramUpdate(GraphName name, char value) {
        return Response.fromListChecked(xmlRpcEndpoint.paramUpdate(nodeName.toString(), name.toString(), value),
                VoidResultFactory::applyStatic);
    }

    public Response<Void> paramUpdate(GraphName name, int value) {
        return Response.fromListChecked(xmlRpcEndpoint.paramUpdate(nodeName.toString(), name.toString(), value),
                VoidResultFactory::applyStatic);
    }

    public Response<Void> paramUpdate(GraphName name, double value) {
        return Response.fromListChecked(xmlRpcEndpoint.paramUpdate(nodeName.toString(), name.toString(), value),
                VoidResultFactory::applyStatic);
    }

    public Response<Void> paramUpdate(GraphName name, String value) {
        return Response.fromListChecked(xmlRpcEndpoint.paramUpdate(nodeName.toString(), name.toString(), value),
                VoidResultFactory::applyStatic);
    }

    public final Response<Void> paramUpdate(GraphName name, List<?> value) {
        return Response.fromListChecked(xmlRpcEndpoint.paramUpdate(nodeName.toString(), name.toString(), value),
                VoidResultFactory::applyStatic);
    }

    public final Response<Void> paramUpdate(GraphName name, Map<?, ?> value) {
        return Response.fromListChecked(xmlRpcEndpoint.paramUpdate(nodeName.toString(), name.toString(), value),
                VoidResultFactory::applyStatic);
    }

    public final Response<Void> publisherUpdate(GraphName topic, Collection<URI> publisherUris) {
        final List<String> publishers = Lists.newArrayList();
        for (final URI uri : publisherUris) {
            publishers.add(uri.toString());
        }
        return Response.fromListChecked(
                this.xmlRpcEndpoint.publisherUpdate(this.nodeName.toString(), topic.toString(), publishers.toArray()),
                VoidResultFactory::applyStatic);
    }

    public final Response<ProtocolDescription> requestTopic(final GraphName topic,
                                                      final Set<String> requestedProtocols) {
        return Response.fromListChecked(this.xmlRpcEndpoint.requestTopic(this.nodeName.toString(), topic.toString(),
                new Object[][]{requestedProtocols.toArray()}), ProtocolDescriptionResultFactory::applyStatic);
    }
}
