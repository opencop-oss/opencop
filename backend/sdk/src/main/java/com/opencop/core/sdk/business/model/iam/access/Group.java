// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.iam.access;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Group {

  private String id;
  private String name;
  private String path;
  private Map<String, List<String>> attributes = new HashMap<>();

  public Group() {}

  public Group(Builder builder) {
    this.id = builder.id;
    this.name = builder.name;
    this.path = builder.path;
    this.attributes = builder.attributes;
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

  public Map<String, List<String>> getAttributes() {
    return attributes;
  }

  public void setAttributes(Map<String, List<String>> attributes) {
    this.attributes = attributes;
  }

  public static class Builder {

    private String id;
    private String name;
    private String path;
    private Map<String, List<String>> attributes;

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

    public Builder withAttributes(Map<String, List<String>> attributes) {
      this.attributes = attributes;
      return this;
    }

    public Group build() {
      return new Group(this);
    }
  }
}
