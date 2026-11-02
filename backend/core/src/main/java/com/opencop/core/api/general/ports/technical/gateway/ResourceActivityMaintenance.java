// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.technical.gateway;

import java.time.Duration;

/** Prunes the activity ledger and the projection rows left behind by deleted resources. */
public interface ResourceActivityMaintenance {

  /**
   * Deletes ledger rows whose effective time is older than {@code retention}.
   *
   * <p>The retention floor is the supported replay horizon, not a disk-space preference: prune
   * inside it and a replayed batch no longer conflicts with a row it should have. Kafka bounds that
   * horizon, so the default is derived from topic retention rather than chosen.
   *
   * @return rows deleted
   */
  int deleteActivityOlderThan(Duration retention);

  /**
   * Deletes projection rows whose resource no longer exists.
   *
   * <p>{@code resource_id} is polymorphic across three tables, so it carries no foreign key and
   * nothing cascades when a layer, data source or mapper is deleted. Those rows are invisible to
   * every read — each join is driven from the resource rows — so this is bloat rather than
   * incorrectness, and sweeping it here keeps the cleanup in one place instead of in three delete
   * paths that would drift.
   *
   * @return rows deleted
   */
  int deleteOrphanedProjections();
}
