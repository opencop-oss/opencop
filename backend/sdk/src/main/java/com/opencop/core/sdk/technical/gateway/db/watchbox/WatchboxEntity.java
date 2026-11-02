// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.watchbox;

import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "watchbox", schema = "watchbox")
public class WatchboxEntity {

  @Id
  @GeneratedValue
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(length = 4000)
  private String description;

  @Column(nullable = false)
  private boolean enabled = true;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private WatchboxDirection direction = WatchboxDirection.BOTH;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "polygon_json", columnDefinition = "jsonb", nullable = false)
  private String polygonJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "filter_json", columnDefinition = "jsonb", nullable = false)
  private String filterJson;

  @Column(name = "config_epoch", nullable = false)
  private long configEpoch = 1L;

  @Column(name = "created_by")
  private UUID createdBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "archived_by")
  private UUID archivedBy;

  @Column(name = "archived_at")
  private Instant archivedAt;

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

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public WatchboxDirection getDirection() {
    return direction;
  }

  public void setDirection(WatchboxDirection direction) {
    this.direction = direction;
  }

  public String getPolygonJson() {
    return polygonJson;
  }

  public void setPolygonJson(String polygonJson) {
    this.polygonJson = polygonJson;
  }

  public String getFilterJson() {
    return filterJson;
  }

  public void setFilterJson(String filterJson) {
    this.filterJson = filterJson;
  }

  public long getConfigEpoch() {
    return configEpoch;
  }

  public void setConfigEpoch(long configEpoch) {
    this.configEpoch = configEpoch;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(UUID createdBy) {
    this.createdBy = createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public UUID getArchivedBy() {
    return archivedBy;
  }

  public void setArchivedBy(UUID archivedBy) {
    this.archivedBy = archivedBy;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(Instant archivedAt) {
    this.archivedAt = archivedAt;
  }
}
