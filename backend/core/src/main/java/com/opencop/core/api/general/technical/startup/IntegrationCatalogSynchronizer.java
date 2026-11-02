// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.startup;

import com.opencop.core.api.general.business.usecase.integrationcatalog.SynchronizeIntegrationCatalogUsecase;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

/** Registers the integrations this Core contributes before anything that references them. */
public final class IntegrationCatalogSynchronizer implements ApplicationRunner, Ordered {

  private final SynchronizeIntegrationCatalogUsecase usecase;

  public IntegrationCatalogSynchronizer(SynchronizeIntegrationCatalogUsecase usecase) {
    this.usecase = usecase;
  }

  @Override
  public int getOrder() {
    return StartupOrder.INTEGRATION_CATALOG;
  }

  @Override
  public void run(ApplicationArguments args) {
    usecase.synchronize();
  }
}
