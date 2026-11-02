// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.business.usecase.access.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.iam.business.usecase.access.layer.EffectiveLayerAccessService;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerUserRepository;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificException;
import com.opencop.core.sdk.business.model.iam.access.SourceLayer;
import com.opencop.core.sdk.business.model.iam.access.SourceUser;
import com.opencop.core.sdk.model.iam.source.AccessVia;
import com.opencop.core.sdk.model.iam.source.LayerInfo;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.ports.gateway.iam.SourceAccessRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class UserSpecificAccessManagerTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
  private static final UUID SERVER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
  private static final UUID OTHER_SERVER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440004");

  private ServerLayerRepository serverLayerRepository;
  private ServerUserRepository serverUserRepository;
  private EffectiveLayerAccessService effectiveLayerAccessService;
  private SourceAccessRepository sourceAccessRepository;
  private UserSpecificAccessManager manager;

  @BeforeEach
  void setUp() {
    serverLayerRepository = mock(ServerLayerRepository.class);
    serverUserRepository = mock(ServerUserRepository.class);
    effectiveLayerAccessService = mock(EffectiveLayerAccessService.class);
    sourceAccessRepository = mock(SourceAccessRepository.class);
    manager =
        new UserSpecificAccessManager(
            serverLayerRepository,
            serverUserRepository,
            effectiveLayerAccessService,
            sourceAccessRepository);
  }

  @Test
  void getLayerInfo_whenUserHasNoAccess_throwsForbidden() {
    when(effectiveLayerAccessService.getEffectiveRole(LAYER_ID, USER_ID))
        .thenReturn(Optional.empty());

    var exception =
        assertThrows(UserSpecificException.class, () -> manager.getLayerInfo(USER_ID, LAYER_ID));

    assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
  }

  @Test
  void getLayerInfo_whenUserHasAccess_returnsIndirectRoles() throws Exception {
    when(effectiveLayerAccessService.getEffectiveRole(LAYER_ID, USER_ID))
        .thenReturn(Optional.of(LayerRole.MEMBER));
    when(serverLayerRepository.getLayerServers(LAYER_ID))
        .thenReturn(
            Set.of(
                new SourceLayer.Builder()
                    .withServerId(SERVER_ID)
                    .withRole(ServerRole.ADMIN)
                    .build(),
                new SourceLayer.Builder()
                    .withServerId(OTHER_SERVER_ID)
                    .withRole(ServerRole.VIEWER)
                    .build()));

    var roles = manager.getLayerInfo(USER_ID, LAYER_ID);
    var rolesById = roles.stream().collect(Collectors.toMap(SourceRole::serverId, role -> role));

    assertEquals(ServerRole.ADMIN, rolesById.get(SERVER_ID).role());
    assertEquals(AccessVia.INDIRECT, rolesById.get(SERVER_ID).via());
    assertEquals(ServerRole.VIEWER, rolesById.get(OTHER_SERVER_ID).role());
  }

  @Test
  void getUserLayers_delegatesToEffectiveLayerAccessService() throws Exception {
    var expected =
        List.of(
            new LayerInfo(
                LAYER_ID,
                "Ops",
                LayerRole.ADMIN,
                com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope.PRIVATE,
                Instant.parse("2024-01-01T00:00:00Z"),
                true));
    when(effectiveLayerAccessService.getEffectiveLayers(USER_ID)).thenReturn(expected);

    assertEquals(expected, manager.getUserLayers(USER_ID));
  }

  @Test
  void getUserDirectServers_returnsDirectRolesOnlyAndIgnoresIndirect() throws Exception {
    when(serverUserRepository.getServersForUser(USER_ID))
        .thenReturn(
            List.of(
                new SourceUser.Builder()
                    .withUserId(USER_ID)
                    .withServerId(SERVER_ID)
                    .withRole(ServerRole.VIEWER)
                    .build()));
    when(effectiveLayerAccessService.getEffectiveLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    LAYER_ID,
                    "Ops",
                    LayerRole.MEMBER,
                    com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope.PRIVATE,
                    Instant.parse("2024-01-01T00:00:00Z"),
                    true)));
    when(serverLayerRepository.getLayerServers(LAYER_ID))
        .thenReturn(
            Set.of(
                new SourceLayer.Builder()
                    .withServerId(SERVER_ID)
                    .withRole(ServerRole.ADMIN)
                    .build()));

    var roles = manager.getUserDirectServers(USER_ID);

    assertEquals(1, roles.size());
    assertEquals(SERVER_ID, roles.getFirst().serverId());
    assertEquals(ServerRole.VIEWER, roles.getFirst().role());
    assertEquals(AccessVia.DIRECT, roles.getFirst().via());
  }

  @Test
  void getUserServers_delegatesSourceGraphToSdkSourceAccessRepository() throws Exception {
    when(sourceAccessRepository.userRoles(USER_ID))
        .thenReturn(
            List.of(
                new SourceRole(SERVER_ID, ServerRole.ADMIN, AccessVia.BOTH),
                new SourceRole(OTHER_SERVER_ID, ServerRole.VIEWER, AccessVia.INDIRECT)));

    var roles = manager.getUserServers(USER_ID);

    assertEquals(2, roles.size());
    assertEquals(SERVER_ID, roles.getFirst().serverId());
    assertEquals(ServerRole.ADMIN, roles.getFirst().role());
    assertEquals(AccessVia.BOTH, roles.getFirst().via());
    assertEquals(OTHER_SERVER_ID, roles.get(1).serverId());
    assertEquals(ServerRole.VIEWER, roles.get(1).role());
    assertEquals(AccessVia.INDIRECT, roles.get(1).via());
    verify(sourceAccessRepository).userRoles(USER_ID);
    verify(serverUserRepository, never()).getServersForUser(USER_ID);
    verify(effectiveLayerAccessService, never()).getEffectiveLayers(USER_ID);
    verify(serverLayerRepository, never()).getLayerServers(any());
  }
}
