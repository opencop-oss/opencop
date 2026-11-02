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

import java.util.Set;
import org.junit.jupiter.api.Test;

class IntegrationCapabilityPolicyTest {

  @Test
  void resolvesDeclaredSupportPrerequisitesAndExplicitRejection() {
    var policy =
        IntegrationCapabilityPolicy.resolve(
            Set.of(CONNECTION_INPUT),
            Set.of(
                INGRESS_MAPPING,
                EGRESS_MAPPING,
                CONNECTION_SETTINGS,
                CONNECTION_BEHAVIOR,
                CONNECTION_MAPPING,
                GEOCODING,
                CONNECTION_EXTENSION));

    assertThat(policy.declaredSupported()).containsExactly(CONNECTION_INPUT);
    assertThat(policy.effectiveSupported())
        .containsExactlyInAnyOrder(CONNECTION_INPUT, CONNECTION_SCHEMA, INTEGRATION_IDENTITY);
    assertThat(policy.rejected())
        .containsExactlyInAnyOrder(
            INGRESS_MAPPING,
            EGRESS_MAPPING,
            CONNECTION_SETTINGS,
            CONNECTION_BEHAVIOR,
            CONNECTION_MAPPING,
            GEOCODING,
            CONNECTION_EXTENSION);
    assertThat(policy.declaredSupported()).isUnmodifiable();
    assertThat(policy.effectiveSupported()).isUnmodifiable();
    assertThat(policy.rejected()).isUnmodifiable();
  }

  @Test
  void rejectsOverlapOmissionAndAnExplicitlyRejectedPrerequisite() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                IntegrationCapabilityPolicy.resolve(
                    Set.of(INGRESS_MAPPING),
                    Set.of(
                        INTEGRATION_IDENTITY,
                        INGRESS_MAPPING,
                        EGRESS_MAPPING,
                        CONNECTION_SETTINGS,
                        CONNECTION_BEHAVIOR,
                        CONNECTION_INPUT,
                        CONNECTION_MAPPING)))
        .withMessageContaining("overlap");

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                IntegrationCapabilityPolicy.resolve(
                    Set.of(INGRESS_MAPPING), Set.of(INTEGRATION_IDENTITY, CONNECTION_BEHAVIOR)))
        .withMessageContaining("classified")
        .withMessageContaining(CONNECTION_INPUT.name());

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                IntegrationCapabilityPolicy.resolve(
                    Set.of(CONNECTION_INPUT),
                    Set.of(
                        INTEGRATION_IDENTITY,
                        INGRESS_MAPPING,
                        EGRESS_MAPPING,
                        CONNECTION_SETTINGS,
                        CONNECTION_BEHAVIOR,
                        CONNECTION_MAPPING)))
        .withMessageContaining("overlap")
        .withMessageContaining(INTEGRATION_IDENTITY.name());
  }
}
