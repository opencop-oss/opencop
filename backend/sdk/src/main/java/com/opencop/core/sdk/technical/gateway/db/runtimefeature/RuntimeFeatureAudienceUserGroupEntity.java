// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.runtimefeature;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "runtime_feature_audience_user_group", schema = "config")
public class RuntimeFeatureAudienceUserGroupEntity {

  @EmbeddedId private RuntimeFeatureAudienceUserGroupIdEntity id;

  public RuntimeFeatureAudienceUserGroupEntity() {}

  public RuntimeFeatureAudienceUserGroupEntity(String featureName, UUID userGroupId) {
    this.id = new RuntimeFeatureAudienceUserGroupIdEntity(featureName, userGroupId);
  }

  public RuntimeFeatureAudienceUserGroupIdEntity getId() {
    return id;
  }
}
