// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.iam.access;

import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.time.Instant;
import java.util.UUID;

public class SourceUser {

  private String firstName;
  private String lastName;
  private String username;
  private UUID serverId;
  private UUID userId;
  private ServerRole role;
  private Instant grantedAt;

  public SourceUser() {}

  public SourceUser(Builder builder) {
    this.firstName = builder.firstName;
    this.lastName = builder.lastName;
    this.username = builder.username;
    this.serverId = builder.serverId;
    this.userId = builder.userId;
    this.role = builder.role;
    this.grantedAt = builder.grantedAt;
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

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public UUID getServerId() {
    return serverId;
  }

  public void setServerId(UUID serverId) {
    this.serverId = serverId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public ServerRole getRole() {
    return role;
  }

  public void setRole(ServerRole role) {
    this.role = role;
  }

  public Instant getGrantedAt() {
    return grantedAt;
  }

  public void setGrantedAt(Instant grantedAt) {
    this.grantedAt = grantedAt;
  }

  public static class Builder {

    private String firstName;
    private String lastName;
    private String username;
    private UUID serverId;
    private UUID userId;
    private ServerRole role;
    private Instant grantedAt;

    public Builder withFirstName(String firstName) {
      this.firstName = firstName;
      return this;
    }

    public Builder withLastName(String lastName) {
      this.lastName = lastName;
      return this;
    }

    public Builder withUsername(String username) {
      this.username = username;
      return this;
    }

    public Builder withServerId(UUID serverId) {
      this.serverId = serverId;
      return this;
    }

    public Builder withUserId(UUID userId) {
      this.userId = userId;
      return this;
    }

    public Builder withRole(ServerRole role) {
      this.role = role;
      return this;
    }

    public Builder withGrantedAt(Instant grantedAt) {
      this.grantedAt = grantedAt;
      return this;
    }

    public SourceUser build() {
      return new SourceUser(this);
    }
  }
}
