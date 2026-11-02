// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.runtimefeature;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface RuntimeFeatureJpaRepository extends JpaRepository<RuntimeFeatureEntity, String> {

  // Project only scalar feature fields and target IDs through one statement. A managed feature
  // entity already present in the persistence context could otherwise retain stale fields while
  // the query returns current target scalars.
  @Query(
      """
      select feature.name as featureName,
             feature.consumerClass as consumerClass,
             feature.audienceMode as audienceMode,
             feature.revision as revision,
             feature.updatedAt as updatedAt,
             target.id.userGroupId as userGroupId
      from RuntimeFeatureEntity feature
      left join RuntimeFeatureAudienceUserGroupEntity target
        on target.id.featureName = feature.name
      order by feature.name, target.id.userGroupId
      """)
  List<RuntimeFeatureAudienceRow> findAllAudienceRows();

  // Keep the named lookup atomic for the same reason as the collection query above.
  @Query(
      """
      select feature.name as featureName,
             feature.consumerClass as consumerClass,
             feature.audienceMode as audienceMode,
             feature.revision as revision,
             feature.updatedAt as updatedAt,
             target.id.userGroupId as userGroupId
      from RuntimeFeatureEntity feature
      left join RuntimeFeatureAudienceUserGroupEntity target
        on target.id.featureName = feature.name
      where feature.name = :name
      order by target.id.userGroupId
      """)
  List<RuntimeFeatureAudienceRow> findAudienceRowsByName(@Param("name") String name);

  // Lock timeout 0 renders FOR UPDATE NOWAIT: a held row fails immediately instead of queueing
  // callers on Core's small connection pool behind a stuck transaction.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
  @Query("select feature from RuntimeFeatureEntity feature where feature.name = :name")
  Optional<RuntimeFeatureEntity> findByNameForUpdate(@Param("name") String name);

  /**
   * Inserts a frontend-only definition only when the name is free, returning the number of rows
   * written (1 when this caller created it, 0 when it already existed).
   *
   * <p>{@code ON CONFLICT DO NOTHING} rather than an ordinary insert with a caught uniqueness
   * violation: a constraint error leaves the PostgreSQL transaction in a failed state and can mark
   * the Spring transaction rollback-only, so the loser of a race would surface a 5xx instead of the
   * intended duplicate outcome. Never {@code DO UPDATE}, even as a no-op — the literal column
   * values below are what make "create-missing-only" a property of the statement rather than a
   * convention the calling use case has to remember.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
          insert into config.runtime_feature
              (name, consumer_class, audience_mode, revision, created_at, updated_at)
          values (:name, 'FRONTEND_ONLY', 'NONE', 0, :now, :now)
          on conflict (name) do nothing
          """,
      nativeQuery = true)
  int insertFrontendOnlyIfAbsent(@Param("name") String name, @Param("now") Instant now);
}
