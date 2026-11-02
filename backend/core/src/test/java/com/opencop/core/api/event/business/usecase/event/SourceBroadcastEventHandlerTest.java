// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.business.policy.SourceBroadcastPolicy;
import com.opencop.core.api.event.ports.business.usecase.OpencopEventException;
import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.ports.gateway.job.JobPublisher;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.ports.usecase.identity.UserManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.model.job.GhostEvictionJobSubmission;
import com.opencop.core.sdk.model.provenance.FeatureProvenance;
import com.opencop.core.sdk.model.provenance.FeatureProvenance.FeatureSourceKind;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.ports.gateway.iam.SourceAccessRepository;
import com.opencop.core.sdk.technical.gateway.broker.kafka.ProvenanceHeaders;
import com.opencop.core.sdk.technical.gateway.db.NotFoundException;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.geojson.Feature;
import org.geojson.Point;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpStatus;

class SourceBroadcastEventHandlerTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440131");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440132");
  private static final String UUID_OBJECT_ID = "550e8400-e29b-41d4-a716-446655440133";
  private static final String NON_UUID_OBJECT_ID = "track-AC-7842";

  private MessageBrokerProducer<Feature> producer;
  private JobPublisher jobPublisher;
  private LayerManager<Layer, LayerMember> layerManager;
  private DataSourceRepository dataSourceRepository;
  private UserManager<InternalUser> userManager;
  private SourceAccessRepository sourceAccessRepository;
  private PostGisService postGisService;
  private SourceBroadcastEventHandler handler;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    producer = mock(MessageBrokerProducer.class);
    jobPublisher = mock(JobPublisher.class);
    layerManager = mock(LayerManager.class);
    when(layerManager.get(any(UUID.class)))
        .thenAnswer((Answer<Layer>) invocation -> activeLayer(invocation.getArgument(0)));
    dataSourceRepository = mock(DataSourceRepository.class);
    userManager = mock(UserManager.class);
    sourceAccessRepository = mock(SourceAccessRepository.class);
    postGisService = mock(PostGisService.class);
    handler =
        new SourceBroadcastEventHandler(
            new BroadcastEventPublisher(producer, userManager, postGisService),
            new SourceBroadcastPolicy(sourceAccessRepository),
            new ResourceLifecycleGuard(dataSourceRepository, layerManager),
            jobPublisher);
  }

  @Test
  void patch_whenDirectSourceAdminAndUuidObjectId_publishes() throws Exception {
    stubExistingFeature(UUID_OBJECT_ID);
    stubSourceRole(ServerRole.ADMIN);
    when(userManager.get(USER_ID)).thenReturn(internalUser());

    var patched =
        handler.patch(USER_ID, SOURCE_ID, UUID_OBJECT_ID, patchRequest(UUID_OBJECT_ID, 7, 8));

    verify(producer).send(any(), any());
    assertEquals(new Point(7, 8), patched.getGeometry());
  }

  @Test
  void patch_whenDirectSourceOwnerAndNonUuidObjectId_publishesWithStringHeader() throws Exception {
    stubExistingFeature(NON_UUID_OBJECT_ID);
    stubSourceRole(ServerRole.OWNER);
    when(userManager.get(USER_ID)).thenReturn(internalUser());

    handler.patch(USER_ID, SOURCE_ID, NON_UUID_OBJECT_ID, patchRequest(NON_UUID_OBJECT_ID, 7, 8));

    var headersCaptor = ArgumentCaptor.forClass((Class<Map<String, String>>) (Class<?>) Map.class);
    verify(producer).send(any(), headersCaptor.capture());
    assertEquals(
        NON_UUID_OBJECT_ID, headersCaptor.getValue().get(OpencopKafkaHeaders.OBJECT_ID.getName()));
    assertEquals(
        SOURCE_ID.toString(),
        headersCaptor.getValue().get(OpencopKafkaHeaders.SOURCE_ID.getName()));
  }

  @Test
  void patch_attachesDataSourceProvenance() throws Exception {
    stubExistingFeature(UUID_OBJECT_ID);
    stubSourceRole(ServerRole.ADMIN);
    when(userManager.get(USER_ID)).thenReturn(internalUser());

    handler.patch(USER_ID, SOURCE_ID, UUID_OBJECT_ID, patchRequest(UUID_OBJECT_ID, 7, 8));

    var provenance = publishedProvenance();
    assertEquals(FeatureSourceKind.DATA_SOURCE, provenance.sourceKind());
    assertNotNull(provenance.sourceReceivedAt());
    assertNotNull(provenance.ingressId());
  }

  @Test
  void delete_attachesDataSourceProvenanceOnTheStalePublication() throws Exception {
    stubExistingFeature(NON_UUID_OBJECT_ID);
    stubSourceRole(ServerRole.ADMIN);

    handler.delete(USER_ID, SOURCE_ID, NON_UUID_OBJECT_ID);

    var provenance = publishedProvenance();
    assertEquals(FeatureSourceKind.DATA_SOURCE, provenance.sourceKind());
    assertNotNull(provenance.sourceReceivedAt());
  }

  @SuppressWarnings("unchecked")
  private FeatureProvenance publishedProvenance() {
    var headersCaptor = ArgumentCaptor.forClass((Class<Map<String, String>>) (Class<?>) Map.class);
    verify(producer).send(any(), headersCaptor.capture());
    var kafkaHeaders = new RecordHeaders();
    headersCaptor
        .getValue()
        .forEach((name, value) -> kafkaHeaders.add(name, value.getBytes(UTF_8)));
    return ProvenanceHeaders.from(kafkaHeaders);
  }

  @Test
  void patch_whenDirectSourceViewer_isUnauthorized() throws Exception {
    stubExistingFeature(UUID_OBJECT_ID);
    stubSourceRole(ServerRole.VIEWER);

    var ex =
        assertThrows(
            OpencopEventException.class,
            () ->
                handler.patch(
                    USER_ID, SOURCE_ID, UUID_OBJECT_ID, patchRequest(UUID_OBJECT_ID, 7, 8)));

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void patch_whenNoSourceRole_isUnauthorized() throws Exception {
    stubExistingFeature(UUID_OBJECT_ID);
    when(sourceAccessRepository.userRole(USER_ID, SOURCE_ID)).thenReturn(null);

    var ex =
        assertThrows(
            OpencopEventException.class,
            () ->
                handler.patch(
                    USER_ID, SOURCE_ID, UUID_OBJECT_ID, patchRequest(UUID_OBJECT_ID, 7, 8)));

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void delete_whenDirectSourceAdminAndNonUuidObjectId_publishesStaleFeature() throws Exception {
    stubExistingFeature(NON_UUID_OBJECT_ID);
    stubSourceRole(ServerRole.ADMIN);

    handler.delete(USER_ID, SOURCE_ID, NON_UUID_OBJECT_ID);

    var featureCaptor = ArgumentCaptor.forClass(Feature.class);
    var headersCaptor = ArgumentCaptor.forClass((Class<Map<String, String>>) (Class<?>) Map.class);
    verify(producer, times(1)).send(featureCaptor.capture(), headersCaptor.capture());

    var published = OpenCopPropertiesParser.readFromFeature(featureCaptor.getValue());
    assertEquals(NON_UUID_OBJECT_ID, published.getObjectId());
    assertEquals(
        NON_UUID_OBJECT_ID, headersCaptor.getValue().get(OpencopKafkaHeaders.OBJECT_ID.getName()));
  }

  @Test
  void delete_whenDirectSourceViewer_isUnauthorized() throws Exception {
    stubSourceRole(ServerRole.VIEWER);

    var ex =
        assertThrows(
            OpencopEventException.class, () -> handler.delete(USER_ID, SOURCE_ID, UUID_OBJECT_ID));

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
    verify(postGisService, never()).getLatestFeature(any(), any());
    verify(producer, never()).send(any(), any());
    verify(jobPublisher, never()).publishNow(any());
  }

  @Test
  void delete_whenLatestEventMissingFromPostGis_publishesGhostEvictionAndSucceeds()
      throws Exception {
    stubSourceRole(ServerRole.ADMIN);
    when(postGisService.getLatestFeature(SOURCE_ID, UUID_OBJECT_ID))
        .thenThrow(new NotFoundException("missing"));

    handler.delete(USER_ID, SOURCE_ID, UUID_OBJECT_ID);

    var captor = ArgumentCaptor.forClass(GhostEvictionJobSubmission.class);
    verify(jobPublisher).publishNow(captor.capture());
    assertEquals(SOURCE_ID, captor.getValue().sourceId());
    assertEquals(UUID_OBJECT_ID, captor.getValue().objectId());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void delete_whenGhostEvictionPublishFails_propagatesException() throws Exception {
    stubSourceRole(ServerRole.ADMIN);
    when(postGisService.getLatestFeature(SOURCE_ID, UUID_OBJECT_ID))
        .thenThrow(new NotFoundException("missing"));
    var brokerFailure = new RuntimeException("broker unavailable");
    doThrow(brokerFailure).when(jobPublisher).publishNow(any());

    var thrown =
        assertThrows(
            RuntimeException.class, () -> handler.delete(USER_ID, SOURCE_ID, UUID_OBJECT_ID));

    assertSame(brokerFailure, thrown);
    verify(producer, never()).send(any(), any());
  }

  private void stubSourceRole(ServerRole role) {
    when(sourceAccessRepository.userRole(USER_ID, SOURCE_ID)).thenReturn(role);
  }

  private void stubExistingFeature(String objectId) throws Exception {
    when(postGisService.getLatestFeature(SOURCE_ID, objectId))
        .thenReturn(featureWith(objectId, new Point(9, 10), staleTimestamp()));
  }

  private Feature patchRequest(String objectId, double x, double y) {
    return featureWith(objectId, new Point(x, y), freshTimestamp());
  }

  private Feature featureWith(String objectId, Point geometry, Timestampz timestampz) {
    var properties = new OpenCopProperties();
    properties.setSource(SOURCE_ID);
    properties.setObjectId(objectId);
    properties.setTimestampz(timestampz);
    return OpenCopPropertiesParser.writeToFeature(properties, geometry);
  }

  private static Timestampz freshTimestamp() {
    return new Timestampz(
        0L,
        Instant.now().minusSeconds(120).toEpochMilli(),
        Instant.now().plusSeconds(60).toEpochMilli());
  }

  private static Timestampz staleTimestamp() {
    return new Timestampz(
        Instant.now().minusSeconds(180).toEpochMilli(),
        Instant.now().minusSeconds(180).toEpochMilli(),
        Instant.now().plusSeconds(60).toEpochMilli());
  }

  private static InternalUser internalUser() {
    return new InternalUser(
        USER_ID,
        "idp-user",
        "user",
        "issuer",
        "user",
        "user@example.com",
        "Jane",
        "Doe",
        "Jane Doe",
        true,
        false,
        0,
        0,
        Instant.now(),
        Instant.now(),
        null);
  }

  private static Layer activeLayer(UUID id) {
    var layer = new Layer();
    layer.setId(id);
    return layer;
  }
}
