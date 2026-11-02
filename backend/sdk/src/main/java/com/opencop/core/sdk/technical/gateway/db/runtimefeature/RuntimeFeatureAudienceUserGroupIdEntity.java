// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.runtimefeature;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class RuntimeFeatureAudienceUserGroupIdEntity implements Serializable {

  @Column(name = "feature_name", nullable = false)
  private String featureName;

  @Column(name = "user_group_id", columnDefinition = "uuid", nullable = false)
  private UUID userGroupId;

  public RuntimeFeatureAudienceUserGroupIdEntity() {}

  public RuntimeFeatureAudienceUserGroupIdEntity(String featureName, UUID userGroupId) {
    this.featureName = featureName;
    this.userGroupId = userGroupId;
  }

  public String getFeatureName() {
    return featureName;
  }

  public UUID getUserGroupId() {
    return userGroupId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RuntimeFeatureAudienceUserGroupIdEntity that)) {
      return false;
    }
    return Objects.equals(featureName, that.featureName)
        && Objects.equals(userGroupId, that.userGroupId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(featureName, userGroupId);
  }
}
