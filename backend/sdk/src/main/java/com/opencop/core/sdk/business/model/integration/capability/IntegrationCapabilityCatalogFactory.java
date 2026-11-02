// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.capability;

import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_BEHAVIOR;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_EXTENSION;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_INPUT;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_SCHEMA;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_SETTINGS;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.EGRESS_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.GEOCODING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INGRESS_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INTEGRATION_IDENTITY;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.ports.integration.connection.behavior.ConnectionBehaviorContribution;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionCapability;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.mapping.ConnectionMappingContribution;
import com.opencop.core.sdk.ports.integration.connection.settings.ConnectionSettingsCapability;
import com.opencop.core.sdk.ports.integration.geocoding.GeocoderCapability;
import com.opencop.core.sdk.ports.integration.mapping.EgressMappingContribution;
import com.opencop.core.sdk.ports.integration.mapping.IngressMappingContribution;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Deterministically assembles immutable integration capability catalogs. */
final class IntegrationCapabilityCatalogFactory {

  private IntegrationCapabilityCatalogFactory() {}

  static IntegrationCapabilityCatalog createStandard() {
    return create(
        List.of(
            contract(INTEGRATION_IDENTITY, "integration-identity", Set.of(), Integration.class),
            contract(
                CONNECTION_SCHEMA,
                "connection-schema",
                Set.of(INTEGRATION_IDENTITY),
                IntegrationConnectionSchema.class),
            contract(
                INGRESS_MAPPING,
                "ingress-mapping",
                Set.of(),
                IngressMappingContribution.class,
                PayloadIngressMapper.class),
            contract(
                EGRESS_MAPPING,
                "egress-mapping",
                Set.of(),
                EgressMappingContribution.class,
                PayloadEgressMapper.class),
            contract(
                CONNECTION_SETTINGS,
                "connection-settings",
                Set.of(CONNECTION_SCHEMA),
                ConnectionSettingsCapability.class),
            contract(
                CONNECTION_BEHAVIOR,
                "connection-behavior",
                Set.of(CONNECTION_SETTINGS),
                ConnectionBehaviorContribution.class),
            contract(
                CONNECTION_INPUT,
                "connection-input",
                Set.of(CONNECTION_SCHEMA),
                ConnectionExtractorCapability.class),
            contract(
                CONNECTION_MAPPING,
                "connection-mapping",
                Set.of(CONNECTION_SCHEMA),
                ConnectionMappingContribution.class),
            contract(
                GEOCODING, "geocoding", Set.of(INTEGRATION_IDENTITY), GeocoderCapability.class),
            contract(
                CONNECTION_EXTENSION,
                "connection-extension",
                Set.of(CONNECTION_SETTINGS),
                ConnectionExtensionCapability.class)));
  }

  static IntegrationCapabilityCatalog create(Collection<IntegrationCapabilityContract> contracts) {
    return new IntegrationCapabilityCatalog(contracts);
  }

  private static IntegrationCapabilityContract contract(
      IntegrationCapability capability,
      String stableKey,
      Set<IntegrationCapability> prerequisites,
      Class<?> firstContributionInterface,
      Class<?>... additionalContributionInterfaces) {
    var contributionInterfaces = new Class<?>[additionalContributionInterfaces.length + 1];
    contributionInterfaces[0] = firstContributionInterface;
    System.arraycopy(
        additionalContributionInterfaces,
        0,
        contributionInterfaces,
        1,
        additionalContributionInterfaces.length);
    return new IntegrationCapabilityContract(
        capability, stableKey, prerequisites, Set.copyOf(Arrays.asList(contributionInterfaces)));
  }
}
