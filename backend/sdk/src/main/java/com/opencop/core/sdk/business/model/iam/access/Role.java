// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.iam.access;

public class Role {

  private String id;
  private String name;
  private String description;
  private boolean composite;
  private boolean clientRole;
  private String containerId;

  public Role() {}

  public Role(Builder builder) {
    this.id = builder.id;
    this.name = builder.name;
    this.description = builder.description;
    this.composite = builder.composite;
    this.clientRole = builder.clientRole;
    this.containerId = builder.containerId;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
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

  public boolean isComposite() {
    return composite;
  }

  public void setComposite(boolean composite) {
    this.composite = composite;
  }

  public boolean isClientRole() {
    return clientRole;
  }

  public void setClientRole(boolean clientRole) {
    this.clientRole = clientRole;
  }

  public String getContainerId() {
    return containerId;
  }

  public void setContainerId(String containerId) {
    this.containerId = containerId;
  }

  public static class Builder {
    private String id;
    private String name;
    private String description;
    private boolean composite;
    private boolean clientRole;
    private String containerId;

    public Builder withId(String id) {
      this.id = id;
      return this;
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withDescription(String description) {
      this.description = description;
      return this;
    }

    public Builder withComposite(boolean composite) {
      this.composite = composite;
      return this;
    }

    public Builder withClientRole(boolean clientRole) {
      this.clientRole = clientRole;
      return this;
    }

    public Builder withContainerId(String containerId) {
      this.containerId = containerId;
      return this;
    }

    public Role build() {
      return new Role(this);
    }
  }
}
