// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.db.watchbox;

import com.opencop.processor.business.model.watchbox.WatchboxStateKey;
import com.opencop.processor.business.model.watchbox.WatchboxStateLockResult;
import com.opencop.processor.business.model.watchbox.WatchboxStateRow;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface WatchboxTransitionStateRepository {
  WatchboxStateLockResult lockCurrentAndSelectForUpdate(Collection<WatchboxStateKey> keys);

  void upsertAll(List<WatchboxStateRow> rows);

  Set<WatchboxStateKey> insertAbsent(List<WatchboxStateRow> rows);

  int deleteExpiredBatch(Instant cutoff, int batchSize);
}
