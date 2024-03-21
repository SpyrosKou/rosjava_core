package org.ros.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Allows the use names to identify threads
 * @author Spyros Koukas
 */
final class NamedThreadFactory implements ThreadFactory {
    private static final AtomicInteger poolNumber = new AtomicInteger(1);
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final ThreadGroup threadGroup;
    private final String threadNamePrefix;
    public final String getThreadNamePrefix(){
        return this.threadNamePrefix;
    }
    NamedThreadFactory(final String givenNamePrefix) {
        final SecurityManager securityManager = System.getSecurityManager();
        this.threadGroup = (securityManager != null) ? securityManager.getThreadGroup() :
                Thread.currentThread().getThreadGroup();

        this.threadNamePrefix = givenNamePrefix + "-pool-" +
                poolNumber.getAndIncrement() +
                "-thread-";
    }

    @Override
    public final Thread newThread(final Runnable runnable) {
        final Thread thread = new Thread(runnable,
                threadNamePrefix + threadNumber.getAndIncrement());
        if (thread.isDaemon())
            thread.setDaemon(false);
        if (thread.getPriority() != Thread.NORM_PRIORITY)
            thread.setPriority(Thread.NORM_PRIORITY);
        return thread;
    }
}
