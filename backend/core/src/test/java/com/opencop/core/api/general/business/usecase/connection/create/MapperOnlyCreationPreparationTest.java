// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.create;

import static com.opencop.core.api.support.integration.TestIntegrationCatalogs.selection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.component.support.TestActors;
import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.business.usecase.connection.mapping.ConnectionMapperSelection;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.business.model.integration.connection.input.DeclaredConnectionInput;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.business.registry.integration.connection.input.ConnectionExtractorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionMappingContribution;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import com.opencop.core.sdk.integration.builtin.technical.extractor.tcp.NativePlainTcpExtractor;
import com.opencop.core.sdk.integration.builtin.technical.extractor.udp.NativeUdpListenerExtractor;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.integration.connection.input.ExtractorProtocol;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MapperOnlyCreationPreparationTest {

  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
  private static final Caller ACTOR = TestActors.of(USER_ID);
  private static final UUID MAPPER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final NativeConnectionSchema NATIVE =
      new NativeConnectionSchema(new NativeIntegration());

  private final IntegrationCatalogGateway catalog = mock(IntegrationCatalogGateway.class);
  private final MapperProfileService mappers = mock(MapperProfileService.class);
  private final MapperOnlyCreationPreparation preparation =
      new MapperOnlyCreationPreparation(
          selection(catalog, NativeConnectionSchema.V1_REFERENCE),
          new ConnectionMappingRegistry(
              List.of(NATIVE), List.of(new NativeConnectionMappingContribution(NATIVE))),
          new ConnectionMapperSelection(mappers, EgressMapperContributions.of(List.of())),
          new ConnectionExtractorRegistry(
              List.of(NATIVE),
              List.of(
                  new NativeUdpListenerExtractor(NATIVE), new NativePlainTcpExtractor(NATIVE))));

  @Test
  void rejectsAnIntegrationTheCatalogDoesNotOffer() {
    when(catalog.findSchemaOwner(any())).thenReturn(Optional.empty());

    assertRejected(ConnectionExceptionCodes.INTEGRATION_NOT_FOUND);
  }

  @Test
  void rejectsAnIntegrationAnAdministratorDeactivated() {
    when(catalog.findSchemaOwner(any()))
        .thenReturn(
            Optional.of(
                new IntegrationIdentity(NativeIntegration.NATIVE_ID, "Native", "Native", false)));

    assertRejected(ConnectionExceptionCodes.INTEGRATION_INACTIVE);
  }

  /** A missing mapper is the requester's to fix, so the rejection points at the field. */
  @Test
  void namesTheMapperFieldWhenNoMapperIsSelected() {
    assertThatThrownBy(
            () -> preparation.prepare(details(null), AccessMode.READ, ExtractorProtocol.UDP))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode())
                  .isEqualTo(ConnectionExceptionCodes.INGRESS_MAPPER_REQUIRED.code());
              assertThat(exception.getField())
                  .isEqualTo(ConnectionCreationDetails.INGRESS_MAPPER_FIELD);
            });
    verifyNoInteractions(catalog, mappers);
  }

  private void assertRejected(ConnectionExceptionCodes code) {
    assertThatThrownBy(
            () -> preparation.prepare(details(MAPPER_ID), AccessMode.READ, ExtractorProtocol.UDP))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(code.code()));
    verifyNoInteractions(mappers);
  }

  private static ConnectionCreationDetails details(UUID mapperId) {
    return new ConnectionCreationDetails(
        "Listener",
        "R",
        NativeConnectionSchema.V1_REFERENCE,
        NativeUdpListenerExtractor.EXTRACTOR_ID,
        new DeclaredConnectionInput(
            List.of(
                new ConnectionInputValue("host", "0.0.0.0"),
                new ConnectionInputValue("listenPort", "8087")),
            List.of()),
        mapperId,
        ACTOR,
        null);
  }
}
