// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists a resource's {@code last_event_at}.
 *
 * <p>Implementations must be idempotent and monotonic: a write carrying an older instant than the
 * stored one must leave the row unchanged. Flushes may land out of order across instances, and
 * retries are expected.
 */
@FunctionalInterface
public interface LastEventAtWriter {

  void write(UUID resourceId, Instant observedAt);
}
