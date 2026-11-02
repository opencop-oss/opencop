// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.iam.access;

import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.time.Instant;
import java.util.UUID;

public class LayerMember {

  private UUID layerId;
  private UUID userId;
  private LayerRole role;
  private Instant joinedAt;
  private String username;
  private String firstName;
  private String lastName;
  private String email;

  public LayerMember() {}

  private LayerMember(Builder builder) {
    this.layerId = builder.layerId;
    this.userId = builder.userId;
    this.role = builder.role;
    this.joinedAt = builder.joinedAt;
    this.username = builder.username;
    this.firstName = builder.firstName;
    this.lastName = builder.lastName;
    this.email = builder.email;
  }

  public UUID getLayerId() {
    return layerId;
  }

  public void setLayerId(UUID layerId) {
    this.layerId = layerId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public LayerRole getRole() {
    return role;
  }

  public void setRole(LayerRole role) {
    this.role = role;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public void setJoinedAt(Instant joinedAt) {
    this.joinedAt = joinedAt;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public static class Builder {
    private UUID layerId;
    private UUID userId;
    private LayerRole role;
    private Instant joinedAt;
    private String username;
    private String firstName;
    private String lastName;
    private String email;

    public Builder withLayerId(UUID layerId) {
      this.layerId = layerId;
      return this;
    }

    public Builder withUserId(UUID userId) {
      this.userId = userId;
      return this;
    }

    public Builder withRole(LayerRole role) {
      this.role = role;
      return this;
    }

    public Builder withJoinedAt(Instant joinedAt) {
      this.joinedAt = joinedAt;
      return this;
    }

    public Builder withUsername(String username) {
      this.username = username;
      return this;
    }

    public Builder withFirstName(String firstName) {
      this.firstName = firstName;
      return this;
    }

    public Builder withLastName(String lastName) {
      this.lastName = lastName;
      return this;
    }

    public Builder withEmail(String email) {
      this.email = email;
      return this;
    }

    public LayerMember build() {
      return new LayerMember(this);
    }
  }
}
