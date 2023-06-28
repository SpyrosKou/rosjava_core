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

import org.ros.exception.RosRuntimeException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Function;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
final class UriResultFactory implements Function<Object,URI> {

  @Override
  public final URI apply(Object value) {return applyStatic(value);}
  public static final URI applyStatic(Object value) {
    try {
      return new URI((String) value);
    } catch (URISyntaxException e) {
      throw new RosRuntimeException(e);
    }
  }
}
