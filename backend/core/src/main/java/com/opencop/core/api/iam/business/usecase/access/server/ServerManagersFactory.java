// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.business.usecase.access.server;

import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerUserRepository;
import com.opencop.core.api.iam.ports.usecase.access.server.ServerManager;
import com.opencop.core.api.iam.technical.presenter.ServerPresenter;
import com.opencop.core.sdk.business.model.iam.access.Server;

public class ServerManagersFactory {

  private ServerManagersFactory() {}

  public static ServerManager<Server> serverAccessManager(
      final DataSourceRepository dataSourceRepository,
      final ServerLayerRepository serverLayerRepository,
      final ServerUserRepository serverUserRepository,
      final UserService userService) {
    return new ServerAccessManager<>(
        new ServerPresenter(),
        dataSourceRepository,
        serverLayerRepository,
        serverUserRepository,
        userService);
  }
}
