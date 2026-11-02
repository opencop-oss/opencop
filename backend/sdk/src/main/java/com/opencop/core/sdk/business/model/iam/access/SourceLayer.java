// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.iam.access;

import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.time.Instant;
import java.util.UUID;

public class SourceLayer {

  private String serverName;
  private UUID serverId;
  private String serverState;
  private Instant lastMessageAt;
  private ServerRole role;
  private Layer layer;
  private Instant grantedAt;

  public SourceLayer() {}

  public SourceLayer(Builder builder) {
    this.serverName = builder.serverName;
    this.serverId = builder.serverId;
    this.role = builder.role;
    this.layer = builder.layer;
    this.grantedAt = builder.grantedAt;
    this.serverState = builder.serverState;
    this.lastMessageAt = builder.lastMessageAt;
  }

  public String getServerName() {
    return serverName;
  }

  public void setServerName(String serverName) {
    this.serverName = serverName;
  }

  public UUID getServerId() {
    return serverId;
  }

  public void setServerId(UUID serverId) {
    this.serverId = serverId;
  }

  public String getServerState() {
    return serverState;
  }

  public void setServerState(String serverState) {
    this.serverState = serverState;
  }

  public Instant getLastMessageAt() {
    return lastMessageAt;
  }

  public void setLastMessageAt(Instant lastMessageAt) {
    this.lastMessageAt = lastMessageAt;
  }

  public ServerRole getRole() {
    return role;
  }

  public void setRole(ServerRole role) {
    this.role = role;
  }

  public Layer getLayer() {
    return layer;
  }

  public void setLayer(Layer layer) {
    this.layer = layer;
  }

  public Instant getGrantedAt() {
    return grantedAt;
  }

  public void setGrantedAt(Instant grantedAt) {
    this.grantedAt = grantedAt;
  }

  public static class Builder {

    private String serverName;
    private UUID serverId;
    private ServerRole role;
    private Layer layer;
    private Instant grantedAt;
    private String serverState;
    private Instant lastMessageAt;

    public Builder withServerName(String serverName) {
      this.serverName = serverName;
      return this;
    }

    public Builder withServerId(UUID serverId) {
      this.serverId = serverId;
      return this;
    }

    public Builder withServerState(String serverState) {
      this.serverState = serverState;
      return this;
    }

    public Builder withLastMessageAt(Instant lastMessageAt) {
      this.lastMessageAt = lastMessageAt;
      return this;
    }

    public Builder withRole(ServerRole role) {
      this.role = role;
      return this;
    }

    public Builder withLayer(Layer layer) {
      this.layer = layer;
      return this;
    }

    public Builder withGrantedAt(Instant grantedAt) {
      this.grantedAt = grantedAt;
      return this;
    }

    public SourceLayer build() {
      return new SourceLayer(this);
    }
  }
}
