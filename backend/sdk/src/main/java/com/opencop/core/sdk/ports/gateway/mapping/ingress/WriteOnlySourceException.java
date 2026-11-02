// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.mapping.ingress;

import java.util.UUID;

/**
 * The source only writes, so it has no ingress mapper and nothing it receives is decoded. Unlike a
 * parsing failure, the payload is not faulty: a caller drops it instead of treating it as a
 * failure.
 */
public class WriteOnlySourceException extends RuntimeException {

  public WriteOnlySourceException(UUID sourceId) {
    super("Source " + sourceId + " only writes; its inbound payload is not decoded");
  }
}
