// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.technical.gateway.tile.MapTileEntity;
import com.opencop.core.api.general.technical.gateway.tile.MapTileRepository;
import com.opencop.core.api.general.technical.service.EffectiveMapTileAccessService;
import com.opencop.core.sdk.component.security.TestPrincipals;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MapTileAccessUsecaseTest {

  @Mock private EffectiveMapTileAccessService effectiveAccess;
  @Mock private MapTileRepository mapTileRepository;
  @Mock private MapTileEntity tile;

  private MapTileAccessUsecase usecase;

  @BeforeEach
  void setUp() {
    usecase = new MapTileAccessUsecase(effectiveAccess, mapTileRepository);
  }

  /**
   * The id overload of {@code canReach} re-reads the tile row. Since {@code canUse} has already
   * loaded it for the enablement check, asking by id would fetch the same row twice per call.
   */
  @Test
  void canUse_resolvesAccessFromTheTileItAlreadyLoaded() {
    var tileId = UUID.randomUUID();
    var principal = principal();
    when(mapTileRepository.findById(tileId)).thenReturn(Optional.of(tile));
    when(tile.isEnabled()).thenReturn(true);
    when(effectiveAccess.canReach(tile, principal.internalUserId())).thenReturn(true);

    assertTrue(usecase.canUse(tileId, principal));

    verify(effectiveAccess).canReach(tile, principal.internalUserId());
    verify(effectiveAccess, never()).canReach(any(UUID.class), any(UUID.class));
  }

  @Test
  void canUse_whenTheTileIsDisabled_isFalse() {
    var tileId = UUID.randomUUID();
    var principal = principal();
    when(mapTileRepository.findById(tileId)).thenReturn(Optional.of(tile));
    when(tile.isEnabled()).thenReturn(false);

    assertFalse(usecase.canUse(tileId, principal));
  }

  @Test
  void canUse_whenTheTileIsMissing_isFalse() {
    var tileId = UUID.randomUUID();
    when(mapTileRepository.findById(tileId)).thenReturn(Optional.empty());

    assertFalse(usecase.canUse(tileId, principal()));
  }

  /**
   * The global-admin flag opens the management routes, not the map. Without this an administrator
   * could keep a tile they hold no grant on as their stored default basemap, so it would keep
   * rendering for them after it had left every catalog they are offered.
   */
  @Test
  void canUse_forAGlobalAdminWithNoReach_isFalse() {
    var tileId = UUID.randomUUID();
    var admin = TestPrincipals.oidcPrincipal(UUID.randomUUID(), true);
    when(mapTileRepository.findById(tileId)).thenReturn(Optional.of(tile));
    when(tile.isEnabled()).thenReturn(true);
    when(effectiveAccess.canReach(tile, admin.internalUserId())).thenReturn(false);

    assertFalse(usecase.canUse(tileId, admin));
  }

  private static OpenCopPrincipal principal() {
    return TestPrincipals.mockPrincipal(UUID.randomUUID());
  }
}
