// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import com.opencop.processor.business.model.watchbox.WatchboxScopeKey;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxSourceStatusRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

public class PostgresWatchboxSourceStatusRepository implements WatchboxSourceStatusRepository {

  // JPA won't work here: INSERT ... ON CONFLICT upsert keyed on (watchbox, source, epoch)
  // has no JPA equivalent.
  private static final String UPSERT_TRIPPED_SQL =
      """
      INSERT INTO watchbox.watchbox_source_status
        (watchbox_id, config_epoch, source_id, status, reason_code, since_at,
         observed_count, cap, updated_at)
      VALUES (?, ?, ?, 'DEGRADED', 'WATCHBOX_OBJECT_ID_CHURN_DETECTED', now(), ?, ?, now())
      ON CONFLICT (watchbox_id, config_epoch, source_id) DO UPDATE SET
        status = EXCLUDED.status,
        reason_code = EXCLUDED.reason_code,
        observed_count = EXCLUDED.observed_count,
        cap = EXCLUDED.cap,
        updated_at = now()
      """;

  // JPA won't work here: a bounded-batch DELETE needs the ORDER BY/LIMIT subselect plus the
  // outer recheck below.
  // Epoch guard: only rows whose config_epoch is behind their watchbox's current epoch are
  // candidates. Epochs only advance (the sole writer is WatchboxJpaRepository's
  // config_epoch + 1 update, linearized by the generation fence's parent locks), so the guard
  // can never flip back to current under concurrency and needs no outer recheck; the FK to
  // watchbox.watchbox is ON DELETE CASCADE, so every surviving status row has a parent to join.
  // The outer updated_at guard re-runs during the delete's row recheck after a lock wait, so a
  // row refreshed by a concurrent upsert (an engine still writing on a just-superseded snapshot)
  // survives; the subquery alone would not re-evaluate and would delete the refreshed row.
  private static final String DELETE_SUPERSEDED_SQL =
      """
      DELETE FROM watchbox.watchbox_source_status
      WHERE (watchbox_id, config_epoch, source_id) IN (
        SELECT s.watchbox_id, s.config_epoch, s.source_id
        FROM watchbox.watchbox_source_status s
        JOIN watchbox.watchbox w ON w.id = s.watchbox_id
        WHERE s.config_epoch < w.config_epoch
          AND s.updated_at < ?
        ORDER BY s.updated_at, s.watchbox_id, s.config_epoch, s.source_id
        LIMIT ?)
        AND updated_at < ?
      """;

  private final JdbcTemplate jdbc;

  public PostgresWatchboxSourceStatusRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void upsertChurnTripped(WatchboxScopeKey scope, long observedCount, long cap) {
    jdbc.update(
        UPSERT_TRIPPED_SQL,
        scope.watchboxId(),
        scope.configEpoch(),
        scope.sourceId(),
        observedCount,
        cap);
  }

  @Override
  public int deleteSupersededBatch(Instant cutoff, int batchSize) {
    var cutoffTimestamp = Timestamp.from(cutoff);
    return jdbc.update(DELETE_SUPERSEDED_SQL, cutoffTimestamp, batchSize, cutoffTimestamp);
  }
}
