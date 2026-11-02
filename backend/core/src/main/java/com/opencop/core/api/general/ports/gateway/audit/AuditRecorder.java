// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.audit;

import com.opencop.core.api.general.business.model.audit.AuditEvent;

/**
 * Writes one row for a successful user action.
 *
 * <p>Implementations join the caller's transaction and must refuse to run without one, so a change
 * and its history row commit or roll back together. A failed write therefore fails the user's
 * request: that is the design, and the reason no outbox or retry exists. The failure also marks the
 * caller's transaction rollback-only, so a caller must not catch and swallow it.
 *
 * <p>One exception: starting a socket data source records after the connect failover has already
 * committed the desired state in a transaction of its own, so a failed write there fails the
 * request after the source was enabled.
 *
 * <p>Call this only after the mutation's own lock or revision boundary, with the state that will
 * actually commit. Recording before that boundary can describe a transition that never happened.
 */
public interface AuditRecorder {

  void record(AuditEvent event);
}
