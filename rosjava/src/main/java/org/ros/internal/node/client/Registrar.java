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

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.concurrent.Holder;
import org.ros.concurrent.RetryingExecutorService;
import org.ros.internal.node.server.NodeIdentifier;
import org.ros.internal.node.server.SlaveServer;
import org.ros.internal.node.server.master.MasterServer;
import org.ros.internal.node.service.ServiceManagerListener;
import org.ros.internal.node.topic.DefaultPublisher;
import org.ros.internal.node.topic.DefaultSubscriber;
import org.ros.internal.node.topic.PublisherIdentifier;
import org.ros.internal.node.topic.TopicParticipantManagerListener;
import org.ros.node.service.ServiceServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages topic, and service registrations of a {@link SlaveServer} with the
 * {@link MasterServer}.
 *
 * @author kwc@willowgarage.com (Ken Conley)
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class Registrar implements TopicParticipantManagerListener, ServiceManagerListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


    private static final int SHUTDOWN_TIMEOUT = 5;
    private static final TimeUnit SHUTDOWN_TIMEOUT_UNITS = TimeUnit.SECONDS;

    private final MasterClient rosCoreClient;
    private final ScheduledExecutorService executorService;
    private final RetryingExecutorService retryingExecutorService;

    private NodeIdentifier nodeIdentifier;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * @param rosCoreClient   a {@link MasterClient} for communicating with the ROS master
     * @param executorService a {@link ScheduledExecutorService} to be used for all asynchronous
     *                        operations
     */
    public Registrar(final MasterClient rosCoreClient, final ScheduledExecutorService executorService) {
        this.rosCoreClient = rosCoreClient;
        this.executorService = executorService;
        this.retryingExecutorService = new RetryingExecutorService(executorService);
        this.nodeIdentifier = null;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("MasterXmlRpcEndpoint URI: " + this.rosCoreClient.getRemoteUri());
        }
    }

    /**
     * Failed registration actions are retried periodically until they succeed.
     * This method adjusts the delay between successive retry attempts for any
     * particular registration action.
     *
     * @param delay the delay in units of {@code unit} between retries
     * @param unit  the unit of {@code delay}
     */
    public void setRetryDelay(long delay, TimeUnit unit) {
        retryingExecutorService.setRetryDelay(delay, unit);
    }

    private final boolean submit(final Callable<Boolean> callable) {
        if (this.running.get()) {
            this.retryingExecutorService.submit(callable);
            return true;
        } else {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Registrar no longer running, request ignored.");
            }
            return false;
        }
    }

    private final <T> boolean callMaster(final Callable<Response<T>> callable) {
        Preconditions.checkNotNull(this.nodeIdentifier, "Registrar not started.");
        boolean success;
        try {
            final Response<T> response = callable.call();
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(this.nodeIdentifier+" got response:" + response);
            }
            success = response.isSuccess();
        } catch (final Exception exception) {
            if (LOGGER.isErrorEnabled()) {
                try {
                    final String remoteUri = this.rosCoreClient.getRemoteUri().toString();
                    LOGGER.error("Exception caught while communicating with roscore @" + remoteUri + " from: " + this.nodeIdentifier + ":" + ExceptionUtils.getStackTrace(exception));

                } catch (final Exception loggingException) {
                    LOGGER.error("Exception caught while communicating with roscore." + ExceptionUtils.getStackTrace(exception));
                }
            }
            success = false;
        }
        return success;
    }

    @Override
    public final void onPublisherAdded(final DefaultPublisher<?> publisher) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Registering publisher: " + publisher);
        }
        final boolean submitted = this.submit(() -> {
            final boolean success = this.callMaster(() -> this.rosCoreClient.registerPublisher(publisher.toDeclaration()));
            if (success) {
                publisher.signalOnMasterRegistrationSuccess();
            } else {
                publisher.signalOnMasterRegistrationFailure();
            }
            return !success;
        });
        if (!submitted) {
            this.executorService.execute(() -> publisher.signalOnMasterRegistrationFailure());
        }
    }

    @Override
    public final void onPublisherRemoved(final DefaultPublisher<?> publisher) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Unregistering publisher: " + publisher);
        }
        final boolean submitted = submit(() -> {
            final boolean success = this.callMaster(() -> this.rosCoreClient.unregisterPublisher(publisher.getIdentifier()));
            if (success) {
                publisher.signalOnMasterUnregistrationSuccess();
            } else {
                publisher.signalOnMasterUnregistrationFailure();
            }
            return !success;
        });
        if (!submitted) {
            executorService.execute(() -> publisher.signalOnMasterUnregistrationFailure());
        }
    }

    @Override
    public final void onSubscriberAdded(final DefaultSubscriber<?> subscriber) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Registering subscriber: " + subscriber);
        }
        final boolean submitted = submit(() -> {
            final Holder<Response<List<URI>>> holder = Holder.newEmpty();
            final boolean success = this.callMaster(() -> holder.set(rosCoreClient.registerSubscriber(nodeIdentifier, subscriber)));
            if (success) {
                final Set<PublisherIdentifier> publisherIdentifiers =
                        PublisherIdentifier.newCollectionFromUris(holder.get().getResult(), subscriber.getTopicDeclaration());
                subscriber.updatePublishers(publisherIdentifiers);
                subscriber.signalOnMasterRegistrationSuccess();
            } else {
                subscriber.signalOnMasterRegistrationFailure();
            }
            return !success;
        });
        if (!submitted) {
            executorService.execute(() -> subscriber.signalOnMasterRegistrationFailure());
        }
    }

    @Override
    public final void onSubscriberRemoved(final DefaultSubscriber<?> subscriber) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Unregistering subscriber: " + subscriber);
        }
        final boolean submitted = submit(() -> {
            final boolean success = this.callMaster(() -> rosCoreClient.unregisterSubscriber(nodeIdentifier, subscriber));
            if (success) {
                subscriber.signalOnMasterUnregistrationSuccess();
            } else {
                subscriber.signalOnMasterUnregistrationFailure();
            }
            return !success;
        });
        if (!submitted) {
            this.executorService.execute(() -> subscriber.signalOnMasterUnregistrationFailure());
        }
    }


    @Override
    public final void onServiceServerAdded(final ServiceServer<?, ?> serviceServer) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Registering service: " + serviceServer);
        }
        final boolean submitted = submit(() -> {
            final boolean success = this.callMaster(() -> rosCoreClient.registerService(nodeIdentifier, serviceServer));
            if (success) {
                serviceServer.onMasterRegistrationSuccess();
            } else {
                serviceServer.onMasterRegistrationFailure();
            }
            return !success;
        });
        if (!submitted) {
            this.executorService.execute(() -> serviceServer.onMasterRegistrationFailure());
        }
    }

    /**
     * Calls master and raises events.
     *
     * @param serviceServer
     * @return
     */
    private final boolean unregisterService(final ServiceServer<?, ?> serviceServer) {
        final boolean success = this.callMaster(() -> this.rosCoreClient.unregisterService(this.nodeIdentifier, serviceServer));
        if (success) {
            serviceServer.onMasterUnregistrationSuccess();
        } else {
            serviceServer.onMasterUnregistrationFailure();
        }
        return !success;
    }

    @Override
    public final void onServiceServerRemoved(final ServiceServer<?, ?> serviceServer) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Unregistering service: " + serviceServer);
        }
        final boolean submitted = this.submit(() -> unregisterService(serviceServer));
        if (!submitted) {
            this.executorService.execute(() -> serviceServer.onMasterUnregistrationFailure());
        }
    }

    /**
     * Starts the {@link Registrar} for the {@link SlaveServer} identified by the
     * given {@link NodeIdentifier}.
     *
     * @param nodeIdentifier the {@link NodeIdentifier} for the {@link SlaveServer} this
     *                       {@link Registrar} is responsible for
     */
    public final void start(NodeIdentifier nodeIdentifier) {
        Preconditions.checkNotNull(nodeIdentifier);
        Preconditions.checkState(this.nodeIdentifier == null, "Registrar already started.");
        this.nodeIdentifier = nodeIdentifier;
        running.set(true);
    }

    /**
     * Shuts down the {@link Registrar}.
     *
     * <p>
     * No further registration requests will be accepted. All queued registration
     * jobs have up to {@link #SHUTDOWN_TIMEOUT} {@link #SHUTDOWN_TIMEOUT_UNITS}
     * to complete before being canceled.
     *
     * <p>
     * Calling {@link #shutdown()} more than once has no effect.
     */
    public final void shutdown() {
        if (this.running.compareAndSet(true, false)) {
            try {
                this.retryingExecutorService.shutdown(SHUTDOWN_TIMEOUT, SHUTDOWN_TIMEOUT_UNITS);
            } catch (final InterruptedException interruptedException) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Exception while waiting for shutdown:" + ExceptionUtils.getStackTrace(interruptedException));
                }
            }
        }
    }
}
