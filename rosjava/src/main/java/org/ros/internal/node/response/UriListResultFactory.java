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

package org.ros.internal.node.response;

import com.google.common.collect.Lists;
import org.ros.exception.RosRuntimeException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * A {@link Function<Object,List<URI>>} to take an object and turn it into a list of URLIs.
 * 
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class UriListResultFactory implements Function<Object,List<URI>> {

	@Override
	public final List<URI> apply(Object value) { return applyStatic(value);}
	public static final List<URI> applyStatic(Object value) {
		final List<Object> values = Arrays.asList((Object[]) value);
		final List<URI> uris = Lists.newArrayList();
		for (Object uri : values) {
			try {
				uris.add(new URI((String) uri));
			} catch (URISyntaxException e) {
				throw new RosRuntimeException(e);
			}
		}
		return uris;
	}
}
