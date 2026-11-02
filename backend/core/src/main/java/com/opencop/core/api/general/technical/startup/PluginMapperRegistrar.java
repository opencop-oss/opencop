// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.startup;

import com.opencop.core.api.general.business.usecase.profile.mapping.PluginMapperCatalogReconciliationUsecase;
import com.opencop.core.api.general.technical.config.StartupLeader;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

/** Registers the mappers integrations contribute, on the startup leader only. */
public final class PluginMapperRegistrar implements ApplicationRunner, Ordered {

  private final PluginMapperCatalogReconciliationUsecase usecase;
  private final StartupLeader startupLeader;

  public PluginMapperRegistrar(
      PluginMapperCatalogReconciliationUsecase usecase, StartupLeader startupLeader) {
    this.usecase = usecase;
    this.startupLeader = startupLeader;
  }

  @Override
  public int getOrder() {
    return StartupOrder.MAPPER_REGISTRATION;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (startupLeader.isLeader()) {
      usecase.reconcile();
    }
  }
}
