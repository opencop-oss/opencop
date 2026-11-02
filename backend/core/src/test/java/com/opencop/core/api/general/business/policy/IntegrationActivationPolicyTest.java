// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationInUseException;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationNotDeactivatableException;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntegrationActivationPolicyTest {

  @Test
  void blockersAreSortedByNameThenId() {
    var alphaFirst = connection(UUID.fromString("10000000-0000-0000-0000-000000000001"), "Alpha");
    var alphaSecond = connection(UUID.fromString("20000000-0000-0000-0000-000000000002"), "Alpha");
    var zulu = connection(UUID.fromString("30000000-0000-0000-0000-000000000003"), "Zulu");

    assertThatThrownBy(
            () -> IntegrationActivationPolicy.requireUnused(List.of(zulu, alphaSecond, alphaFirst)))
        .isInstanceOfSatisfying(
            IntegrationInUseException.class,
            exception ->
                assertThat(exception.connections()).containsExactly(alphaFirst, alphaSecond, zulu));
  }

  @Test
  void refusesToDeactivateTheNativeIntegration() {
    assertThatThrownBy(
            () -> IntegrationActivationPolicy.requireDeactivatable(NativeIntegration.NATIVE_ID))
        .isInstanceOf(IntegrationNotDeactivatableException.class)
        .hasMessageContaining(NativeIntegration.NATIVE_ID.toString());
  }

  @Test
  void letsAPluginIntegrationBeDeactivated() {
    assertThatNoException()
        .isThrownBy(() -> IntegrationActivationPolicy.requireDeactivatable(UUID.randomUUID()));
  }

  private static ConnectionMetadata connection(UUID id, String name) {
    return new ConnectionMetadata(
        id, name, UUID.randomUUID(), TransportKind.TCP, AccessMode.READ, true, null, true, null);
  }
}
