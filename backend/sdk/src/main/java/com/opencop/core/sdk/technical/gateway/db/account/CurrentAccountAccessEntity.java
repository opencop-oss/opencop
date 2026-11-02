// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "opencop_user")
public class CurrentAccountAccessEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID internalUserId;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "administrator", nullable = false)
  private boolean administrator;

  @Column(name = "session_generation", nullable = false)
  private long sessionGeneration;

  @Column(name = "account_access_revision", nullable = false)
  private long accountRevision;

  protected CurrentAccountAccessEntity() {}

  public UUID getInternalUserId() {
    return internalUserId;
  }

  public boolean isActive() {
    return active;
  }

  public boolean isAdministrator() {
    return administrator;
  }

  public long getSessionGeneration() {
    return sessionGeneration;
  }

  public long getAccountRevision() {
    return accountRevision;
  }
}
