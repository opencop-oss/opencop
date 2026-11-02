// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DataSourceGroupPolicy {

  private DataSourceGroupPolicy() {}

  public static void assertRetainsOwner(
      Map<UUID, DataSourceGroupRole> projectedUsers,
      Map<UUID, DataSourceGroupRole> projectedUserGroups)
      throws PersistenceException {
    boolean hasUserOwner =
        projectedUsers.values().stream().anyMatch(r -> r == DataSourceGroupRole.OWNER);
    boolean hasUserGroupOwner =
        projectedUserGroups.values().stream().anyMatch(r -> r == DataSourceGroupRole.OWNER);
    if (!hasUserOwner && !hasUserGroupOwner) {
      throw new PersistenceException("Data source group must have at least one OWNER");
    }
  }

  public static boolean affectsOwnerList(
      Map<UUID, DataSourceGroupRole> currentUsers,
      Map<UUID, DataSourceGroupRole> projectedUsers,
      Map<UUID, DataSourceGroupRole> currentUserGroups,
      Map<UUID, DataSourceGroupRole> projectedUserGroups) {
    return mapsAffectOwner(currentUsers, projectedUsers)
        || mapsAffectOwner(currentUserGroups, projectedUserGroups);
  }

  public static boolean memberPatchAffectsOwnerList(
      Map<UUID, DataSourceGroupRole> currentUsers,
      Map<UUID, DataSourceGroupRole> projectedUsers,
      Map<UUID, DataSourceGroupRole> currentUserGroups) {
    return affectsOwnerList(currentUsers, projectedUsers, currentUserGroups, currentUserGroups);
  }

  public static boolean userGroupPatchAffectsOwnerList(
      Map<UUID, DataSourceGroupRole> currentUsers,
      Map<UUID, DataSourceGroupRole> currentUserGroups,
      Map<UUID, DataSourceGroupRole> projectedUserGroups) {
    return affectsOwnerList(currentUsers, currentUsers, currentUserGroups, projectedUserGroups);
  }

  public static void assertContainerMember(
      Optional<DataSourceGroupRole> effectiveRole, boolean isGlobalAdmin) {
    assertContainerRole(
        effectiveRole,
        isGlobalAdmin,
        DataSourceGroupRole.OWNER,
        DataSourceGroupRole.ADMIN,
        DataSourceGroupRole.MAINTAINER,
        DataSourceGroupRole.MEMBER);
  }

  public static void assertContainerRole(
      Optional<DataSourceGroupRole> effectiveRole,
      boolean isGlobalAdmin,
      DataSourceGroupRole... allowedRoles) {
    if (isGlobalAdmin) {
      return;
    }

    Set<DataSourceGroupRole> allowed = Set.of(allowedRoles);
    if (effectiveRole.isPresent() && allowed.contains(effectiveRole.get())) {
      return;
    }

    throw new AuthzAccessDeniedException(
        effectiveRole.isEmpty()
            ? AuthzReasonCode.MISSING_DATA_SOURCE_GROUP_ROLE
            : AuthzReasonCode.INSUFFICIENT_DATA_SOURCE_GROUP_ROLE,
        "You are not authorized for the specified change to this data source group");
  }

  public static void assertCanAssignSourceRole(
      Optional<ServerRole> effectiveRole, ServerRole requested) {
    if (effectiveRole.isEmpty() || !effectiveRole.get().isAtLeast(requested)) {
      throw new AuthzAccessDeniedException(
          AuthzReasonCode.INSUFFICIENT_SOURCE_ROLE_FOR_DATA_SOURCE_GROUP_ASSIGNMENT,
          "You are not authorized to assign the requested data source role to this data source group");
    }
  }

  private static boolean mapsAffectOwner(
      Map<UUID, DataSourceGroupRole> current, Map<UUID, DataSourceGroupRole> projected) {
    Set<UUID> ids = new HashSet<>();
    ids.addAll(current.keySet());
    ids.addAll(projected.keySet());
    for (UUID id : ids) {
      boolean wasOwner = current.get(id) == DataSourceGroupRole.OWNER;
      boolean willBeOwner = projected.get(id) == DataSourceGroupRole.OWNER;
      if (wasOwner != willBeOwner) {
        return true;
      }
    }
    return false;
  }
}
