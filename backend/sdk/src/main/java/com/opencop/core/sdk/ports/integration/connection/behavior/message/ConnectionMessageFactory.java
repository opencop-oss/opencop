// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.behavior.message;

import java.time.Instant;
import java.util.UUID;

/**
 * Creates fresh integration-control bytes for one behavior attempt.
 *
 * <p>Implementations are immutable, stateless, and safe for concurrent calls. They must derive each
 * payload from the supplied source identity and execution time rather than cache time-sensitive
 * bytes. Expected construction or serialization failures may be reported as {@link Exception}; the
 * connection runtime logs them without payload data and keeps the connection alive.
 */
@FunctionalInterface
public interface ConnectionMessageFactory {

  /** Creates a new payload array for the supplied source and execution instant. */
  @SuppressWarnings("java:S112") // Plugin libraries expose different checked serialization errors.
  byte[] create(UUID sourceId, Instant executionTime) throws Exception;
}
