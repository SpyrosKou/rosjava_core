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

package org.ros.internal.node.parameter;

import org.ros.concurrent.ListenerGroup;
import org.ros.namespace.GraphName;
import org.ros.node.parameter.ParameterListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class ParameterManager {

    private final ExecutorService executorService;
    private final Map<GraphName, ListenerGroup<ParameterListener>> listeners = new ConcurrentHashMap<>();

    public ParameterManager(final ExecutorService executorService) {
        this.executorService = executorService;
    }

    public final void addListener(final GraphName parameterName, final ParameterListener listener) {
        final ListenerGroup<ParameterListener> listenerGroup = this.listeners.computeIfAbsent(parameterName, name -> new ListenerGroup<>(this.executorService));
        listenerGroup.add(listener);
    }

    /**
     * @param parameterName
     * @param parameterValue
     * @return the number of listeners called with the new value
     */
    public final int updateParameter(GraphName parameterName, final Object parameterValue) {
        final int numberOfListeners;
        final ListenerGroup<ParameterListener> listenerCollection = listeners.get(parameterName);
        if (listenerCollection != null) {
            numberOfListeners = listenerCollection.signal(listener -> listener.onNewValue(parameterValue));
        } else {
            numberOfListeners = 0;
        }

        return numberOfListeners;
    }
}
