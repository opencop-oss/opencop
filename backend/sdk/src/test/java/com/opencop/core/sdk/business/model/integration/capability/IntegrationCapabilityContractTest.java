// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.capability;

import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INGRESS_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INTEGRATION_IDENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IntegrationCapabilityContractTest {

  private static final String STABLE_KEY = "integration-identity";

  @Test
  void defensivelyCopiesItsDirectPrerequisitesAndContributionInterfaces() {
    var prerequisites = new HashSet<IntegrationCapability>();
    var contributionInterfaces = new HashSet<Class<?>>();
    contributionInterfaces.add(IntegrationConnectionSchema.class);

    var contract =
        new IntegrationCapabilityContract(
            INTEGRATION_IDENTITY, STABLE_KEY, prerequisites, contributionInterfaces);
    prerequisites.add(INGRESS_MAPPING);
    contributionInterfaces.add(PayloadIngressMapper.class);

    assertThat(contract.capability()).isEqualTo(INTEGRATION_IDENTITY);
    assertThat(contract.stableKey()).isEqualTo(STABLE_KEY);
    assertThat(contract.prerequisites()).isEmpty();
    assertThat(contract.prerequisites()).isUnmodifiable();
    assertThat(contract.contributionInterfaces())
        .containsExactly(IntegrationConnectionSchema.class)
        .isUnmodifiable();
  }

  @Test
  void rejectsIncompleteOrInvalidLocalDefinitions() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new IntegrationCapabilityContract(
                    null, STABLE_KEY, Set.of(), Set.of(IntegrationConnectionSchema.class)))
        .withMessageContaining("Capability");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new IntegrationCapabilityContract(
                    INTEGRATION_IDENTITY,
                    null,
                    Set.of(),
                    Set.of(IntegrationConnectionSchema.class)))
        .withMessageContaining("stable key");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new IntegrationCapabilityContract(
                    INTEGRATION_IDENTITY,
                    STABLE_KEY,
                    null,
                    Set.of(IntegrationConnectionSchema.class)));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new IntegrationCapabilityContract(
                    INTEGRATION_IDENTITY, STABLE_KEY, Set.of(), null));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new IntegrationCapabilityContract(
                    INTEGRATION_IDENTITY, STABLE_KEY, Set.of(), Set.of()))
        .withMessageContaining("contribution interface");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new IntegrationCapabilityContract(
                    INTEGRATION_IDENTITY,
                    STABLE_KEY,
                    Set.of(INTEGRATION_IDENTITY),
                    Set.of(IntegrationConnectionSchema.class)))
        .withMessageContaining("itself");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new IntegrationCapabilityContract(
                    INTEGRATION_IDENTITY, STABLE_KEY, Set.of(), Set.of(ConcreteContribution.class)))
        .withMessageContaining("interfaces");

    var nullPrerequisite = new HashSet<IntegrationCapability>();
    nullPrerequisite.add(null);
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new IntegrationCapabilityContract(
                    INTEGRATION_IDENTITY,
                    STABLE_KEY,
                    nullPrerequisite,
                    Set.of(IntegrationConnectionSchema.class)));

    var nullContributionInterface = new HashSet<Class<?>>();
    nullContributionInterface.add(null);
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new IntegrationCapabilityContract(
                    INTEGRATION_IDENTITY, STABLE_KEY, Set.of(), nullContributionInterface));
  }

  private static final class ConcreteContribution {}
}
