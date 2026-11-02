// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.runtime;

import com.opencop.core.api.general.ports.technical.gateway.ResourceActivityMaintenance;
import java.time.Duration;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Age-based ledger pruning and orphaned-projection cleanup, matching the watchbox retention shape.
 */
public class PostgresResourceActivityMaintenance implements ResourceActivityMaintenance {

  private static final String DELETE_AGED_SQL =
      """
      DELETE FROM activity.recorded_activity
       WHERE effective_at < now() - (:retentionMillis * INTERVAL '1 millisecond')
      """;

  /**
   * Deletes projection rows for resources that no longer exist.
   *
   * <p>Each kind is checked against its own table because the key is polymorphic. A kind that is
   * somehow not one of the three is left alone rather than deleted: an unrecognized value is more
   * likely a newer writer than a resource that vanished, and deleting activity is not the safe
   * default.
   */
  private static final String DELETE_ORPHANS_SQL =
      """
      DELETE FROM activity.resource_activity ra
       WHERE (ra.resource_kind = 'LAYER'
              AND NOT EXISTS (SELECT 1 FROM iam.layer WHERE id = ra.resource_id))
          OR (ra.resource_kind = 'DATA_SOURCE'
              AND NOT EXISTS (SELECT 1 FROM profiles.data_source WHERE id = ra.resource_id))
          OR (ra.resource_kind = 'MAPPER'
              AND NOT EXISTS (SELECT 1 FROM profiles.data_mapper WHERE id = ra.resource_id))
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public PostgresResourceActivityMaintenance(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public int deleteActivityOlderThan(Duration retention) {
    return jdbc.update(DELETE_AGED_SQL, Map.of("retentionMillis", retention.toMillis()));
  }

  @Override
  public int deleteOrphanedProjections() {
    return jdbc.update(DELETE_ORPHANS_SQL, Map.of());
  }
}
