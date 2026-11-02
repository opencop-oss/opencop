// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.runtimefeature;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RuntimeFeatureAudienceUserGroupJpaRepository
    extends JpaRepository<
        RuntimeFeatureAudienceUserGroupEntity, RuntimeFeatureAudienceUserGroupIdEntity> {

  List<RuntimeFeatureAudienceUserGroupEntity> findAllByIdFeatureName(String featureName);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "delete from RuntimeFeatureAudienceUserGroupEntity target"
          + " where target.id.featureName = :featureName")
  int deleteAllByIdFeatureName(@Param("featureName") String featureName);
}
