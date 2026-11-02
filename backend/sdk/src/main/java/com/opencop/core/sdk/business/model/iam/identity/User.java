// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.iam.identity;

import com.opencop.core.sdk.business.model.iam.access.Group;
import java.util.HashSet;
import java.util.Set;

public class User {

  private String id;
  private String principal;
  private String username;
  private String email;
  private String firstName;
  private String lastName;
  private Boolean enabled;
  private Boolean emailVerified;
  private Long createdTimestamp;
  private Set<Group> groups = new HashSet<>();

  public User() {}

  public User(Builder builder) {
    this.id = builder.id;
    this.principal = builder.principal;
    this.username = builder.username;
    this.email = builder.email;
    this.firstName = builder.firstName;
    this.lastName = builder.lastName;
    this.enabled = builder.enabled;
    this.emailVerified = builder.emailVerified;
    this.createdTimestamp = builder.createdTimestamp;
    this.groups = builder.groups;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getPrincipal() {
    return principal;
  }

  public void setPrincipal(String principal) {
    this.principal = principal;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
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

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getEmailVerified() {
    return emailVerified;
  }

  public void setEmailVerified(Boolean emailVerified) {
    this.emailVerified = emailVerified;
  }

  public Long getCreatedTimestamp() {
    return createdTimestamp;
  }

  public void setCreatedTimestamp(Long createdTimestamp) {
    this.createdTimestamp = createdTimestamp;
  }

  public Set<Group> getGroups() {
    return groups;
  }

  public void setGroups(Set<Group> groups) {
    this.groups = groups;
  }

  public static class Builder {

    private String id;
    private String principal;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Boolean enabled;
    private Boolean emailVerified;
    private Long createdTimestamp;
    private Set<Group> groups = new HashSet<>();

    public Builder withId(String id) {
      this.id = id;
      return this;
    }

    public Builder withPrincipal(String principal) {
      this.principal = principal;
      return this;
    }

    public Builder withUsername(String username) {
      this.username = username;
      return this;
    }

    public Builder withEmail(String email) {
      this.email = email;
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

    public Builder withEnabled(Boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public Builder withEmailVerified(Boolean emailVerified) {
      this.emailVerified = emailVerified;
      return this;
    }

    public Builder withCreatedTimestamp(Long createdTimestamp) {
      this.createdTimestamp = createdTimestamp;
      return this;
    }

    public Builder withGroups(Set<Group> groups) {
      this.groups = groups;
      return this;
    }

    public User build() {
      return new User(this);
    }
  }
}
