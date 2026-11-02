// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.profile.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.api.general.business.model.profile.MapperCatalogProfile;
import com.opencop.core.api.general.ports.gateway.profile.MapperCatalogGateway;
import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.egress.IntegrationEgressMappers;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class PluginMapperCatalogReconciliationUsecaseTest {

  private static final Integration TEST_INTEGRATION =
      new TestIntegration(
          UUID.fromString("00000000-0000-4000-8000-0000000000aa"),
          "Test integration",
          "Test integration");

  private static final UUID NEW_MAPPER_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID OTHER_INTEGRATION_ID =
      UUID.fromString("00000000-0000-4000-8000-0000000000bb");

  private static final UUID RETURNING_MAPPER_ID =
      UUID.fromString("20000000-0000-4000-8000-000000000001");
  private static final UUID METADATA_MAPPER_ID =
      UUID.fromString("30000000-0000-4000-8000-000000000001");
  private static final UUID MISSING_MAPPER_ID =
      UUID.fromString("40000000-0000-4000-8000-000000000001");

  private MapperProfileService profiles;
  private MapperCatalogGateway catalog;

  @BeforeEach
  void setUp() {
    profiles = mock(MapperProfileService.class);
    catalog = mock(MapperCatalogGateway.class);
  }

  @Test
  void loadsCatalogAndAppliesTheCompletePlanInWriteOrder() {
    // Archived while another integration contributed it, and contributed again by this one.
    var returning = catalogMapper(RETURNING_MAPPER_ID, "COT", false, OTHER_INTEGRATION_ID, 3, true);
    var stalePlugin =
        catalogMapper(METADATA_MAPPER_ID, "Old name", false, TEST_INTEGRATION.id(), 2, false);
    var missingPlugin =
        catalogMapper(MISSING_MAPPER_ID, "Missing mapper", false, TEST_INTEGRATION.id(), 1, false);
    when(catalog.loadCatalog()).thenReturn(List.of(missingPlugin, stalePlugin, returning));
    var usecase =
        newUsecase(
            List.of(
                new TestDecoder(NEW_MAPPER_ID, "New mapper", null, 1),
                new TestDecoder(RETURNING_MAPPER_ID, "COT", null, 3),
                new TestDecoder(METADATA_MAPPER_ID, "Updated name", "Updated description", 5)));

    usecase.reconcile();

    var writes = inOrder(catalog, profiles);
    writes.verify(catalog).loadCatalog();
    writes
        .verify(profiles)
        .createMapperProfile(
            MapperProfile.builder()
                .withId(NEW_MAPPER_ID)
                .withName("New mapper")
                .withEditable(false)
                .withVersion(1)
                .withArchived(false)
                .build(),
            null,
            null,
            null);
    writes
        .verify(catalog)
        .updateMetadata(METADATA_MAPPER_ID, "Updated name", "Updated description");
    writes.verify(catalog).assignIntegration(NEW_MAPPER_ID, TEST_INTEGRATION.id());
    writes.verify(catalog).assignIntegration(RETURNING_MAPPER_ID, TEST_INTEGRATION.id());
    writes.verify(catalog).reconcileAvailability(RETURNING_MAPPER_ID, false);
    writes.verify(catalog).reconcileAvailability(MISSING_MAPPER_ID, true);
    // The egress pass reloads the catalog and has nothing to reconcile.
    writes.verify(catalog).loadCatalog();
    writes.verifyNoMoreInteractions();
  }

  @Test
  void reportsARejectionBeforeLeavingTheCatalogUnchanged() {
    var mapperId = UUID.randomUUID();
    var editableOwner = catalogMapper(mapperId, "Editable owner", true, null, 1, false);
    when(catalog.loadCatalog()).thenReturn(List.of(editableOwner));
    var usecase = newUsecase(List.of(new TestDecoder(mapperId, "Claim", null, 1)));
    var logger = (Logger) LoggerFactory.getLogger(PluginMapperCatalogReconciliationUsecase.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);

    try {
      usecase.reconcile();

      assertThat(appender.list)
          .singleElement()
          .extracting(ILoggingEvent::getFormattedMessage)
          .asString()
          .contains(
              "reason=editable-uuid-owner",
              "mapperId=" + mapperId,
              "mapperName='Claim'",
              "ownerMapperId=" + mapperId);
      verifyNoInteractions(profiles);
      verify(catalog, never()).updateMetadata(any(), any(), any());
      verify(catalog, never()).reconcileAvailability(any(), anyBoolean());
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void propagatesAWriteFailureAndStopsApplyingThePlan() {
    var first = new TestDecoder(NEW_MAPPER_ID, "First mapper", null, 1);
    var second =
        new TestDecoder(
            UUID.fromString("00000000-0000-4000-8000-000000000002"), "Second mapper", null, 1);
    var failure = new IllegalStateException("write failed");
    when(profiles.createMapperProfile(any(), isNull(), isNull(), isNull())).thenThrow(failure);
    var usecase = newUsecase(List.of(second, first));

    assertThatThrownBy(usecase::reconcile).isSameAs(failure);

    verify(catalog).loadCatalog();
    verify(profiles, times(1)).createMapperProfile(any(), isNull(), isNull(), isNull());
    verify(catalog, never()).updateMetadata(any(), any(), any());
    verify(catalog, never()).reconcileAvailability(any(), anyBoolean());
  }

  @Test
  void registersAnEgressContributionAsAnEgressMapperOwnedByItsIntegration() {
    var egressId = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    var egressMapper = mock(PayloadEgressMapper.class);
    when(egressMapper.id()).thenReturn(egressId);
    when(egressMapper.name()).thenReturn("COT Egress");
    when(egressMapper.version()).thenReturn(1);
    var usecase =
        new PluginMapperCatalogReconciliationUsecase(
            IngressMapperContributions.of(List.of()),
            EgressMapperContributions.of(
                List.of(new IntegrationEgressMappers(TEST_INTEGRATION, List.of(egressMapper)))),
            profiles,
            catalog);

    usecase.reconcile();

    var inserted = ArgumentCaptor.forClass(MapperProfile.class);
    verify(profiles).createMapperProfile(inserted.capture(), isNull(), isNull(), isNull());
    assertThat(inserted.getValue().id()).isEqualTo(egressId);
    assertThat(inserted.getValue().qualifier()).isEqualTo(MapperQualifier.EGRESS);
    verify(catalog).assignIntegration(egressId, TEST_INTEGRATION.id());
  }

  private PluginMapperCatalogReconciliationUsecase newUsecase(List<TestDecoder> decoders) {
    return new PluginMapperCatalogReconciliationUsecase(
        IngressMapperContributions.of(
            List.of(new IntegrationIngressMappers(TEST_INTEGRATION, List.copyOf(decoders)))),
        EgressMapperContributions.of(List.of()),
        profiles,
        catalog);
  }

  private static MapperCatalogProfile catalogMapper(
      UUID mapperId,
      String name,
      boolean editable,
      UUID integrationId,
      int version,
      boolean archived) {
    return new MapperCatalogProfile(
        mapperId,
        version,
        name,
        null,
        editable,
        false,
        archived,
        MapperQualifier.INGRESS,
        integrationId);
  }

  private record TestDecoder(UUID id, String name, String description, int version)
      implements PayloadIngressMapper<Feature> {

    @Override
    public List<Feature> map(UUID id, MappingProperties properties, byte[] input) {
      return List.of();
    }
  }
}
