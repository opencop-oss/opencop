// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

/**
 * Raised when a caller filters the catalog by a transport protocol this build does not define.
 *
 * <p>The message names every accepted value, because the empty success an unrecognised filter would
 * otherwise produce is indistinguishable from a protocol no integration happens to serve.
 */
public final class UnknownTransportProtocolException extends RuntimeException {

  public static final String CODE = "UNKNOWN_TRANSPORT_PROTOCOL";

  public UnknownTransportProtocolException(String requested, String acceptedValues) {
    super(
        "Unknown transport protocol '"
            + requested
            + "'. Accepted values are "
            + acceptedValues
            + ".");
  }
}
