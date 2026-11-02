// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.profile.mapping;

import com.opencop.core.api.general.business.factory.profile.PluginMapperCatalogReconciliationPlanFactory;
import com.opencop.core.api.general.business.factory.profile.PluginMapperContributionFactory;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.Rejection;
import com.opencop.core.api.general.business.model.profile.PluginMapperContribution;
import com.opencop.core.api.general.ports.gateway.profile.MapperCatalogGateway;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Brings the mapper catalog in line with the mappers integrations contribute from code: ingress
 * mappers first, then egress mappers, each against the catalog as the previous step left it.
 */
public class PluginMapperCatalogReconciliationUsecase {

  private static final Logger log =
      LoggerFactory.getLogger(PluginMapperCatalogReconciliationUsecase.class);

  private final IngressMapperContributions ingressContributions;
  private final EgressMapperContributions egressContributions;
  private final MapperProfileService mappers;
  private final MapperCatalogGateway catalog;

  public PluginMapperCatalogReconciliationUsecase(
      IngressMapperContributions ingressContributions,
      EgressMapperContributions egressContributions,
      MapperProfileService mappers,
      MapperCatalogGateway catalog) {
    this.ingressContributions = ingressContributions;
    this.egressContributions = egressContributions;
    this.mappers = mappers;
    this.catalog = catalog;
  }

  @Transactional
  public void reconcile() {
    reconcile(
        MapperQualifier.INGRESS, PluginMapperContributionFactory.ingress(ingressContributions));
    reconcile(MapperQualifier.EGRESS, PluginMapperContributionFactory.egress(egressContributions));
  }

  private void reconcile(MapperQualifier qualifier, List<PluginMapperContribution> contributions) {
    var plan =
        PluginMapperCatalogReconciliationPlanFactory.create(
            qualifier, contributions, catalog.loadCatalog());

    logRejections(qualifier, plan.rejections());
    apply(plan);
  }

  private static void logRejections(MapperQualifier qualifier, List<Rejection> rejections) {
    for (var rejection : rejections) {
      log.warn(
          "Rejected mapper catalog contribution qualifier={} reason={} mapperId={} mapperName='{}' ownerMapperId={}",
          qualifier,
          rejection.reason().code(),
          rejection.mapperId(),
          rejection.mapperName(),
          rejection.ownerMapperId());
    }
  }

  private void apply(PluginMapperCatalogReconciliationPlan plan) {
    for (var mapper : plan.insertions()) {
      mappers.createMapperProfile(mapper, null, null, null);
    }
    for (var update : plan.metadataUpdates()) {
      catalog.updateMetadata(update.mapperId(), update.name(), update.description());
    }
    for (var assignment : plan.integrationAssignments()) {
      catalog.assignIntegration(assignment.mapperId(), assignment.integrationId());
    }
    for (var update : plan.lifecycleUpdates()) {
      catalog.reconcileAvailability(update.mapperId(), update.archived());
    }
  }
}
