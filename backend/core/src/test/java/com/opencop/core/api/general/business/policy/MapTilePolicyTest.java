// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.MapTileAccessScope;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MapTilePolicyTest {

  // --- Access ------------------------------------------------------------

  @Test
  void canView_requiresAGrantOrGlobalAdmin() {
    assertTrue(MapTilePolicy.canView(true, false));
    assertTrue(MapTilePolicy.canView(false, true));
    assertFalse(MapTilePolicy.canView(false, false));
  }

  @Test
  void assertCanView_withoutAGrant_isDenied() {
    var ex =
        assertThrows(
            AuthzAccessDeniedException.class, () -> MapTilePolicy.assertCanView(false, false));

    assertEquals(AuthzReasonCode.MISSING_MAP_TILE_ROLE, ex.reasonCode());
  }

  @Test
  void assertCanView_withAGrant_isAllowed() {
    assertDoesNotThrow(() -> MapTilePolicy.assertCanView(true, false));
  }

  @Test
  void assertCanView_forAGlobalAdmin_isAllowedWithoutAGrant() {
    assertDoesNotThrow(() -> MapTilePolicy.assertCanView(false, true));
  }

  // --- Manage ------------------------------------------------------------

  @Test
  void assertMayManage_forANonAdmin_isDenied() {
    var ex =
        assertThrows(AuthzAccessDeniedException.class, () -> MapTilePolicy.assertMayManage(false));

    assertEquals(AuthzReasonCode.MISSING_MAP_TILE_ROLE, ex.reasonCode());
  }

  @Test
  void assertMayManage_forAGlobalAdmin_isAllowed() {
    assertDoesNotThrow(() -> MapTilePolicy.assertMayManage(true));
  }

  /**
   * A grant conveys visibility only. Without this the edit, delete and grant routes would be open
   * to anyone holding one, since they sit outside the {@code /v1/admin/**} prefix.
   */
  @Test
  void assertMayManage_isNotSatisfiedByHoldingAGrant() {
    assertTrue(MapTilePolicy.canView(true, false));
    assertThrows(AuthzAccessDeniedException.class, () -> MapTilePolicy.assertMayManage(false));
  }

  // --- canUse ------------------------------------------------------------

  @Test
  void canUse_requiresBothAccessAndEnablement() {
    assertTrue(MapTilePolicy.canUse(true, true));
    assertFalse(MapTilePolicy.canUse(true, false));
    assertFalse(MapTilePolicy.canUse(false, true));
  }

  /**
   * Putting a tile on a map is not an administrative act. {@link MapTilePolicy#canView} lets an
   * administrator read and manage a tile they hold no grant on; this gate deliberately does not, so
   * their own map surfaces resolve from reach like everyone else's.
   */
  @Test
  void canUse_takesNoGlobalAdminBypass_unlikeCanView() {
    assertTrue(MapTilePolicy.canView(false, true));
    assertFalse(MapTilePolicy.canUse(false, true));
  }

  // --- Scope -------------------------------------------------------------

  @Test
  void assertMayUseGlobalScope_forANonAdmin_isRejected() {
    assertThrows(
        GlobalOpenCopException.class,
        () -> MapTilePolicy.assertMayUseGlobalScope(Optional.of(MapTileAccessScope.GLOBAL), false));
  }

  @Test
  void assertMayUseGlobalScope_stayingPrivate_isAllowedForAnyone() {
    assertDoesNotThrow(
        () ->
            MapTilePolicy.assertMayUseGlobalScope(Optional.of(MapTileAccessScope.PRIVATE), false));
  }

  // --- Primary -----------------------------------------------------------

  @Test
  void canBePrimary_requiresBasemapEnabledAndGlobal() {
    assertTrue(MapTilePolicy.canBePrimary(true, true, MapTileAccessScope.GLOBAL));
    assertFalse(MapTilePolicy.canBePrimary(false, true, MapTileAccessScope.GLOBAL));
    assertFalse(MapTilePolicy.canBePrimary(true, false, MapTileAccessScope.GLOBAL));
    assertFalse(MapTilePolicy.canBePrimary(true, true, MapTileAccessScope.PRIVATE));
  }

  @Test
  void assertMayBePrimary_reportsWhichConditionFailed() {
    assertEquals(
        "MAP-TILE-1005",
        assertThrows(
                GlobalOpenCopException.class,
                () -> MapTilePolicy.assertMayBePrimary(false, true, MapTileAccessScope.GLOBAL))
            .getCode());
    assertEquals(
        "MAP-TILE-1010",
        assertThrows(
                GlobalOpenCopException.class,
                () -> MapTilePolicy.assertMayBePrimary(true, false, MapTileAccessScope.GLOBAL))
            .getCode());
    assertEquals(
        "MAP-TILE-1011",
        assertThrows(
                GlobalOpenCopException.class,
                () -> MapTilePolicy.assertMayBePrimary(true, true, MapTileAccessScope.PRIVATE))
            .getCode());
  }

  // --- Delete ------------------------------------------------------------

  @Test
  void assertMayDelete_forThePrimaryTile_withNoEligibleSuccessor_isRejected() {
    assertEquals(
        "MAP-TILE-1013",
        assertThrows(GlobalOpenCopException.class, () -> MapTilePolicy.assertMayDelete(true, false))
            .getCode());
  }

  @Test
  void assertMayDelete_forThePrimaryTile_withASuccessor_isAllowed() {
    assertDoesNotThrow(() -> MapTilePolicy.assertMayDelete(true, true));
  }

  /** A non-primary tile is unaffected by the successor question. */
  @Test
  void assertMayDelete_forANonPrimaryTile_isAlwaysAllowed() {
    assertDoesNotThrow(() -> MapTilePolicy.assertMayDelete(false, false));
    assertDoesNotThrow(() -> MapTilePolicy.assertMayDelete(false, true));
  }

  // --- Default tile ------------------------------------------------------

  @Test
  void assertMayBeDefaultTile_whenTheCallerCannotUseIt_isRejected() {
    assertEquals(
        "MAP-TILE-1015",
        assertThrows(
                GlobalOpenCopException.class, () -> MapTilePolicy.assertMayBeDefaultTile(false))
            .getCode());
  }

  @Test
  void assertMayBeDefaultTile_whenTheCallerCanUseIt_isAllowed() {
    assertDoesNotThrow(() -> MapTilePolicy.assertMayBeDefaultTile(true));
  }
}
