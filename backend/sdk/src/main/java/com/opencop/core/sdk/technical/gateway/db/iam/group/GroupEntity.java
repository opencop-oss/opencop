// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.group;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "user_group",
    schema = "iam",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_group_name", columnNames = "name"))
public class GroupEntity {

  @Id
  @GeneratedValue
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(nullable = false, unique = true)
  private String name;

  @Column private String description;

  @Column(name = "idp_issuer")
  private String idpIssuer;

  @Column(name = "idp_group_id")
  private String idpGroupId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<GroupMemberEntity> members = new LinkedHashSet<>();

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getIdpIssuer() {
    return idpIssuer;
  }

  public void setIdpIssuer(String idpIssuer) {
    this.idpIssuer = idpIssuer;
  }

  public String getIdpGroupId() {
    return idpGroupId;
  }

  public void setIdpGroupId(String idpGroupId) {
    this.idpGroupId = idpGroupId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Set<GroupMemberEntity> getMembers() {
    return members;
  }
}
