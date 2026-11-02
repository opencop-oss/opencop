// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.scim.relationship;

import java.io.Serializable;
import java.util.UUID;

public class InternalUserGroupRelationshipId implements Serializable {

  private UUID user;
  private UUID group;

  public InternalUserGroupRelationshipId() {}

  public InternalUserGroupRelationshipId(UUID user, UUID group) {
    this.user = user;
    this.group = group;
  }

  // Getters and setters
  public UUID getUser() {
    return user;
  }

  public void setUser(UUID user) {
    this.user = user;
  }

  public UUID getGroup() {
    return group;
  }

  public void setGroup(UUID group) {
    this.group = group;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    InternalUserGroupRelationshipId that = (InternalUserGroupRelationshipId) o;

    if (!user.equals(that.user)) return false;
    return group.equals(that.group);
  }

  @Override
  public int hashCode() {
    int result = user.hashCode();
    result = 31 * result + group.hashCode();
    return result;
  }
}
