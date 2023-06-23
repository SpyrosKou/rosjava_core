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

package org.ros.concurrent;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link WatchdogTimer} expects to receive a {@link #pulse()} at least once
 * every {@link #period} {@link #unit}s. Once per every period in which a
 * {@link #pulse()} is not received, the provided {@link Runnable} will be
 * executed.
 *
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class WatchdogTimer {

    private final ScheduledExecutorService scheduledExecutorService;
    private final long period;
    private final TimeUnit unit;
    private final Runnable runnable;

    private final AtomicBoolean pulsed = new AtomicBoolean(false);
    private ScheduledFuture<?> scheduledFuture;

    public WatchdogTimer(ScheduledExecutorService scheduledExecutorService, long period,
                         TimeUnit unit, final Runnable runnable) {
        this.scheduledExecutorService = scheduledExecutorService;
        this.period = period;
        this.unit = unit;
        this.runnable = () -> {
            try {
                if (!pulsed.get()) {
                    runnable.run();
                }
            } finally {
                pulsed.set(false);
            }
        };
        pulsed.set(false);
    }

    /**
     * Starts the {@link WatchdogTimer}, if it is already started, it cancels and starts again the timer
     */
    public final void start() {
        if (this.scheduledFuture != null) {
            this.cancel();
        }
        this.scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(runnable, period, period, unit);
    }

    /**
     * A {@link WatchdogTimer} expects to receive a {@link #pulse()} at least once
     * * every {@link #period} {@link #unit}s. Once per every period in which a
     * * {@link #pulse()} is not received, the provided {@link Runnable} will be
     * * executed.
     */
    public void pulse() {
        this.pulsed.set(true);
    }

    /**
     *
     */
    public void cancel() {
        this.scheduledFuture.cancel(true);
        this.scheduledFuture=null;
    }
}
