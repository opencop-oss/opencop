// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.group;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "user_group_member",
    schema = "iam",
    indexes = {@Index(name = "idx_user_group_member_user_id", columnList = "user_id")})
public class GroupMemberEntity {

  @EmbeddedId private GroupMemberIdEntity id;

  @MapsId("groupId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "group_id", nullable = false)
  private GroupEntity group;

  @CreationTimestamp
  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  public GroupMemberEntity() {}

  public GroupMemberEntity(GroupEntity group, UUID userId) {
    this.group = group;
    this.id = new GroupMemberIdEntity(group.getId(), userId);
  }

  public GroupMemberIdEntity getId() {
    return id;
  }

  public UUID getUserId() {
    return id != null ? id.getUserId() : null;
  }

  public GroupEntity getGroup() {
    return group;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public void setId(GroupMemberIdEntity id) {
    this.id = id;
  }

  public void setGroup(GroupEntity group) {
    this.group = group;
  }
}
