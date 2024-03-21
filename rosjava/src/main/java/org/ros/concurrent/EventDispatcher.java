/*
 * Copyright (C) 2012 Google Inc.
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

package org.ros.concurrent;


import java.util.function.Consumer;

/**
 * @author damonkohler@google.com (Damon Kohler)
 *
 * @param <T>
 *          the listener type
 */
public final class EventDispatcher<T> extends CancellableLoop {

  private final T listener;
  private final CircularBlockingDeque<Consumer<T>> events;

  public EventDispatcher(final T listener,final int queueCapacity) {
    this.listener = listener;
    this.events = new CircularBlockingDeque<>(queueCapacity);
  }

  public final void signal(final Consumer<T> signalConsumer) {
    this.events.addLast(signalConsumer);
  }

  @Override
  public final void loop() throws InterruptedException {
    final Consumer<T> consumer = this.events.takeFirst();
    consumer.accept(listener);
  }

  public final T getListener()
  {
    return listener;
  }
}