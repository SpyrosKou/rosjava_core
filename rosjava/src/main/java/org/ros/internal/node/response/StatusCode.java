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

public enum StatusCode {
    ERROR(-1, "Error"), FAILURE(0, "Failure"), SUCCESS(1, "Success");

    private final int intValue;
    private final String stringRepresentation;

    private StatusCode(final int value, final String stringRepresentation) {
        this.intValue = value;
        this.stringRepresentation = stringRepresentation;
    }

    public final int toInt() {
        return intValue;
    }

    public static final StatusCode fromInt(int intValue) {
        switch (intValue) {
            case -1:
                return ERROR;
            case 1:
                return SUCCESS;
            case 0:
            default:
                return FAILURE;
        }
    }

    @Override
    public final String toString() {
        return this.stringRepresentation;
    }
}
