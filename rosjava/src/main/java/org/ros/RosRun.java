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

package org.ros;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.ros.exception.RosRuntimeException;
import org.ros.internal.loader.CommandLineLoader;
import org.ros.node.DefaultNodeMainExecutor;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMain;
import org.ros.node.NodeMainExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * This is a main class entry point for executing {@link NodeMain}s.
 *
 * @author kwc@willowgarage.com (Ken Conley)
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class RosRun {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static void printUsage() {
        System.err.println("Usage: java -jar my_package.jar com.example.MyNodeMain [args]");
    }

    public static void main(String[] argv) {
        if (argv.length == 0) {
            printUsage();
            System.exit(1);
        }

        final CommandLineLoader loader = new CommandLineLoader(Lists.newArrayList(argv));
        final String nodeClassName = loader.getNodeClassName();
        System.out.println("Loading node class: " + loader.getNodeClassName());
        final NodeConfiguration nodeConfiguration = loader.build();

        NodeMain nodeMain = null;
        try {
            nodeMain = loader.loadClass(nodeClassName);
        } catch (ClassNotFoundException e) {
            final String msg = "Arguments:" + argv + " Unable to locate node: " + nodeClassName;
            LOGGER.error(msg);
            throw new RosRuntimeException(msg, e);
        } catch (InstantiationException e) {
            final String msg = "Arguments:" + argv + " Unable to instantiate node: " + nodeClassName;
            LOGGER.error(msg);
            throw new RosRuntimeException(msg, e);
        } catch (IllegalAccessException e) {
            final String msg = "Arguments:" + argv + " Unable to instantiate node: " + nodeClassName;
            LOGGER.error(msg);
            throw new RosRuntimeException(msg, e);
        }

        Preconditions.checkState(nodeMain != null, "nodeMain was null");
        final NodeMainExecutor nodeMainExecutor = DefaultNodeMainExecutor.newDefault();
        nodeMainExecutor.execute(nodeMain, nodeConfiguration);
    }
}
