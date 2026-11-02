// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Notes that a resource was used, without committing the caller to when or whether that reaches the
 * database.
 *
 * <p>Call sites sit on hot paths and treat recording as fire-and-forget: implementations must not
 * block on I/O, and must not throw for valid input. A null {@code resourceId} or {@code observedAt}
 * is a caller bug rather than a runtime condition, so implementations may throw {@link
 * NullPointerException} for those rather than silently discarding the observation.
 */
@FunctionalInterface
public interface LastEventAtRecorder {

  /** Discards observations. For tests, and for contexts with no persistence wired. */
  LastEventAtRecorder NO_OP = (_, _) -> {};

  void record(UUID resourceId, Instant observedAt);
}
