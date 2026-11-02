// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.audit;

/**
 * Outbound port for authorization-denied audit reporting.
 *
 * <p>Contract for implementations and callers:
 *
 * <ul>
 *   <li>Implementations must never throw and must never alter the request outcome — {@code
 *       reportDenied} runs inside deny paths, after the decision and before the 403 is rendered.
 *   <li>Call on the request thread: the exactly-once guard marks the current request through
 *       request-scoped state, so off-thread reports may double-count against the backstop.
 *   <li>Event construction is clamping, not throwing; see {@link AuthzDenialEvent} for the
 *       NONE/UNKNOWN sentinel conventions.
 * </ul>
 */
public interface AuthzAuditReporting {
  void reportDenied(AuthzDenialEvent event);
}
