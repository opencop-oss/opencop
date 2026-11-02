// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.db.watchbox;

import com.opencop.processor.business.model.watchbox.AcceptedDetection;
import com.opencop.processor.business.model.watchbox.WatchboxAlertOutboxRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The durable handoff between committed detections and Redis publication work (V41's
 * watchbox_alert_outbox). Insert runs inside the detection transaction; claim applies the current
 * parent-generation fence and delete runs inside the drainer's one transaction. A crash between
 * Redis publication and commit leaves current claimed rows for retry and can duplicate that Redis
 * publication; the stable {@code eventId} lets consumers deduplicate it.
 *
 * <p>The outbox does not make browser receipt durable or at-least-once: Redis Pub/Sub, the
 * WebSocket fan-out, and browsers retain no alert backlog and provide no acknowledgement or replay.
 * Live browser delivery is therefore best-effort.
 */
public interface WatchboxAlertOutboxRepository {

  /**
   * Inserts delivery work for detections the database accepted. Must run in the same transaction as
   * the detection insert, and must be called only with that insert's returned (accepted) rows.
   * Their storage ids and display names are stored alongside the identity.
   */
  void insertAll(List<AcceptedDetection> acceptedDetections);

  /**
   * Claims up to {@code batchSize} current-generation rows in id order, FOR UPDATE SKIP LOCKED.
   * Superseded candidate rows are retired inside the same transaction and never returned.
   */
  List<WatchboxAlertOutboxRow> claimBatch(int batchSize);

  /**
   * Deletes claimed rows at the end of a drain pass — published ones (delete-after-publish is the
   * outbox's retention boundary) and rows expired past the delivery window (deleted unpublished,
   * counted by the drainer).
   */
  void deleteAll(List<Long> outboxIds);

  /** Insertion time of the oldest pending row, empty when the outbox is drained. */
  Optional<Instant> oldestCreatedAt();
}
