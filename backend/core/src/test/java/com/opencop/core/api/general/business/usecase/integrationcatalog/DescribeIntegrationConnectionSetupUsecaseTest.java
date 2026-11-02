// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetupRejection;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetupUnavailableException;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.business.model.integrationcatalog.ProtocolConnectionSetup;
import com.opencop.core.api.general.business.usecase.integrationcatalog.setup.ConnectionSetupDescribers;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DescribeIntegrationConnectionSetupUsecaseTest {

  private static final UUID REGISTERED_ID = UUID.fromString("4b1d0b1e-3b3a-4f07-9c1e-9a0f0c2b7d11");
  private static final UUID UNREGISTERED_ID =
      UUID.fromString("6d3f2d30-5d5c-4129-9e30-1c2f2e4d9f33");
  private static final int SCHEMA_VERSION = 2;
  private static final IntegrationConnectionSchemaKey KEY =
      new IntegrationConnectionSchemaKey(REGISTERED_ID, SCHEMA_VERSION);

  private static final IntegrationConnectionSchema SCHEMA =
      new TestConnectionSchema(
          REGISTERED_ID, SCHEMA_VERSION, "Name", "Name", Set.of(TransportProtocol.UDP));
  private static final ProtocolConnectionSetup UDP =
      new ProtocolConnectionSetup.Udp(
          List.of(
              new ExtractorDescriptor(
                  UUID.fromString("00000000-0000-0000-0000-0000000000e1"),
                  "Datagram",
                  List.of(StandardExtractorField.HOST.declare(true)))),
          MapperDeclaration.openMapper(),
          List.of(AccessMode.READ));

  private final ConnectionSetupDescribers describers = mock(ConnectionSetupDescribers.class);

  @Test
  void describesWhatTheDescribersReturnForTheRegisteredSchema() {
    when(describers.describe(SCHEMA)).thenReturn(List.of(UDP));

    var setup = usecase(catalogWith(REGISTERED_ID, true)).describe(REGISTERED_ID, SCHEMA_VERSION);

    assertThat(setup.schema()).isEqualTo(KEY);
    assertThat(setup.protocols()).containsExactly(UDP);
  }

  @Test
  void readsTheCatalogWithoutMutatingIt() {
    when(describers.describe(SCHEMA)).thenReturn(List.of(UDP));
    var gateway = catalogWith(REGISTERED_ID, true);

    usecase(gateway).describe(REGISTERED_ID, SCHEMA_VERSION);

    // The single verified read plus no-more-interactions is the read-only proof: any mutating
    // call the use case made would fail here.
    verify(gateway).findSchemaOwner(KEY);
    verifyNoMoreInteractions(gateway);
  }

  @Test
  void rejectsAReferenceTheCatalogDoesNotCarry() {
    assertRejects(
        emptyCatalog(), REGISTERED_ID, SCHEMA_VERSION, IntegrationConnectionSetupRejection.UNKNOWN);
  }

  @Test
  void answersAnUnusableSchemaVersionAsUnknownWithoutConsultingTheCatalog() {
    var gateway = catalogWith(REGISTERED_ID, true);

    assertRejects(gateway, REGISTERED_ID, 0, IntegrationConnectionSetupRejection.UNKNOWN);
    verifyNoInteractions(gateway);
  }

  @Test
  void rejectsADeactivatedIntegration() {
    assertRejects(
        catalogWith(REGISTERED_ID, false),
        REGISTERED_ID,
        SCHEMA_VERSION,
        IntegrationConnectionSetupRejection.INACTIVE);
  }

  @Test
  void distinguishesAnUnavailableIntegrationFromAnUnknownOne() {
    assertRejects(
        catalogWith(UNREGISTERED_ID, true),
        UNREGISTERED_ID,
        SCHEMA_VERSION,
        IntegrationConnectionSetupRejection.UNAVAILABLE);
    verifyNoInteractions(describers);
  }

  @Test
  void saysExplicitlyThatAnIntegrationOfferingNoTransportHasNoConnectionSetupHere() {
    when(describers.describe(SCHEMA)).thenReturn(List.of());

    assertRejects(
        catalogWith(REGISTERED_ID, true),
        REGISTERED_ID,
        SCHEMA_VERSION,
        IntegrationConnectionSetupRejection.PROTOCOL_UNSUPPORTED);
  }

  private void assertRejects(
      IntegrationCatalogGateway gateway,
      UUID integrationId,
      int schemaVersion,
      IntegrationConnectionSetupRejection expected) {
    assertThatThrownBy(() -> usecase(gateway).describe(integrationId, schemaVersion))
        .isInstanceOfSatisfying(
            IntegrationConnectionSetupUnavailableException.class,
            exception -> assertThat(exception.reason()).isEqualTo(expected));
  }

  private DescribeIntegrationConnectionSetupUsecase usecase(IntegrationCatalogGateway gateway) {
    return new DescribeIntegrationConnectionSetupUsecase(gateway, describers, List.of(SCHEMA));
  }

  private static IntegrationCatalogGateway catalogWith(UUID id, boolean active) {
    var gateway = emptyCatalog();
    when(gateway.findSchemaOwner(new IntegrationConnectionSchemaKey(id, SCHEMA_VERSION)))
        .thenReturn(Optional.of(new IntegrationIdentity(id, "Name", "Description", active)));
    return gateway;
  }

  /** A catalog carrying no schema version, so every lookup is an unknown reference. */
  private static IntegrationCatalogGateway emptyCatalog() {
    var gateway = mock(IntegrationCatalogGateway.class);
    when(gateway.findSchemaOwner(any())).thenReturn(Optional.empty());
    return gateway;
  }
}
