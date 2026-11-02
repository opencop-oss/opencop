// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogEntry;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogSnapshot;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.business.model.integrationcatalog.UnknownTransportProtocolException;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscoverIntegrationsUsecaseTest {

  private static final UUID TCP_ID = UUID.fromString("8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20");
  private static final UUID UDP_ID = UUID.fromString("9b2e1d5f-3e4c-4b6d-8f70-6c7d8e9f0a31");
  private static final UUID UNPROVIDED_ID = UUID.fromString("7c3d2e6a-4f5b-4c7d-9e80-5a6b7c8d9e42");

  private final IntegrationCatalogGateway catalog = mock(IntegrationCatalogGateway.class);

  @Test
  void joinsCatalogVersionsAgainstTheConnectionSchemasLoadedInThisRuntime() {
    when(catalog.findAll())
        .thenReturn(
            snapshot(
                List.of(
                    integration(TCP_ID, "Streaming feed"), integration(UNPROVIDED_ID, "Retired")),
                List.of(key(TCP_ID, 1), key(UNPROVIDED_ID, 1))));

    assertThat(usecase(tcpSchema()).discover(null))
        .extracting(IntegrationCatalogEntry::integrationId, IntegrationCatalogEntry::available)
        .containsExactly(tuple(UNPROVIDED_ID, false), tuple(TCP_ID, true));
  }

  @Test
  void readsDeclaredProtocolsFromTheLoadedSchemaRatherThanTheCatalog() {
    when(catalog.findAll())
        .thenReturn(
            snapshot(
                List.of(integration(TCP_ID, "Streaming feed"), integration(UDP_ID, "Datagram")),
                List.of(key(TCP_ID, 1), key(UDP_ID, 1))));

    assertThat(usecase(tcpSchema(), udpSchema()).discover("TCP"))
        .extracting(IntegrationCatalogEntry::integrationId)
        .containsExactly(TCP_ID);
  }

  @Test
  void readsTheCatalogOnceAndTouchesNoOtherGatewayOperation() {
    when(catalog.findAll())
        .thenReturn(
            snapshot(List.of(integration(TCP_ID, "Streaming feed")), List.of(key(TCP_ID, 1))));

    usecase(tcpSchema()).discover(null);

    verify(catalog).findAll();
    verifyNoMoreInteractions(catalog);
  }

  /**
   * A version declaring nothing is still provided, so it stays available while matching no filter.
   * It also pins where display metadata comes from: the catalog row, not the loaded schema, so a
   * plugin's own wording reaches the response only once the synchronizer has refreshed the catalog.
   */
  @Test
  void aLoadedSchemaDeclaringNoProtocolsStaysAvailableButMatchesNoFilter() {
    when(catalog.findAll())
        .thenReturn(snapshot(List.of(integration(TCP_ID, "Silent")), List.of(key(TCP_ID, 1))));
    var usecase =
        usecase(new TestConnectionSchema(TCP_ID, 1, "Silent", "Declares nothing", Set.of()));

    assertThat(usecase.discover(null))
        .singleElement()
        .extracting(
            IntegrationCatalogEntry::integrationId,
            IntegrationCatalogEntry::available,
            IntegrationCatalogEntry::name,
            IntegrationCatalogEntry::description)
        .containsExactly(TCP_ID, true, "Silent", "Example integration");
    assertThat(usecase.discover("TCP")).isEmpty();
  }

  @Test
  void anUnknownProtocolIsRejectedBeforeTheCatalogIsRead() {
    assertThatThrownBy(() -> usecase(tcpSchema()).discover("SCTP"))
        .isInstanceOf(UnknownTransportProtocolException.class);

    // The point of the name: a caller error must not reach the database at all.
    verifyNoInteractions(catalog);
  }

  @Test
  void aBlankProtocolIsRejectedBeforeTheCatalogIsReadRatherThanTreatedAsNoFilter() {
    assertThatThrownBy(() -> usecase(tcpSchema()).discover("   "))
        .isInstanceOf(UnknownTransportProtocolException.class);

    verifyNoInteractions(catalog);
  }

  @Test
  void refusesToAnswerWhenTwoLoadedSchemasClaimOneCatalogVersion() {
    assertThatThrownBy(() -> usecase(tcpSchema(), tcpSchema()).discover(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Two loaded connection schemas claim one catalog version");
  }

  private DiscoverIntegrationsUsecase usecase(IntegrationConnectionSchema... loaded) {
    return new DiscoverIntegrationsUsecase(catalog, List.of(loaded));
  }

  private static IntegrationCatalogSnapshot snapshot(
      List<IntegrationIdentity> integrations, List<IntegrationConnectionSchemaKey> schemas) {
    return new IntegrationCatalogSnapshot(integrations, schemas);
  }

  private static IntegrationConnectionSchema tcpSchema() {
    return new TestConnectionSchema(
        TCP_ID, 1, "Streaming feed", "Example integration", Set.of(TransportProtocol.TCP));
  }

  private static IntegrationConnectionSchema udpSchema() {
    return new TestConnectionSchema(
        UDP_ID, 1, "Datagram", "Example integration", Set.of(TransportProtocol.UDP));
  }

  private static IntegrationIdentity integration(UUID id, String name) {
    return new IntegrationIdentity(id, name, "Example integration", true);
  }

  private static IntegrationConnectionSchemaKey key(UUID integrationId, int schemaVersion) {
    return new IntegrationConnectionSchemaKey(integrationId, schemaVersion);
  }
}
