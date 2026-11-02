// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.connection;

public interface SocketReconciliationNudgePublisher {

  /**
   * Requests a best-effort Connect reconciliation wake-up. Production implementations should log
   * and swallow serialization or transport failures so notification failure does not roll back
   * persisted desired-state changes.
   */
  void requestReconciliation();
}
