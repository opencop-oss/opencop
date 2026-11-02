// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.business.policy.LayerBroadcastPolicy;
import com.opencop.core.api.event.ports.business.usecase.OpencopEventException;
import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.ports.gateway.job.JobPublisher;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.api.iam.ports.usecase.identity.UserManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.model.iam.source.AccessVia;
import com.opencop.core.sdk.model.iam.source.LayerInfo;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.model.job.GhostEvictionJobSubmission;
import com.opencop.core.sdk.model.provenance.FeatureProvenance;
import com.opencop.core.sdk.model.provenance.FeatureProvenance.FeatureSourceKind;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.technical.gateway.broker.kafka.ProvenanceHeaders;
import com.opencop.core.sdk.technical.gateway.db.NotFoundException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders;
import java.time.Instant;
import java.util.List;
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

class LayerBroadcastEventHandlerTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440121");
  private static final UUID TARGET_LAYER = UUID.fromString("550e8400-e29b-41d4-a716-446655440122");
  private static final UUID OTHER_LAYER = UUID.fromString("550e8400-e29b-41d4-a716-446655440123");
  private static final UUID OBJECT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440124");
  private static final UUID OTHER_OBJECT_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440125");

  private MessageBrokerProducer<Feature> producer;
  private JobPublisher jobPublisher;
  private LayerManager<Layer, LayerMember> layerManager;
  private DataSourceRepository dataSourceRepository;
  private UserManager<InternalUser> userManager;
  private UserSpecificManager userSpecificManager;
  private PostGisService postGisService;
  private LayerBroadcastEventHandler handler;

  @BeforeEach
  void setUp() {
    producer = mock(MessageBrokerProducer.class);
    jobPublisher = mock(JobPublisher.class);
    layerManager = mock(LayerManager.class);
    when(layerManager.get(any(UUID.class)))
        .thenAnswer((Answer<Layer>) invocation -> activeLayer(invocation.getArgument(0)));
    dataSourceRepository = mock(DataSourceRepository.class);
    userManager = mock(UserManager.class);
    userSpecificManager = mock(UserSpecificManager.class);
    postGisService = mock(PostGisService.class);
    handler =
        new LayerBroadcastEventHandler(
            new BroadcastEventPublisher(producer, userManager, postGisService),
            new LayerBroadcastPolicy(userSpecificManager),
            new ResourceLifecycleGuard(dataSourceRepository, layerManager),
            jobPublisher);
  }

  @Test
  void create_whenFeatureHasNoProperties_throwsBadRequest() {
    var ex =
        assertThrows(
            OpencopEventException.class,
            () -> handler.create(USER_ID, TARGET_LAYER, new Feature()));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void create_whenFeatureSourceMissing_throwsBadRequest() {
    var ex =
        assertThrows(
            OpencopEventException.class,
            () -> handler.create(USER_ID, TARGET_LAYER, feature(null, OBJECT_ID.toString())));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void create_whenTimestampzMissing_throwsBadRequest() {
    var ex =
        assertThrows(
            OpencopEventException.class,
            () ->
                handler.create(
                    USER_ID,
                    TARGET_LAYER,
                    feature(TARGET_LAYER, OBJECT_ID.toString(), new Point(1, 2), null)));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void create_whenObjectIdMissing_generatesNewObjectId() throws Exception {
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    TARGET_LAYER,
                    "Ops",
                    LayerRole.EDITOR,
                    com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));
    when(userManager.get(USER_ID)).thenReturn(internalUser());

    var geometry = new Point(5, 6);
    var createdFeature =
        handler.create(
            USER_ID,
            TARGET_LAYER,
            feature(
                TARGET_LAYER,
                null,
                geometry,
                new Timestampz(
                    0L,
                    Instant.now().minusSeconds(120).toEpochMilli(),
                    Instant.now().plusSeconds(60).toEpochMilli())));

    var properties = OpenCopPropertiesParser.readFromFeature(createdFeature);
    assertNotNull(properties.getObjectId());
    assertEquals(TARGET_LAYER, properties.getSource());
    assertEquals(geometry, createdFeature.getGeometry());
  }

  @Test
  void create_whenStaleMissing_throwsBadRequest() {
    var ex =
        assertThrows(
            OpencopEventException.class,
            () ->
                handler.create(
                    USER_ID,
                    TARGET_LAYER,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        new Point(1, 2),
                        new Timestampz(0L, 0L, 0L))));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void create_whenStaleIsInPast_throwsBadRequest() {
    var ex =
        assertThrows(
            OpencopEventException.class,
            () ->
                handler.create(
                    USER_ID,
                    TARGET_LAYER,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        new Point(1, 2),
                        new Timestampz(0L, 0L, Instant.now().minusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertEquals("CORE-1003", ex.getCode());
    assertEquals("The object is stale and it cannot be created or modified", ex.getMessage());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void create_whenGeometryMissing_throwsBadRequest() {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                handler.create(
                    USER_ID,
                    TARGET_LAYER,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        null,
                        new Timestampz(
                            0L,
                            Instant.now().minusSeconds(120).toEpochMilli(),
                            Instant.now().plusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertEquals("Specified event geometry is missing", ex.getMessage());
    verifyNoInteractions(producer, userManager, userSpecificManager);
  }

  @Test
  void create_whenTargetLayerDoesNotMatchEventSource_throwsBadRequest() throws Exception {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                handler.create(
                    USER_ID,
                    TARGET_LAYER,
                    feature(
                        OTHER_LAYER,
                        OBJECT_ID.toString(),
                        new Point(1, 2),
                        new Timestampz(
                            0L,
                            Instant.now().minusSeconds(120).toEpochMilli(),
                            Instant.now().plusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertEquals(
        "Specified target layer does not match the event source specified", ex.getMessage());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void create_whenUserHasNoRoleOnTargetLayer_throwsUnauthorized() throws Exception {
    when(userSpecificManager.getUserLayers(USER_ID)).thenReturn(List.of());

    var ex =
        assertThrows(
            OpencopEventException.class,
            () ->
                handler.create(
                    USER_ID,
                    TARGET_LAYER,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        new Point(1, 2),
                        new Timestampz(
                            0L,
                            Instant.now().minusSeconds(120).toEpochMilli(),
                            Instant.now().plusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
    verify(userManager, never()).get(USER_ID);
  }

  @Test
  void create_whenUserIsOnlyMemberOnTargetLayer_throwsUnauthorized() throws Exception {
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    TARGET_LAYER,
                    "Ops",
                    LayerRole.MEMBER,
                    com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));

    var ex =
        assertThrows(
            OpencopEventException.class,
            () ->
                handler.create(
                    USER_ID,
                    TARGET_LAYER,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        new Point(1, 2),
                        new Timestampz(
                            0L,
                            Instant.now().minusSeconds(120).toEpochMilli(),
                            Instant.now().plusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
    verify(userManager, never()).get(USER_ID);
  }

  @Test
  void create_whenUserIsEditor_sendsGeneratedObjectIdAndGeometry() throws Exception {
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    TARGET_LAYER,
                    "Ops",
                    LayerRole.EDITOR,
                    com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));
    when(userManager.get(USER_ID)).thenReturn(internalUser());

    var event =
        feature(
            TARGET_LAYER,
            OBJECT_ID.toString(),
            null,
            new Timestampz(
                0L,
                Instant.now().minusSeconds(120).toEpochMilli(),
                Instant.now().plusSeconds(60).toEpochMilli()));
    var geometry = new Point(45.5, -73.6);
    event.setGeometry(geometry);

    Feature createdFeature = handler.create(USER_ID, TARGET_LAYER, event);

    var featureCaptor = ArgumentCaptor.forClass(Feature.class);
    var headersCaptor = ArgumentCaptor.forClass((Class<Map<String, String>>) (Class<?>) Map.class);
    verify(producer).send(featureCaptor.capture(), headersCaptor.capture());

    var returnedProperties = OpenCopPropertiesParser.readFromFeature(createdFeature);
    var properties = OpenCopPropertiesParser.readFromFeature(featureCaptor.getValue());
    assertNotNull(properties.getId());
    assertEquals(returnedProperties.getObjectId(), properties.getObjectId());
    assertEquals(TARGET_LAYER, properties.getSource());
    assertEquals(1, properties.getContacts().size());
    assertEquals(USER_ID, properties.getContacts().getFirst().getUserId());
    assertEquals(geometry, featureCaptor.getValue().getGeometry());
    assertEquals(geometry, createdFeature.getGeometry());

    assertEquals(
        returnedProperties.getObjectId(),
        headersCaptor.getValue().get(OpencopKafkaHeaders.OBJECT_ID.getName()));
    assertEquals(
        TARGET_LAYER.toString(),
        headersCaptor.getValue().get(OpencopKafkaHeaders.SOURCE_ID.getName()));
  }

  @Test
  void create_whenHydrated_updatesFeatureTimeToNow() throws Exception {
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    TARGET_LAYER,
                    "Ops",
                    LayerRole.EDITOR,
                    com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));
    when(userManager.get(USER_ID)).thenReturn(internalUser());

    var originalTime = Instant.parse("2026-04-12T09:00:00Z").toEpochMilli();
    var event =
        feature(
            TARGET_LAYER,
            OBJECT_ID.toString(),
            new Point(1, 2),
            new Timestampz(
                originalTime,
                Instant.now().minusSeconds(120).toEpochMilli(),
                Instant.now().plusSeconds(60).toEpochMilli()));

    var before = Instant.now().toEpochMilli();
    var createdFeature = handler.create(USER_ID, TARGET_LAYER, event);
    var after = Instant.now().toEpochMilli();

    var hydrated = OpenCopPropertiesParser.readFromFeature(createdFeature);
    assertNotNull(hydrated.getTimestampz());
    assertFalse(hydrated.getTimestampz().getTime() < before);
    assertFalse(hydrated.getTimestampz().getTime() > after);
    assertFalse(hydrated.getTimestampz().getTime() == originalTime);
  }

  @Test
  void create_whenLayerArchived_returnsConflictWithoutPublishing() throws Exception {
    when(layerManager.get(TARGET_LAYER)).thenReturn(archivedLayer(TARGET_LAYER));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                handler.create(
                    USER_ID,
                    TARGET_LAYER,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        new Point(1, 2),
                        new Timestampz(
                            0L,
                            Instant.now().minusSeconds(120).toEpochMilli(),
                            Instant.now().plusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
    verifyNoInteractions(userSpecificManager);
  }

  @Test
  void create_whenLayerLockedForMerge_returnsConflictWithoutPublishing() throws Exception {
    when(layerManager.get(TARGET_LAYER)).thenReturn(lockedLayer(TARGET_LAYER));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                handler.create(
                    USER_ID,
                    TARGET_LAYER,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        new Point(1, 2),
                        new Timestampz(
                            0L,
                            Instant.now().minusSeconds(120).toEpochMilli(),
                            Instant.now().plusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
    verifyNoInteractions(userSpecificManager);
  }

  @Test
  void patch_whenGeometryMissing_throwsBadRequest() throws Exception {
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    TARGET_LAYER,
                    "Ops",
                    LayerRole.EDITOR,
                    com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                handler.patch(
                    USER_ID,
                    TARGET_LAYER,
                    OBJECT_ID,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        null,
                        new Timestampz(
                            0L,
                            Instant.now().minusSeconds(120).toEpochMilli(),
                            Instant.now().plusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertEquals("Specified event geometry is missing", ex.getMessage());
    verify(producer, never()).send(any(), any());
    verify(userManager, never()).get(USER_ID);
  }

  @Test
  void patch_whenObjectIdDoesNotMatchPayload_throwsBadRequest() throws Exception {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                handler.patch(
                    USER_ID,
                    TARGET_LAYER,
                    OBJECT_ID,
                    feature(
                        TARGET_LAYER,
                        OTHER_OBJECT_ID.toString(),
                        new Point(1, 2),
                        new Timestampz(
                            0L,
                            Instant.now().minusSeconds(120).toEpochMilli(),
                            Instant.now().plusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertEquals("Specified objectId does not match the event objectId specified", ex.getMessage());
    verifyNoInteractions(producer, userManager, userSpecificManager);
  }

  @Test
  void patch_whenValid_sendsHydratedEventUsingPathObjectId() throws Exception {
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    TARGET_LAYER,
                    "Ops",
                    LayerRole.EDITOR,
                    com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));
    when(userManager.get(USER_ID)).thenReturn(internalUser());
    stubExistingFeature();

    var event =
        feature(
            TARGET_LAYER,
            OBJECT_ID.toString(),
            new Point(7, 8),
            new Timestampz(
                0L,
                Instant.now().minusSeconds(120).toEpochMilli(),
                Instant.now().plusSeconds(60).toEpochMilli()));
    var patchedFeature = handler.patch(USER_ID, TARGET_LAYER, OBJECT_ID, event);

    var featureCaptor = ArgumentCaptor.forClass(Feature.class);
    var headersCaptor = ArgumentCaptor.forClass((Class<Map<String, String>>) (Class<?>) Map.class);
    verify(producer).send(featureCaptor.capture(), headersCaptor.capture());

    var hydrated = OpenCopPropertiesParser.readFromFeature(featureCaptor.getValue());
    var returned = OpenCopPropertiesParser.readFromFeature(patchedFeature);
    assertEquals(OBJECT_ID.toString(), hydrated.getObjectId());
    assertEquals(new Point(7, 8), featureCaptor.getValue().getGeometry());
    assertEquals(returned.getObjectId(), hydrated.getObjectId());
    assertEquals(new Point(7, 8), patchedFeature.getGeometry());
    assertEquals(
        OBJECT_ID.toString(),
        headersCaptor.getValue().get(OpencopKafkaHeaders.OBJECT_ID.getName()));
  }

  @Test
  void patch_whenHydrated_updatesFeatureTimeToNow() throws Exception {
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    TARGET_LAYER,
                    "Ops",
                    LayerRole.EDITOR,
                    com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));
    when(userManager.get(USER_ID)).thenReturn(internalUser());
    stubExistingFeature();

    var originalTime = Instant.parse("2026-04-12T09:05:00Z").toEpochMilli();
    var event =
        feature(
            TARGET_LAYER,
            OBJECT_ID.toString(),
            new Point(3, 4),
            new Timestampz(
                originalTime,
                Instant.now().minusSeconds(120).toEpochMilli(),
                Instant.now().plusSeconds(60).toEpochMilli()));

    var before = Instant.now().toEpochMilli();
    var patchedFeature = handler.patch(USER_ID, TARGET_LAYER, OBJECT_ID, event);
    var after = Instant.now().toEpochMilli();

    var hydrated = OpenCopPropertiesParser.readFromFeature(patchedFeature);
    assertNotNull(hydrated.getTimestampz());
    assertFalse(hydrated.getTimestampz().getTime() < before);
    assertFalse(hydrated.getTimestampz().getTime() > after);
    assertFalse(hydrated.getTimestampz().getTime() == originalTime);
  }

  @Test
  void patch_whenTimestampzMissing_throwsBadRequest() throws Exception {
    var ex =
        assertThrows(
            OpencopEventException.class,
            () ->
                handler.patch(
                    USER_ID,
                    TARGET_LAYER,
                    OBJECT_ID,
                    feature(TARGET_LAYER, OBJECT_ID.toString(), new Point(3, 4), null)));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void patch_whenStartMissing_throwsBadRequest() throws Exception {
    var ex =
        assertThrows(
            OpencopEventException.class,
            () ->
                handler.patch(
                    USER_ID,
                    TARGET_LAYER,
                    OBJECT_ID,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        new Point(3, 4),
                        new Timestampz(0L, 0L, Instant.now().plusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void patch_whenStaleMissing_throwsBadRequest() throws Exception {
    var ex =
        assertThrows(
            OpencopEventException.class,
            () ->
                handler.patch(
                    USER_ID,
                    TARGET_LAYER,
                    OBJECT_ID,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        new Point(3, 4),
                        new Timestampz(
                            0L, Instant.parse("2026-04-12T09:04:00Z").toEpochMilli(), 0L))));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void patch_whenStaleIsInPast_throwsBadRequest() throws Exception {
    var ex =
        assertThrows(
            OpencopEventException.class,
            () ->
                handler.patch(
                    USER_ID,
                    TARGET_LAYER,
                    OBJECT_ID,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        new Point(3, 4),
                        new Timestampz(
                            0L,
                            Instant.now().minusSeconds(120).toEpochMilli(),
                            Instant.now().minusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertEquals("CORE-1003", ex.getCode());
    assertEquals("The object is stale and it cannot be created or modified", ex.getMessage());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void patch_whenObjectDoesNotExist_layerEditor_returnsNotFoundWithoutPublishing()
      throws Exception {
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    TARGET_LAYER,
                    "Ops",
                    LayerRole.EDITOR,
                    com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));
    when(postGisService.getLatestFeature(TARGET_LAYER, OBJECT_ID.toString()))
        .thenThrow(new NotFoundException("missing"));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                handler.patch(
                    USER_ID,
                    TARGET_LAYER,
                    OBJECT_ID,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        new Point(7, 8),
                        new Timestampz(
                            0L,
                            Instant.now().minusSeconds(120).toEpochMilli(),
                            Instant.now().plusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void patch_whenLayerArchived_returnsConflictWithoutPublishing() throws Exception {
    when(layerManager.get(TARGET_LAYER)).thenReturn(archivedLayer(TARGET_LAYER));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                handler.patch(
                    USER_ID,
                    TARGET_LAYER,
                    OBJECT_ID,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        new Point(3, 4),
                        new Timestampz(
                            0L,
                            Instant.now().minusSeconds(120).toEpochMilli(),
                            Instant.now().plusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
    verifyNoInteractions(userSpecificManager);
  }

  @Test
  void patch_whenLayerLockedForMerge_returnsConflictWithoutPublishing() throws Exception {
    when(layerManager.get(TARGET_LAYER)).thenReturn(lockedLayer(TARGET_LAYER));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                handler.patch(
                    USER_ID,
                    TARGET_LAYER,
                    OBJECT_ID,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        new Point(3, 4),
                        new Timestampz(
                            0L,
                            Instant.now().minusSeconds(120).toEpochMilli(),
                            Instant.now().plusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    verify(postGisService, never()).getLatestFeature(any(), any());
    verify(producer, never()).send(any(), any());
    verifyNoInteractions(userSpecificManager);
  }

  @Test
  void create_whenUserIsDirectSourceAdminWithoutLayerRole_isUnauthorized() {
    when(userSpecificManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(TARGET_LAYER, ServerRole.ADMIN, AccessVia.DIRECT)));

    var ex =
        assertThrows(
            OpencopEventException.class,
            () ->
                handler.create(
                    USER_ID,
                    TARGET_LAYER,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        new Point(1, 2),
                        new Timestampz(
                            0L,
                            Instant.now().minusSeconds(120).toEpochMilli(),
                            Instant.now().plusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void create_whenUserIsDirectSourceOwnerWithoutLayerRole_isUnauthorized() {
    when(userSpecificManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(TARGET_LAYER, ServerRole.OWNER, AccessVia.DIRECT)));

    var ex =
        assertThrows(
            OpencopEventException.class,
            () ->
                handler.create(
                    USER_ID,
                    TARGET_LAYER,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        new Point(1, 2),
                        new Timestampz(
                            0L,
                            Instant.now().minusSeconds(120).toEpochMilli(),
                            Instant.now().plusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void patch_whenSourceAdminOnly_isUnauthorized() throws Exception {
    when(userSpecificManager.getUserDirectServers(USER_ID))
        .thenReturn(List.of(new SourceRole(TARGET_LAYER, ServerRole.ADMIN, AccessVia.DIRECT)));
    when(userSpecificManager.getUserLayers(USER_ID)).thenReturn(List.of());
    stubExistingFeature();

    var ex =
        assertThrows(
            OpencopEventException.class,
            () ->
                handler.patch(
                    USER_ID,
                    TARGET_LAYER,
                    OBJECT_ID,
                    feature(
                        TARGET_LAYER,
                        OBJECT_ID.toString(),
                        new Point(7, 8),
                        new Timestampz(
                            0L,
                            Instant.now().minusSeconds(120).toEpochMilli(),
                            Instant.now().plusSeconds(60).toEpochMilli()))));

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void delete_whenSourceOwnerOnly_isUnauthorized() throws Exception {
    when(userSpecificManager.getUserDirectServers(USER_ID))
        .thenReturn(List.of(new SourceRole(TARGET_LAYER, ServerRole.OWNER, AccessVia.DIRECT)));
    when(userSpecificManager.getUserLayers(USER_ID)).thenReturn(List.of());

    var ex =
        assertThrows(
            OpencopEventException.class, () -> handler.delete(USER_ID, TARGET_LAYER, OBJECT_ID));

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
    verify(postGisService, never()).getLatestFeature(any(), any());
    verify(producer, never()).send(any(), any());
    verify(jobPublisher, never()).publishNow(any());
  }

  @Test
  void delete_whenLatestEventExists_marksItStaleAndPublishes() throws Exception {
    var latestFeature =
        feature(
            TARGET_LAYER,
            OBJECT_ID.toString(),
            new Point(9, 10),
            new Timestampz(
                Instant.now().minusSeconds(180).toEpochMilli(),
                Instant.now().minusSeconds(180).toEpochMilli(),
                Instant.now().plusSeconds(60).toEpochMilli()));
    when(postGisService.getLatestFeature(TARGET_LAYER, OBJECT_ID.toString()))
        .thenReturn(latestFeature);
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    TARGET_LAYER,
                    "Ops",
                    LayerRole.EDITOR,
                    com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));

    handler.delete(USER_ID, TARGET_LAYER, OBJECT_ID);

    var featureCaptor = ArgumentCaptor.forClass(Feature.class);
    var headersCaptor = ArgumentCaptor.forClass((Class<Map<String, String>>) (Class<?>) Map.class);
    verify(producer).send(featureCaptor.capture(), headersCaptor.capture());

    var published = OpenCopPropertiesParser.readFromFeature(featureCaptor.getValue());
    assertEquals(OBJECT_ID.toString(), published.getObjectId());
    assertNotNull(published.getId());
    assertEquals(new Point(9, 10), featureCaptor.getValue().getGeometry());
    assertNotNull(published.getTimestampz());
    assertFalse(published.getTimestampz().getStale() <= 0);
    assertEquals(
        OBJECT_ID.toString(),
        headersCaptor.getValue().get(OpencopKafkaHeaders.OBJECT_ID.getName()));
  }

  @Test
  void delete_whenLatestEventMissingFromPostGis_publishesGhostEvictionAndSucceeds()
      throws Exception {
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    TARGET_LAYER,
                    "Ops",
                    LayerRole.EDITOR,
                    com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));
    when(postGisService.getLatestFeature(TARGET_LAYER, OBJECT_ID.toString()))
        .thenThrow(new NotFoundException("missing"));

    handler.delete(USER_ID, TARGET_LAYER, OBJECT_ID);

    var captor = ArgumentCaptor.forClass(GhostEvictionJobSubmission.class);
    verify(jobPublisher).publishNow(captor.capture());
    assertEquals(TARGET_LAYER, captor.getValue().sourceId());
    assertEquals(OBJECT_ID.toString(), captor.getValue().objectId());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void delete_whenGhostEvictionPublishFails_propagatesException() throws Exception {
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    TARGET_LAYER,
                    "Ops",
                    LayerRole.EDITOR,
                    com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));
    when(postGisService.getLatestFeature(TARGET_LAYER, OBJECT_ID.toString()))
        .thenThrow(new NotFoundException("missing"));
    var brokerFailure = new RuntimeException("broker unavailable");
    doThrow(brokerFailure).when(jobPublisher).publishNow(any());

    var thrown =
        assertThrows(
            RuntimeException.class, () -> handler.delete(USER_ID, TARGET_LAYER, OBJECT_ID));

    assertSame(brokerFailure, thrown);
    verify(producer, never()).send(any(), any());
  }

  @Test
  void delete_whenLatestEventMissingAndUnauthorized_doesNotPublishGhostEviction() throws Exception {
    when(userSpecificManager.getUserLayers(USER_ID)).thenReturn(List.of());

    assertThrows(
        OpencopEventException.class, () -> handler.delete(USER_ID, TARGET_LAYER, OBJECT_ID));

    verify(postGisService, never()).getLatestFeature(any(), any());
    verify(jobPublisher, never()).publishNow(any());
    verify(producer, never()).send(any(), any());
  }

  @Test
  void delete_whenLayerArchived_returnsConflictWithoutPublishing() throws Exception {
    when(layerManager.get(TARGET_LAYER)).thenReturn(archivedLayer(TARGET_LAYER));

    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> handler.delete(USER_ID, TARGET_LAYER, OBJECT_ID));

    assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    verify(postGisService, never()).getLatestFeature(any(), any());
    verify(producer, never()).send(any(), any());
    verifyNoInteractions(userSpecificManager);
  }

  @Test
  void delete_whenLayerLockedForMerge_returnsConflictWithoutPublishing() throws Exception {
    when(layerManager.get(TARGET_LAYER)).thenReturn(lockedLayer(TARGET_LAYER));

    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> handler.delete(USER_ID, TARGET_LAYER, OBJECT_ID));

    assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    verify(postGisService, never()).getLatestFeature(any(), any());
    verify(producer, never()).send(any(), any());
    verifyNoInteractions(userSpecificManager);
  }

  @Test
  void create_attachesLayerProvenanceSoDeliveryIsRecorded() throws Exception {
    stubEditorLayer();
    when(userManager.get(USER_ID)).thenReturn(internalUser());

    var event =
        feature(
            TARGET_LAYER,
            OBJECT_ID.toString(),
            null,
            new Timestampz(
                0L,
                Instant.now().minusSeconds(120).toEpochMilli(),
                Instant.now().plusSeconds(60).toEpochMilli()));
    event.setGeometry(new Point(45.5, -73.6));

    Instant before = Instant.now();
    handler.create(USER_ID, TARGET_LAYER, event);
    Instant after = Instant.now();

    var provenance = publishedProvenance();
    assertEquals(FeatureSourceKind.LAYER, provenance.sourceKind());

    // The gate in PostGisActivityWriter: a null receipt time skips layer delivery entirely.
    assertNotNull(provenance.sourceReceivedAt());
    assertFalse(provenance.sourceReceivedAt().isBefore(before));
    assertFalse(provenance.sourceReceivedAt().isAfter(after));

    // No mapper ran, so mapper activity must not be claimed for a hand-drawn event.
    assertNull(provenance.mapperId());
    assertNull(provenance.mappingCompletedAt());
  }

  @Test
  void create_ingressIdIsTheEventIdAndFitsTheStorageBound() throws Exception {
    stubEditorLayer();
    when(userManager.get(USER_ID)).thenReturn(internalUser());

    var event =
        feature(
            TARGET_LAYER,
            OBJECT_ID.toString(),
            null,
            new Timestampz(
                0L,
                Instant.now().minusSeconds(120).toEpochMilli(),
                Instant.now().plusSeconds(60).toEpochMilli()));
    event.setGeometry(new Point(45.5, -73.6));

    Feature created = handler.create(USER_ID, TARGET_LAYER, event);

    var published = OpenCopPropertiesParser.readFromFeature(created);
    var provenance = publishedProvenance();
    assertEquals(published.getId().toString(), provenance.ingressId());
    assertTrue(provenance.ingressId().length() <= 255);
  }

  @Test
  void delete_attachesLayerProvenanceOnTheStalePublication() throws Exception {
    stubEditorLayer();
    stubExistingFeature();

    handler.delete(USER_ID, TARGET_LAYER, OBJECT_ID);

    var provenance = publishedProvenance();
    assertEquals(FeatureSourceKind.LAYER, provenance.sourceKind());
    assertNotNull(provenance.sourceReceivedAt());
    assertNotNull(provenance.ingressId());
  }

  /**
   * The headers are built once, before the send, so a Kafka redelivery of the same record replays
   * the same receipt time and ingress identity rather than reporting a second, later arrival.
   */
  @Test
  void create_provenanceIsFixedAtPublicationSoRedeliveryReportsTheSameIngress() throws Exception {
    stubEditorLayer();
    when(userManager.get(USER_ID)).thenReturn(internalUser());

    var event =
        feature(
            TARGET_LAYER,
            OBJECT_ID.toString(),
            null,
            new Timestampz(
                0L,
                Instant.now().minusSeconds(120).toEpochMilli(),
                Instant.now().plusSeconds(60).toEpochMilli()));
    event.setGeometry(new Point(45.5, -73.6));

    handler.create(USER_ID, TARGET_LAYER, event);

    var headers = capturedHeaders();
    var first = ProvenanceHeaders.from(asKafkaHeaders(headers));
    var redelivered = ProvenanceHeaders.from(asKafkaHeaders(headers));

    assertEquals(first, redelivered);
    assertEquals(first.sourceReceivedAt(), redelivered.sourceReceivedAt());
    assertEquals(first.ingressId(), redelivered.ingressId());
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> capturedHeaders() {
    var headersCaptor = ArgumentCaptor.forClass((Class<Map<String, String>>) (Class<?>) Map.class);
    verify(producer).send(any(), headersCaptor.capture());
    return headersCaptor.getValue();
  }

  /** Round-trips the published headers back through the consumer-side reader. */
  private FeatureProvenance publishedProvenance() {
    return ProvenanceHeaders.from(asKafkaHeaders(capturedHeaders()));
  }

  private static RecordHeaders asKafkaHeaders(Map<String, String> headers) {
    var kafkaHeaders = new RecordHeaders();
    headers.forEach((name, value) -> kafkaHeaders.add(name, value.getBytes(UTF_8)));
    return kafkaHeaders;
  }

  private void stubEditorLayer() {
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    TARGET_LAYER,
                    "Ops",
                    LayerRole.EDITOR,
                    com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));
  }

  private static Feature feature(UUID sourceId, String objectId) {
    return feature(sourceId, objectId, null);
  }

  private static Feature feature(UUID sourceId, String objectId, Point geometry) {
    return feature(sourceId, objectId, geometry, null);
  }

  private static Feature feature(
      UUID sourceId, String objectId, Point geometry, Timestampz timestampz) {
    var properties = new OpenCopProperties();
    properties.setSource(sourceId);
    properties.setObjectId(objectId);
    properties.setTimestampz(timestampz);
    return OpenCopPropertiesParser.writeToFeature(properties, geometry);
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

  private static Layer archivedLayer(UUID layerId) {
    var layer = new Layer();
    layer.setId(layerId);
    layer.setArchivedAt(Instant.now());
    return layer;
  }

  private static Layer lockedLayer(UUID layerId) {
    var layer = new Layer();
    layer.setId(layerId);
    layer.setLifecycleState(LayerLifecycleState.MERGE_SOURCE_LOCKED);
    return layer;
  }

  private static Layer activeLayer(UUID layerId) {
    var layer = new Layer();
    layer.setId(layerId);
    return layer;
  }

  private void stubExistingFeature() throws Exception {
    when(postGisService.getLatestFeature(TARGET_LAYER, OBJECT_ID.toString()))
        .thenReturn(
            feature(
                TARGET_LAYER,
                OBJECT_ID.toString(),
                new Point(0, 0),
                new Timestampz(
                    Instant.now().minusSeconds(180).toEpochMilli(),
                    Instant.now().minusSeconds(180).toEpochMilli(),
                    Instant.now().plusSeconds(60).toEpochMilli())));
  }
}
