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

package org.ros.master.uri;

import com.google.common.collect.Lists;

import org.ros.exception.RosRuntimeException;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A proxying {@link MasterUriProvider} which can be switched between providers.
 *
 * <p>
 * This class is thread-safe.
 *
 * @author Keith M. Hughes
 */
public final class SwitchableMasterUriProvider implements MasterUriProvider {

    private final Object mutex = new Object();
    ;

    /**
     * The current provider in use.
     */
    private MasterUriProvider provider;

    /**
     * The list of all pending requests.
     */
    private List<ProviderRequest> pendingRequests = Lists.newArrayList();

    /**
     * @param provider the initial provider to use
     */
    public SwitchableMasterUriProvider(MasterUriProvider provider) {
        this.provider = provider;

    }

    @Override
    public URI getMasterUri() throws RosRuntimeException {
        MasterUriProvider providerToUse = null;
        ProviderRequest requestToUse = null;

        synchronized (mutex) {
            if (this.provider != null) {
                providerToUse = this.provider;
            } else {
                requestToUse = new ProviderRequest();
                this.pendingRequests.add(requestToUse);
            }
        }

        if (providerToUse != null) {
            return providerToUse.getMasterUri();
        } else {
            return requestToUse.getMasterUri();
        }
    }



    /**
     * Switch between providers.
     *
     * @param switcher the new provider
     */
    public final void switchProvider(final MasterUriProviderSwitcher switcher) {
        synchronized (mutex) {
            final MasterUriProvider oldProvider = provider;
            this.provider = switcher.switchProvider(oldProvider);

            if (oldProvider == null) {
                for (final ProviderRequest request : this.pendingRequests) {
                    request.setProvider(this.provider);
                }
                this.pendingRequests.clear();
            }
        }
    }

    /**
     * Perform a switch between {@link MasterUriProvider} instances for the
     * {@link SwitchableMasterUriProvider}.
     *
     * <p>
     * This class permits the use of atomic provider switches.
     */
    public interface MasterUriProviderSwitcher {

        /**
         * Switch the provider in use.
         *
         * @param oldProvider a reference to the provider which came before
         * @return the new provider to use
         */
        MasterUriProvider switchProvider(MasterUriProvider oldProvider);
    }

    /**
     * A request for a URI which is blocked until it is available.
     */
    private static class ProviderRequest {

        /**
         * The latch used to wait.
         */
        private final CountDownLatch latch = new CountDownLatch(1);

        /**
         * The provider which will give the eventual answer.
         */
        private MasterUriProvider provider;

        /**
         * Get a service.
         *
         * <p>
         * This call can block indefinitely.
         *
         * @return the master {@link URI}
         */
        public final URI getMasterUri() {
            try {
                this.latch.await();
                return provider.getMasterUri();
            } catch (InterruptedException e) {
                throw new RosRuntimeException("URI provider interrupted", e);
            }
        }

        /**
         * Set the provider who will finally process the request.
         *
         * @param provider the {@link MasterUriProvider} to use
         */
        public final void setProvider(final MasterUriProvider provider) {
            this.provider = provider;
            this.latch.countDown();
        }
    }
}
