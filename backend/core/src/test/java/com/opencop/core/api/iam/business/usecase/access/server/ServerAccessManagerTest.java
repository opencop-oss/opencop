// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.business.usecase.access.server;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerUserRepository;
import com.opencop.core.api.iam.ports.presenter.Presenter;
import com.opencop.core.sdk.business.model.iam.access.Server;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.iam.source.UpdateSourcePermissionsRequest;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServerAccessManagerTest {

  private static final UUID SERVER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448001");
  private static final UUID VIEWER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448002");
  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448003");
  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448004");

  private DataSourceRepository dataSourceRepository;
  private ServerLayerRepository serverLayerRepository;
  private ServerUserRepository serverUserRepository;
  private ServerAccessManager<Server> manager;

  @BeforeEach
  void setUp() {
    @SuppressWarnings("unchecked")
    Presenter<Server, Server> presenter = mock(Presenter.class);
    dataSourceRepository = mock(DataSourceRepository.class);
    serverLayerRepository = mock(ServerLayerRepository.class);
    serverUserRepository = mock(ServerUserRepository.class);

    manager =
        new ServerAccessManager<>(
            presenter,
            dataSourceRepository,
            serverLayerRepository,
            serverUserRepository,
            mock(UserService.class));

    when(dataSourceRepository.findById(SERVER_ID))
        .thenReturn(
            Optional.of(
                new KafkaDataSource(
                    SERVER_ID,
                    "Test Source",
                    "tenant",
                    "topic",
                    "user",
                    null,
                    null,
                    AccessMode.READ,
                    true,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "local",
                    true)));
  }

  @Test
  void updateLayers_usesAdminsBucketForAdminRole() throws Exception {
    manager.updateLayers(
        SERVER_ID,
        new UpdateSourcePermissionsRequest(Set.of(VIEWER_ID), Set.of(ADMIN_ID), Set.of(OWNER_ID)));

    verify(serverLayerRepository).addLayers(SERVER_ID, Set.of(VIEWER_ID), ServerRole.VIEWER);
    verify(serverLayerRepository).addLayers(SERVER_ID, Set.of(ADMIN_ID), ServerRole.ADMIN);
    verify(serverLayerRepository).addLayers(SERVER_ID, Set.of(OWNER_ID), ServerRole.OWNER);
    verifyNoMoreInteractions(serverLayerRepository);
  }

  @Test
  void updateUsers_usesAdminsBucketForAdminRole() throws Exception {
    manager.updateUsers(
        SERVER_ID,
        new UpdateSourcePermissionsRequest(Set.of(VIEWER_ID), Set.of(ADMIN_ID), Set.of(OWNER_ID)));

    verify(serverUserRepository).addUsers(SERVER_ID, Set.of(VIEWER_ID), ServerRole.VIEWER);
    verify(serverUserRepository).addUsers(SERVER_ID, Set.of(ADMIN_ID), ServerRole.ADMIN);
    verify(serverUserRepository).addUsers(SERVER_ID, Set.of(OWNER_ID), ServerRole.OWNER);
    verifyNoMoreInteractions(serverUserRepository);
  }
}
