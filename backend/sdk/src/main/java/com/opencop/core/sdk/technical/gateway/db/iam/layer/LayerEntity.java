// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layer;

import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import com.opencop.core.sdk.technical.gateway.db.iam.member.LayerMemberEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.CurrentTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.SourceType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "layer", schema = "iam")
public class LayerEntity {

  @Id
  @GeneratedValue
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(nullable = false)
  private String name;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "created_by", columnDefinition = "uuid")
  private UUID createdBy;

  /**
   * Null until the row is first updated: nothing backfills it, and a create must not report the
   * layer as already edited.
   *
   * <p>{@code @CurrentTimestamp} scoped to {@code UPDATE} rather than {@code @UpdateTimestamp},
   * whose generator fires on {@code INSERT_AND_UPDATE} — that would stamp a timestamp at creation
   * while {@code updated_by} stayed null, pairing an edit time with no editor.
   */
  @CurrentTimestamp(event = EventType.UPDATE, source = SourceType.VM)
  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "updated_by", columnDefinition = "uuid")
  private UUID updatedBy;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Column(name = "archived_by", columnDefinition = "uuid")
  private UUID archivedBy;

  @Column(name = "default_visible", nullable = false)
  private boolean defaultVisible = true;

  @Enumerated(EnumType.STRING)
  @Column(name = "access_scope", nullable = false, columnDefinition = "iam.layer_access_scope")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private LayerAccessScope accessScope = LayerAccessScope.PRIVATE;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "lifecycle_state",
      nullable = false,
      columnDefinition = "iam.layer_lifecycle_state")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private LayerLifecycleState lifecycleState = LayerLifecycleState.ACTIVE;

  @OneToMany(mappedBy = "layer", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<LayerMemberEntity> members = new LinkedHashSet<>();

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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(UUID createdBy) {
    this.createdBy = createdBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(UUID updatedBy) {
    this.updatedBy = updatedBy;
  }

  public LayerAccessScope getAccessScope() {
    return accessScope;
  }

  public void setAccessScope(LayerAccessScope accessScope) {
    this.accessScope = accessScope;
  }

  public LayerLifecycleState getLifecycleState() {
    return lifecycleState;
  }

  public void setLifecycleState(LayerLifecycleState lifecycleState) {
    this.lifecycleState = lifecycleState;
  }

  public Set<LayerMemberEntity> getMembers() {
    return members;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(Instant archivedAt) {
    this.archivedAt = archivedAt;
  }

  public UUID getArchivedBy() {
    return archivedBy;
  }

  public void setArchivedBy(UUID archivedBy) {
    this.archivedBy = archivedBy;
  }

  public boolean isDefaultVisible() {
    return defaultVisible;
  }

  public void setDefaultVisible(boolean defaultVisible) {
    this.defaultVisible = defaultVisible;
  }
}
