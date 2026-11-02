// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.scim.relationship;

import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "opencop_user_groups",
    indexes = {
      @Index(name = "idx_opencop_user_groups_user_id", columnList = "user_id"),
      @Index(name = "idx_opencop_user_groups_group_id", columnList = "group_id")
    })
@IdClass(InternalUserGroupRelationshipId.class)
public class InternalUserGroupRelationshipEntity {

  @Id
  @ManyToOne
  @JoinColumn(name = "user_id", nullable = false)
  private InternalUserEntity user;

  @Id
  @ManyToOne
  @JoinColumn(name = "group_id", nullable = false)
  private InternalGroupEntity group;

  @Column(name = "assigned_at", nullable = false)
  private OffsetDateTime assignedAt;

  @Column(name = "user_id", insertable = false, updatable = false)
  private UUID userId;

  @Column(name = "group_id", insertable = false, updatable = false)
  private UUID groupId;

  public InternalUserGroupRelationshipEntity() {
    this.assignedAt = OffsetDateTime.now();
  }

  public InternalUserEntity getUser() {
    return user;
  }

  public void setUser(InternalUserEntity user) {
    this.user = user;
  }

  public InternalGroupEntity getGroup() {
    return group;
  }

  public void setGroup(InternalGroupEntity group) {
    this.group = group;
  }

  public OffsetDateTime getAssignedAt() {
    return assignedAt;
  }

  public void setAssignedAt(OffsetDateTime assignedAt) {
    this.assignedAt = assignedAt;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public void setGroupId(UUID groupId) {
    this.groupId = groupId;
  }
}
