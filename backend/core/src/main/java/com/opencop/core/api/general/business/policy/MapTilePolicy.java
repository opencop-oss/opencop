// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import com.opencop.core.api.general.ports.usecase.map.MapTileExceptionCodes;
import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.MapTileAccessScope;
import java.util.Optional;

/**
 * Every rule governing imagery tiles, as decisions over already-resolved inputs.
 *
 * <p>Nothing here reads the database. Callers resolve the caller's access and the tile's current
 * state first, then ask this class whether the operation is allowed. That keeps the rules in one
 * readable place and lets them be tested without persistence.
 *
 * <p>A grant on a tile carries no role. Holding one means the tile is visible to that principal and
 * nothing more: imagery is managed by global admins, so there is no per-tile ladder to express.
 */
public final class MapTilePolicy {

  private MapTilePolicy() {}

  // --- Access ------------------------------------------------------------

  /** Whether the caller may see the tile. */
  public static boolean canView(boolean hasGrant, boolean isGlobalAdmin) {
    return isGlobalAdmin || hasGrant;
  }

  /**
   * Whether the caller may put the tile on a map. Access and enablement are separate axes and
   * anything handing a tile to an operator has to satisfy both.
   *
   * <p>Unlike {@link #canView}, this takes no global-admin bypass. Administering a tile is a
   * separate act from carrying it on your own map, so an administrator's operator surfaces (the
   * catalog they are offered and the default basemap they have stored) resolve from reach alone,
   * the same as anyone else's.
   */
  public static boolean canUse(boolean hasGrant, boolean enabled) {
    return enabled && hasGrant;
  }

  /** The gate for reading a tile and using it on the map. */
  public static void assertCanView(boolean hasGrant, boolean isGlobalAdmin) {
    if (canView(hasGrant, isGlobalAdmin)) {
      return;
    }
    throw new AuthzAccessDeniedException(
        AuthzReasonCode.MISSING_MAP_TILE_ROLE, "You do not have access to this imagery layer");
  }

  /**
   * Who may change a tile: edit it, delete it, or change who can see it.
   *
   * <p>Global admins only. A grant carries no role, so holding one confers visibility and nothing
   * more. This is enforced here rather than by the route prefix because the tile routes sit outside
   * {@code /v1/admin/**}.
   */
  public static void assertMayManage(boolean isGlobalAdmin) {
    if (!isGlobalAdmin) {
      throw new AuthzAccessDeniedException(
          AuthzReasonCode.MISSING_MAP_TILE_ROLE,
          "Only an administrator can manage an imagery layer");
    }
  }

  // --- Scope -------------------------------------------------------------

  /**
   * Publishing a tile to everyone is a global-admin act. Without this, anyone able to edit a tile
   * could make it world-readable.
   */
  public static void assertMayUseGlobalScope(
      Optional<MapTileAccessScope> requestedScope, boolean isGlobalAdmin) {
    if (requestedScope.filter(scope -> scope == MapTileAccessScope.GLOBAL).isPresent()
        && !isGlobalAdmin) {
      throw failure(MapTileExceptionCodes.GLOBAL_SCOPE_REQUIRES_ADMIN);
    }
  }

  // --- Default tile ------------------------------------------------------

  /**
   * A tile may only be somebody's default basemap while they can still use it. Access can be
   * revoked, narrowed or disabled long after the choice was made, so this is asserted when a
   * preference is stored; responses that expand a stored tile re-check with {@code canUse}
   * directly.
   */
  public static void assertMayBeDefaultTile(boolean canUse) {
    if (!canUse) {
      throw failure(MapTileExceptionCodes.DEFAULT_TILE_NOT_AVAILABLE);
    }
  }

  // --- Primary -----------------------------------------------------------

  /**
   * Whether a tile is eligible to be the primary basemap: it must be a basemap, enabled, and
   * GLOBAL. A primary any operator can be denied leaves them with no basemap at all.
   */
  public static boolean canBePrimary(
      boolean isBasemap, boolean enabled, MapTileAccessScope accessScope) {
    return isBasemap && enabled && accessScope == MapTileAccessScope.GLOBAL;
  }

  /** As {@link #canBePrimary}, reporting which condition failed. */
  public static void assertMayBePrimary(
      boolean isBasemap, boolean enabled, MapTileAccessScope accessScope) {
    if (!isBasemap) {
      throw failure(MapTileExceptionCodes.PRIMARY_MUST_BE_BASEMAP);
    }
    if (!enabled) {
      throw failure(MapTileExceptionCodes.PRIMARY_MUST_BE_ENABLED);
    }
    if (accessScope != MapTileAccessScope.GLOBAL) {
      throw failure(MapTileExceptionCodes.PRIMARY_MUST_BE_GLOBAL);
    }
  }

  /**
   * Deleting the primary basemap is allowed only when another tile can take over. Auto-promotion on
   * delete considers enabled GLOBAL basemaps only, so without a successor the catalog would be left
   * with no primary at all and every operator falls back to it. Demote it first by making another
   * basemap primary, or make one eligible.
   */
  public static void assertMayDelete(boolean isPrimary, boolean hasEligibleSuccessor) {
    if (isPrimary && !hasEligibleSuccessor) {
      throw failure(MapTileExceptionCodes.PRIMARY_CANNOT_BE_DELETED);
    }
  }

  private static GlobalOpenCopException failure(MapTileExceptionCodes code) {
    return new GlobalOpenCopException(code.code(), code.description(), code.httpStatus());
  }
}
