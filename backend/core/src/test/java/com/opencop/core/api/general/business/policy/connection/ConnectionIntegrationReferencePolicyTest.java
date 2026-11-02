// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingPair;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectionIntegrationReferencePolicyTest {
  private static final IntegrationConnectionSchemaReference INTEGRATION =
      new IntegrationConnectionSchemaReference(UUID.randomUUID(), 2);
  private static final String PROTOCOL = "UDP";

  @Test
  void requiresTheIntegrationToDeclareAMappingForTheTransport() {
    var declaration =
        MapperFramingDeclaration.of(new MapperFramingPair(UUID.randomUUID(), UUID.randomUUID()));

    assertThat(
            ConnectionIntegrationReferencePolicy.requireDeclaration(
                Optional.of(declaration), INTEGRATION, PROTOCOL))
        .isEqualTo(declaration);
    assertThatThrownBy(
            () ->
                ConnectionIntegrationReferencePolicy.requireDeclaration(
                    Optional.empty(), INTEGRATION, PROTOCOL))
        .isInstanceOfSatisfying(
            ConnectionManagementException.class,
            exception ->
                assertThat(exception.getCode()).isEqualTo("INTEGRATION_DECLARATION_NOT_FOUND"));
  }

  @Test
  void rejectsASchemaVersionThisRuntimeRegistersNoImplementationFor() {
    assertThatThrownBy(
            () -> ConnectionIntegrationReferencePolicy.assertAvailable(INTEGRATION, false))
        .isInstanceOfSatisfying(
            ConnectionManagementException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("INTEGRATION_UNAVAILABLE"));
  }

  @Test
  void acceptsASchemaVersionThisRuntimeRegisters() {
    assertThatCode(() -> ConnectionIntegrationReferencePolicy.assertAvailable(INTEGRATION, true))
        .doesNotThrowAnyException();
  }
}
