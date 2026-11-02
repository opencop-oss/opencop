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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IntegrationCapabilityCatalogTest {

  @Test
  void exposesTheStableProviderNeutralCapabilityCatalog() {
    var catalog = IntegrationCapabilityCatalog.standard();

    assertThat(IntegrationCapability.values())
        .containsExactly(
            INTEGRATION_IDENTITY,
            CONNECTION_SCHEMA,
            INGRESS_MAPPING,
            EGRESS_MAPPING,
            CONNECTION_BEHAVIOR,
            CONNECTION_INPUT,
            CONNECTION_MAPPING,
            GEOCODING,
            CONNECTION_SETTINGS,
            CONNECTION_EXTENSION);

    assertThat(catalog.contractFor(INTEGRATION_IDENTITY).stableKey())
        .isEqualTo("integration-identity");
    assertThat(catalog.contractFor(INTEGRATION_IDENTITY).prerequisites()).isEmpty();
    assertThat(catalog.contractFor(INTEGRATION_IDENTITY).contributionInterfaces())
        .containsExactly(Integration.class);

    assertThat(catalog.contractFor(CONNECTION_SCHEMA).stableKey()).isEqualTo("connection-schema");
    assertThat(catalog.contractFor(CONNECTION_SCHEMA).prerequisites())
        .containsExactly(INTEGRATION_IDENTITY);
    assertThat(catalog.contractFor(CONNECTION_SCHEMA).contributionInterfaces())
        .containsExactly(IntegrationConnectionSchema.class);

    assertThat(catalog.contractFor(INGRESS_MAPPING).stableKey()).isEqualTo("ingress-mapping");
    assertThat(catalog.contractFor(INGRESS_MAPPING).prerequisites()).isEmpty();
    assertThat(catalog.contractFor(INGRESS_MAPPING).contributionInterfaces())
        .containsExactlyInAnyOrder(IngressMappingContribution.class, PayloadIngressMapper.class);

    assertThat(catalog.contractFor(EGRESS_MAPPING).stableKey()).isEqualTo("egress-mapping");
    assertThat(catalog.contractFor(EGRESS_MAPPING).prerequisites()).isEmpty();
    assertThat(catalog.contractFor(EGRESS_MAPPING).contributionInterfaces())
        .containsExactlyInAnyOrder(EgressMappingContribution.class, PayloadEgressMapper.class);

    assertThat(catalog.contractFor(CONNECTION_SETTINGS).stableKey())
        .isEqualTo("connection-settings");
    assertThat(catalog.contractFor(CONNECTION_SETTINGS).prerequisites())
        .containsExactly(CONNECTION_SCHEMA);
    assertThat(catalog.contractFor(CONNECTION_SETTINGS).contributionInterfaces())
        .containsExactly(ConnectionSettingsCapability.class);

    assertThat(catalog.contractFor(CONNECTION_BEHAVIOR).stableKey())
        .isEqualTo("connection-behavior");
    assertThat(catalog.contractFor(CONNECTION_BEHAVIOR).prerequisites())
        .containsExactly(CONNECTION_SETTINGS);
    assertThat(catalog.contractFor(CONNECTION_BEHAVIOR).contributionInterfaces())
        .containsExactly(ConnectionBehaviorContribution.class);
    assertThat(catalog.expandWithPrerequisites(Set.of(CONNECTION_BEHAVIOR)))
        .containsExactlyInAnyOrder(
            CONNECTION_BEHAVIOR, CONNECTION_SETTINGS, CONNECTION_SCHEMA, INTEGRATION_IDENTITY)
        .isUnmodifiable();

    assertThat(catalog.contractFor(CONNECTION_INPUT).stableKey()).isEqualTo("connection-input");
    assertThat(catalog.contractFor(CONNECTION_INPUT).prerequisites())
        .containsExactly(CONNECTION_SCHEMA);
    assertThat(catalog.contractFor(CONNECTION_INPUT).contributionInterfaces())
        .containsExactly(ConnectionExtractorCapability.class);
    assertThat(catalog.expandWithPrerequisites(Set.of(CONNECTION_INPUT)))
        .containsExactlyInAnyOrder(CONNECTION_INPUT, CONNECTION_SCHEMA, INTEGRATION_IDENTITY);
    assertThat(catalog.contractFor(CONNECTION_MAPPING).stableKey()).isEqualTo("connection-mapping");
    assertThat(catalog.contractFor(CONNECTION_MAPPING).prerequisites())
        .containsExactly(CONNECTION_SCHEMA);
    assertThat(catalog.contractFor(CONNECTION_MAPPING).contributionInterfaces())
        .containsExactly(ConnectionMappingContribution.class);
    assertThat(catalog.contractFor(GEOCODING).stableKey()).isEqualTo("geocoding");
    assertThat(catalog.contractFor(GEOCODING).prerequisites())
        .containsExactly(INTEGRATION_IDENTITY);
    assertThat(catalog.contractFor(GEOCODING).contributionInterfaces())
        .containsExactly(GeocoderCapability.class);
    assertThat(catalog.contractFor(CONNECTION_EXTENSION).stableKey())
        .isEqualTo("connection-extension");
    assertThat(catalog.contractFor(CONNECTION_EXTENSION).prerequisites())
        .containsExactly(CONNECTION_SETTINGS);
    assertThat(catalog.contractFor(CONNECTION_EXTENSION).contributionInterfaces())
        .containsExactly(ConnectionExtensionCapability.class);
  }

  @Test
  void compilesTransitivePrerequisitesFromDeclarativeContracts() {
    var contracts = validContracts();
    contracts.put(
        CONNECTION_INPUT,
        contract(
            CONNECTION_INPUT, Set.of(CONNECTION_BEHAVIOR), ConnectionExtractorCapability.class));

    var catalog = IntegrationCapabilityCatalogFactory.create(contracts.values());

    assertThat(catalog.expandWithPrerequisites(Set.of(CONNECTION_INPUT)))
        .containsExactlyInAnyOrder(
            CONNECTION_INPUT,
            CONNECTION_BEHAVIOR,
            CONNECTION_SETTINGS,
            CONNECTION_SCHEMA,
            INTEGRATION_IDENTITY);
  }

  @Test
  void reportsTheOwnerAndInvalidStableKey() {
    var blankKey = validContracts();
    blankKey.put(
        INGRESS_MAPPING, contract(INGRESS_MAPPING, " ", Set.of(), PayloadIngressMapper.class));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IntegrationCapabilityCatalogFactory.create(blankKey.values()))
        .withMessageContaining("INGRESS_MAPPING")
        .withMessageContaining("invalid stable key ' '");
  }

  @Test
  void reportsBothOwnersAndTheDuplicateStableKey() {
    var duplicateKey = validContracts();
    duplicateKey.put(
        INGRESS_MAPPING,
        contract(
            INGRESS_MAPPING,
            IntegrationCapabilityCatalog.standard().contractFor(INTEGRATION_IDENTITY).stableKey(),
            Set.of(),
            PayloadIngressMapper.class));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IntegrationCapabilityCatalogFactory.create(duplicateKey.values()))
        .withMessageContaining("stable key 'integration-identity'")
        .withMessageContaining("INTEGRATION_IDENTITY")
        .withMessageContaining("INGRESS_MAPPING");
  }

  @Test
  void reportsEveryMissingCapability() {
    var missingContract = validContracts();
    missingContract.remove(INGRESS_MAPPING);
    missingContract.remove(CONNECTION_INPUT);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IntegrationCapabilityCatalogFactory.create(missingContract.values()))
        .withMessageContaining("missing contracts")
        .withMessageContaining("[INGRESS_MAPPING, CONNECTION_INPUT]");
  }

  @Test
  void rejectsDuplicateCatalogEntries() {
    var duplicateContracts = new ArrayList<>(validContracts().values());
    duplicateContracts.add(contract(INGRESS_MAPPING, Set.of(), PayloadIngressMapper.class));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IntegrationCapabilityCatalogFactory.create(duplicateContracts))
        .withMessageContaining("exactly once");
  }

  @Test
  void rejectsCyclesAndDuplicateContributionInterfaceOwnership() {
    var cycle = validContracts();
    cycle.put(
        INTEGRATION_IDENTITY,
        contract(INTEGRATION_IDENTITY, Set.of(CONNECTION_BEHAVIOR), Integration.class));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IntegrationCapabilityCatalogFactory.create(cycle.values()))
        .withMessageContaining("cycle")
        .withMessageContaining(
            "INTEGRATION_IDENTITY -> CONNECTION_BEHAVIOR -> CONNECTION_SETTINGS -> CONNECTION_SCHEMA"
                + " -> INTEGRATION_IDENTITY");

    var duplicateOwnership = validContracts();
    duplicateOwnership.put(INGRESS_MAPPING, contract(INGRESS_MAPPING, Set.of(), Integration.class));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IntegrationCapabilityCatalogFactory.create(duplicateOwnership.values()))
        .withMessageContaining("owned")
        .withMessageContaining(Integration.class.getName());
  }

  private static EnumMap<IntegrationCapability, IntegrationCapabilityContract> validContracts() {
    var contracts =
        new EnumMap<IntegrationCapability, IntegrationCapabilityContract>(
            IntegrationCapability.class);
    var catalog = IntegrationCapabilityCatalog.standard();
    for (var capability : IntegrationCapability.values()) {
      contracts.put(capability, catalog.contractFor(capability));
    }
    return contracts;
  }

  private static IntegrationCapabilityContract contract(
      IntegrationCapability capability,
      Set<IntegrationCapability> prerequisites,
      Class<?> contributionInterface) {
    var stableKey = IntegrationCapabilityCatalog.standard().contractFor(capability).stableKey();
    return contract(capability, stableKey, prerequisites, contributionInterface);
  }

  private static IntegrationCapabilityContract contract(
      IntegrationCapability capability,
      String stableKey,
      Set<IntegrationCapability> prerequisites,
      Class<?> contributionInterface) {
    return new IntegrationCapabilityContract(
        capability, stableKey, prerequisites, Set.of(contributionInterface));
  }
}
