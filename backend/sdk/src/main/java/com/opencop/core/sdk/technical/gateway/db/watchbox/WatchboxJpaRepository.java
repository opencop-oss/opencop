// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.watchbox;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface WatchboxJpaRepository extends JpaRepository<WatchboxEntity, UUID> {

  Optional<WatchboxEntity> findByIdAndArchivedAtIsNull(UUID id);

  /**
   * Locks the active row until the caller's transaction ends ({@code SELECT ... FOR UPDATE}), so a
   * read-decide-update sequence executes against the committed row. The patch path allocates the
   * next config epoch from this read; the V37 guard only rejects epoch decreases, so unlocked
   * concurrent resets could both commit the same generation.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<WatchboxEntity> findWithLockByIdAndArchivedAtIsNull(UUID id);

  List<WatchboxEntity> findAllByArchivedAtIsNullOrderByCreatedAtDesc();

  // JPA won't work here: Spring Data has no updateBy... derivation, and loading/saving each
  // Watchbox would turn this atomic statement into a racy read-modify-write. The materialized CTE
  // drives active-parent lock acquisition in the same UUID order as Processor's generation fence,
  // preventing an opposite-order multi-parent deadlock. Native SQL is also required for PostgreSQL
  // clock_timestamp(): JPQL
  // CURRENT_TIMESTAMP is transaction-start time and can move updated_at backwards while waiting
  // behind a later-started patch. GREATEST also protects against VM/database clock skew.
  @Modifying
  @Query(
      value =
          """
          WITH locked_watchboxes AS MATERIALIZED (
            SELECT id
            FROM watchbox.watchbox
            WHERE archived_at IS NULL
            ORDER BY id
            FOR NO KEY UPDATE
          )
          UPDATE watchbox.watchbox AS target
          SET config_epoch = target.config_epoch + 1,
              updated_at = GREATEST(target.updated_at, clock_timestamp())
          FROM locked_watchboxes AS locked
          WHERE target.id = locked.id
          """,
      nativeQuery = true)
  int bumpAllActiveConfigEpochs();
}
