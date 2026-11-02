// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.watchbox;

import com.opencop.core.sdk.model.watchbox.WatchboxAlertingStatus;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusReason;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "watchbox_source_status", schema = "watchbox")
public class WatchboxSourceStatusEntity {

  @EmbeddedId private WatchboxSourceStatusIdEntity id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private WatchboxAlertingStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason_code", nullable = false)
  private WatchboxStatusReason reasonCode;

  @CreationTimestamp
  @Column(name = "since_at", nullable = false, updatable = false)
  private Instant sinceAt;

  @Column(name = "observed_count")
  private Long observedCount;

  private Long cap;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public WatchboxSourceStatusIdEntity getId() {
    return id;
  }

  public void setId(WatchboxSourceStatusIdEntity id) {
    this.id = id;
  }

  public WatchboxAlertingStatus getStatus() {
    return status;
  }

  public void setStatus(WatchboxAlertingStatus status) {
    this.status = status;
  }

  public WatchboxStatusReason getReasonCode() {
    return reasonCode;
  }

  public void setReasonCode(WatchboxStatusReason reasonCode) {
    this.reasonCode = reasonCode;
  }

  public Instant getSinceAt() {
    return sinceAt;
  }

  public Long getObservedCount() {
    return observedCount;
  }

  public void setObservedCount(Long observedCount) {
    this.observedCount = observedCount;
  }

  public Long getCap() {
    return cap;
  }

  public void setCap(Long cap) {
    this.cap = cap;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
