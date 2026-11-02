// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.db.watchbox;

import com.opencop.processor.business.model.watchbox.AcceptedDetection;
import com.opencop.processor.business.model.watchbox.WatchboxTransition;
import java.time.Instant;
import java.util.List;

public interface WatchboxDetectionRepository {

  /**
   * Inserts the emissions as detection rows and returns the subset the database actually accepted
   * (replay duplicates are suppressed by the unique detection identity and not returned). Accepted
   * rows are the only legitimate source of delivery work: candidates the insert deduplicated must
   * never be re-published. Each accepted row carries its storage id for the outbox.
   */
  List<AcceptedDetection> write(List<WatchboxTransition> emissions);

  int deleteExpiredBatch(Instant cutoff, int batchSize);
}
