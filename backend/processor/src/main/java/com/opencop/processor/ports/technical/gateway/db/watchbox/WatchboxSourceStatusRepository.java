// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.db.watchbox;

import com.opencop.processor.business.model.watchbox.WatchboxScopeKey;
import java.time.Instant;

public interface WatchboxSourceStatusRepository {
  void upsertChurnTripped(WatchboxScopeKey scope, long observedCount, long cap);

  /**
   * Deletes at most {@code batchSize} rows stranded by an epoch bump: rows whose config epoch is
   * older than their watchbox's current epoch and whose {@code updated_at} predates {@code cutoff}.
   * Current-epoch rows are never deleted regardless of age — the age guard is purely the forensic
   * window on superseded generations. Returns rows deleted.
   */
  int deleteSupersededBatch(Instant cutoff, int batchSize);
}
