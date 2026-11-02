// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class LayerGroupPolicy {

  private LayerGroupPolicy() {}

  /**
   * Rejects a patch whose projected post-image would leave the layer group with no OWNER across
   * both user and user-group assignments. Throws if neither map contains an OWNER. Both maps being
   * empty is considered no-owner and is rejected.
   */
  public static void assertRetainsOwner(
      Map<UUID, LayerGroupRole> projectedUsers, Map<UUID, LayerGroupRole> projectedUserGroups)
      throws PersistenceException {
    boolean hasUserOwner =
        projectedUsers.values().stream().anyMatch(r -> r == LayerGroupRole.OWNER);
    boolean hasUserGroupOwner =
        projectedUserGroups.values().stream().anyMatch(r -> r == LayerGroupRole.OWNER);
    if (!hasUserOwner && !hasUserGroupOwner) {
      throw new PersistenceException("Layer group must have at least one OWNER");
    }
  }

  /**
   * Rejects a member patch whose projected post-image would leave the layer group with no OWNER. An
   * empty projected map is allowed (removing all members is permitted). When both maps are
   * non-empty (or a user-group side is also considered), use the two-map overload directly.
   */
  public static void assertRetainsOwner(Map<UUID, LayerGroupRole> projected)
      throws PersistenceException {
    if (projected.isEmpty()) {
      return;
    }
    assertRetainsOwner(projected, Map.of());
  }

  /**
   * Returns true if the projected change adds, removes, or alters an OWNER assignment in either the
   * user map or the user-group map. Use this to gate owner-list changes behind OWNER-only
   * authorization.
   */
  public static boolean affectsOwnerList(
      Map<UUID, LayerGroupRole> currentUsers,
      Map<UUID, LayerGroupRole> projectedUsers,
      Map<UUID, LayerGroupRole> currentUserGroups,
      Map<UUID, LayerGroupRole> projectedUserGroups) {
    if (mapsAffectOwner(currentUsers, projectedUsers)) {
      return true;
    }
    return mapsAffectOwner(currentUserGroups, projectedUserGroups);
  }

  public static boolean memberPatchAffectsOwnerList(
      Map<UUID, LayerGroupRole> currentUsers,
      Map<UUID, LayerGroupRole> projectedUsers,
      Map<UUID, LayerGroupRole> currentUserGroups) {
    return affectsOwnerList(currentUsers, projectedUsers, currentUserGroups, currentUserGroups);
  }

  public static boolean userGroupPatchAffectsOwnerList(
      Map<UUID, LayerGroupRole> currentUsers,
      Map<UUID, LayerGroupRole> currentUserGroups,
      Map<UUID, LayerGroupRole> projectedUserGroups) {
    return affectsOwnerList(currentUsers, currentUsers, currentUserGroups, projectedUserGroups);
  }

  /**
   * Returns true if the projected user-map change adds, removes, or alters an OWNER assignment.
   * Delegates to the four-map overload treating user-group maps as empty.
   */
  public static boolean affectsOwnerList(
      Map<UUID, LayerGroupRole> current, Map<UUID, LayerGroupRole> projected) {
    return affectsOwnerList(current, projected, Map.of(), Map.of());
  }

  public static void assertContainerMember(
      Optional<LayerGroupRole> effectiveRole, boolean isGlobalAdmin) {
    assertContainerRole(
        effectiveRole,
        isGlobalAdmin,
        LayerGroupRole.OWNER,
        LayerGroupRole.ADMIN,
        LayerGroupRole.MAINTAINER,
        LayerGroupRole.MEMBER);
  }

  public static void assertContainerRole(
      Optional<LayerGroupRole> effectiveRole,
      boolean isGlobalAdmin,
      LayerGroupRole... allowedRoles) {
    if (isGlobalAdmin) {
      return;
    }

    Set<LayerGroupRole> allowed = Set.of(allowedRoles);
    if (effectiveRole.isPresent() && allowed.contains(effectiveRole.get())) {
      return;
    }

    throw new AuthzAccessDeniedException(
        effectiveRole.isEmpty()
            ? AuthzReasonCode.MISSING_LAYER_GROUP_ROLE
            : AuthzReasonCode.INSUFFICIENT_LAYER_GROUP_ROLE,
        "You are not authorized for the specified change to this layer group");
  }

  private static boolean mapsAffectOwner(
      Map<UUID, LayerGroupRole> current, Map<UUID, LayerGroupRole> projected) {
    Set<UUID> ids = new HashSet<>();
    ids.addAll(current.keySet());
    ids.addAll(projected.keySet());
    for (UUID id : ids) {
      boolean wasOwner = current.get(id) == LayerGroupRole.OWNER;
      boolean willBeOwner = projected.get(id) == LayerGroupRole.OWNER;
      if (wasOwner != willBeOwner) {
        return true;
      }
    }
    return false;
  }

  /**
   * Rejects a layer-assignment of a non-PRIVATE layer by a non-global-admin. {@code
   * EffectiveLayerAccessService} falls back to {@link LayerRole#MEMBER} for every user on every
   * GLOBAL layer, and that inferred role is enough to pass {@link #assertCanAssignRole} at MEMBER,
   * so without this gate any user could pull every global layer into a group they own and share it
   * onward. Global admins are exempt and must not be routed here; they may still assign a GLOBAL
   * layer into a PRIVATE group.
   */
  public static void assertLayerIsPrivate(LayerAccessScope accessScope, UUID layerId) {
    if (accessScope == LayerAccessScope.PRIVATE) {
      return;
    }

    throw new AuthzAccessDeniedException(
        AuthzReasonCode.INSUFFICIENT_LAYER_SCOPE_FOR_LAYER_GROUP_ASSIGNMENT,
        "Cannot assign layer "
            + layerId
            + " to a layer group: only admins can assign a "
            + accessScope
            + " layer");
  }

  /**
   * Rejects a layer-assignment by an actor with less than EDITOR on the layer, since assigning a
   * layer to a group grants it onward to the group's members. {@link #assertCanAssignRole} then
   * decides which role the assignment may carry.
   */
  public static void assertCanAssignLayer(Optional<LayerRole> effectiveRole, UUID layerId) {
    if (effectiveRole.isPresent() && effectiveRole.get().isAtLeast(LayerRole.EDITOR)) {
      return;
    }

    throw new AuthzAccessDeniedException(
        effectiveRole.isEmpty()
            ? AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE
            : AuthzReasonCode.INSUFFICIENT_SOURCE_OR_LAYER_ROLE,
        "Cannot assign layer "
            + layerId
            + " to a layer group: requires EDITOR or above on the layer, actor's effective role is "
            + effectiveRole.map(LayerRole::name).orElse("NONE"));
  }

  /**
   * Rejects a layer-assignment where the actor would grant a role higher than their own effective
   * role on that layer (privilege escalation guard).
   */
  public static void assertCanAssignRole(
      Optional<LayerRole> effectiveRole, LayerRole requested, UUID layerId) {
    if (effectiveRole.isEmpty() || requested.isHigherThan(effectiveRole.get())) {
      throw new AuthzAccessDeniedException(
          effectiveRole.isEmpty()
              ? AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE
              : AuthzReasonCode.INSUFFICIENT_SOURCE_OR_LAYER_ROLE,
          "Cannot assign role "
              + requested
              + " on layer "
              + layerId
              + ": actor's effective role is "
              + effectiveRole.map(LayerRole::name).orElse("NONE"));
    }
  }
}
