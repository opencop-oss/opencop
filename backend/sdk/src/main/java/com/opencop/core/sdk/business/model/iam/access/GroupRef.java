// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.iam.access;

public class GroupRef {

  private String id;
  private String name;
  private String path;

  public GroupRef() {}

  private GroupRef(Builder builder) {
    this.id = builder.id;
    this.name = builder.name;
    this.path = builder.path;
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

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public static class Builder {

    private String id;
    private String name;
    private String path;

    public Builder withId(String id) {
      this.id = id;
      return this;
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withPath(String path) {
      this.path = path;
      return this;
    }

    public GroupRef build() {
      return new GroupRef(this);
    }
  }
}
