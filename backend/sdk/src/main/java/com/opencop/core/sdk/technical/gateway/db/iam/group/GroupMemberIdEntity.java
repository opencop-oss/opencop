// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.group;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class GroupMemberIdEntity implements Serializable {

  @Column(name = "group_id", columnDefinition = "uuid", nullable = false)
  private UUID groupId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  public GroupMemberIdEntity() {}

  public GroupMemberIdEntity(UUID groupId, UUID userId) {
    this.groupId = groupId;
    this.userId = userId;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public UUID getUserId() {
    return userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GroupMemberIdEntity that)) {
      return false;
    }
    return Objects.equals(groupId, that.groupId) && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, userId);
  }
}
