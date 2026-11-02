// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.iam.access;

import java.util.UUID;

public class Server {

  private String name;
  private UUID serverId;
  private String state;

  public Server() {}

  public Server(Builder builder) {
    this.serverId = builder.serverId;
    this.name = builder.name;
    this.state = builder.state;
  }

  public UUID getServerId() {
    return serverId;
  }

  public void setServerId(UUID serverId) {
    this.serverId = serverId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public static class Builder {

    private String name;
    private UUID serverId;
    private String state;

    public Builder withServerId(UUID serverId) {
      this.serverId = serverId;
      return this;
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withState(String state) {
      this.state = state;
      return this;
    }

    public Server build() {
      return new Server(this);
    }
  }
}
