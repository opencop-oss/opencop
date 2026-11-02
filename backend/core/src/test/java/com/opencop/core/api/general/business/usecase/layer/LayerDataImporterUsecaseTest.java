// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.media.LayerMediaRepository;
import com.opencop.core.api.general.ports.gateway.media.MediaRepository;
import com.opencop.core.api.general.technical.model.data.DataImportRequest;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.model.dataimport.ImportEnvelope;
import com.opencop.core.sdk.model.dataimport.LayerImportProperties;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.ports.gateway.iam.AccessRepository;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.media.MediaStore;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

class LayerDataImporterUsecaseTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655445001");
  private static final Caller ACTOR =
      new Caller(USER_ID, "Import Admin", "import.admin@example.test", true);
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655445002");
  private static final UUID MAPPER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655445003");

  private MapperProfileService mapperService;
  private AccessRepository<LayerRole> layerAccess;
  private LayerManager<Layer, LayerMember> layerManager;
  private MessageBrokerProducer<byte[]> brokerProducer;
  private MediaStore mediaStore;
  private BlobClient blobClient;
  private MediaRepository mediaRepository;
  private LayerMediaRepository layerMediaRepository;
  private ObjectMapper objectMapper;
  private LayerImportProperties properties;
  private AuditRecorder auditRecorder;
  private LayerDataImporterUsecase usecase;

  @BeforeEach
  void setUp() {
    mapperService = mock(MapperProfileService.class);
    layerAccess = mock(AccessRepository.class);
    layerManager = mock(LayerManager.class);
    brokerProducer = mock(MessageBrokerProducer.class);
    mediaStore = mock(MediaStore.class);
    blobClient = mock(BlobClient.class);
    mediaRepository = mock(MediaRepository.class);
    layerMediaRepository = mock(LayerMediaRepository.class);
    objectMapper = mock(ObjectMapper.class);
    auditRecorder = mock(AuditRecorder.class);
    properties = new LayerImportProperties();
    properties.setMaxBytes(1024);
    when(layerManager.get(LAYER_ID)).thenReturn(new Layer.Builder().withId(LAYER_ID).build());
    usecase =
        new LayerDataImporterUsecase(
            mapperService,
            layerAccess,
            brokerProducer,
            mediaStore,
            blobClient,
            mediaRepository,
            layerMediaRepository,
            objectMapper,
            properties,
            layerManager,
            auditRecorder,
            new LayerAuditProjection(),
            mock(PlatformTransactionManager.class));
  }

  @Test
  void importData_whenMapperIdMissing_throwsNotFound() {
    var request = new DataImportRequest(null, new byte[] {1}, "file.csv", "text/csv");

    var exception =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.importData(ACTOR, LAYER_ID, request));

    assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
  }

  @Test
  void importData_whenMapperDoesNotExist_throwsBadRequest() {
    var request = new DataImportRequest(MAPPER_ID, new byte[] {1}, "file.csv", "text/csv");
    when(mapperService.getLatestMapperProfileMeta(MAPPER_ID)).thenReturn(null);

    var exception =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.importData(ACTOR, LAYER_ID, request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
  }

  @Test
  void importData_whenPayloadIsTooLarge_throwsContentTooLarge() {
    properties.setMaxBytes(1);
    var request = new DataImportRequest(MAPPER_ID, new byte[] {1, 2}, "file.csv", "text/csv");
    when(mapperService.getLatestMapperProfileMeta(MAPPER_ID)).thenReturn(ingressMapper());

    var exception =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.importData(ACTOR, LAYER_ID, request));

    assertEquals(HttpStatus.CONTENT_TOO_LARGE, exception.getHttpStatus());
  }

  @Test
  void importData_whenUserIsNotAdminOrOwner_throwsForbidden() {
    var request = new DataImportRequest(MAPPER_ID, new byte[] {1}, "file.csv", "text/csv");
    when(mapperService.getLatestMapperProfileMeta(MAPPER_ID)).thenReturn(ingressMapper());
    when(layerAccess.userRole(USER_ID, LAYER_ID)).thenReturn(LayerRole.MEMBER);

    var exception =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.importData(ACTOR, LAYER_ID, request));

    assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
    verify(blobClient, never()).upload(any(), any());
  }

  @Test
  void importData_whenRequestIsValid_uploadsBlobAndSendsEnvelope() throws Exception {
    var request = new DataImportRequest(MAPPER_ID, new byte[] {1, 2, 3}, "file.csv", "text/csv");
    when(mapperService.getLatestMapperProfileMeta(MAPPER_ID)).thenReturn(ingressMapper());
    when(layerAccess.userRole(USER_ID, LAYER_ID)).thenReturn(LayerRole.ADMIN);
    when(objectMapper.writeValueAsBytes(any(ImportEnvelope.class))).thenReturn(new byte[] {9, 9});

    usecase.importData(ACTOR, LAYER_ID, request);

    verify(blobClient).upload(any(), any());
    var headersCaptor = ArgumentCaptor.forClass((Class<Map<String, String>>) (Class<?>) Map.class);
    verify(brokerProducer).send(eq(new byte[] {9, 9}), headersCaptor.capture());
    assertEquals(
        LAYER_ID.toString(), headersCaptor.getValue().get(OpencopKafkaHeaders.LAYER_ID.getName()));
    verify(mediaStore, never()).upload(any(), any(), anyLong(), any(), any());
  }

  /**
   * The row is committed before the publish, so an accepted import always has history. It records
   * what was accepted, not what landed: the mapping finishes in the processor.
   */
  @Test
  void importData_recordsTheAcceptedImportBeforePublishing() throws Exception {
    var request = new DataImportRequest(MAPPER_ID, new byte[] {1, 2, 3}, "file.csv", "text/csv");
    when(mapperService.getLatestMapperProfileMeta(MAPPER_ID)).thenReturn(ingressMapper());
    when(layerAccess.userRole(USER_ID, LAYER_ID)).thenReturn(LayerRole.ADMIN);
    when(objectMapper.writeValueAsBytes(any(ImportEnvelope.class))).thenReturn(new byte[] {9, 9});

    usecase.importData(ACTOR, LAYER_ID, request);

    var recorded = ArgumentCaptor.forClass(AuditEvent.class);
    var order = inOrder(auditRecorder, brokerProducer);
    order.verify(auditRecorder).record(recorded.capture());
    order.verify(brokerProducer).send(any(), any());

    var event = recorded.getValue();
    assertEquals(EntityType.LAYER, event.entityType());
    assertEquals(LAYER_ID, event.entityId());
    assertEquals(Operation.IMPORT_REQUESTED, event.operation());
    assertEquals("file.csv", event.change().details().get("filename"));
    assertEquals("text/csv", event.change().details().get("format"));
    assertEquals(MAPPER_ID.toString(), event.change().details().get("mapperId"));
    assertNotNull(event.change().details().get("importId"));
  }

  /**
   * A publish that fails leaves a row for a request that never ran, which is the safer error than a
   * processed import with no history.
   */
  @Test
  void importData_whenPublishFails_keepsTheRecordedRow() throws Exception {
    var request = new DataImportRequest(MAPPER_ID, new byte[] {1, 2, 3}, "file.csv", "text/csv");
    when(mapperService.getLatestMapperProfileMeta(MAPPER_ID)).thenReturn(ingressMapper());
    when(layerAccess.userRole(USER_ID, LAYER_ID)).thenReturn(LayerRole.ADMIN);
    when(objectMapper.writeValueAsBytes(any(ImportEnvelope.class))).thenReturn(new byte[] {9, 9});
    doThrow(new IllegalStateException("broker down")).when(brokerProducer).send(any(), any());

    assertThrows(IllegalStateException.class, () -> usecase.importData(ACTOR, LAYER_ID, request));

    verify(auditRecorder).record(any(AuditEvent.class));
  }

  @Test
  void importData_whenLayerArchived_returnsConflictBeforeBlobUpload() {
    var request = new DataImportRequest(MAPPER_ID, new byte[] {1, 2, 3}, "file.csv", "text/csv");
    var mapper = mock(MapperProfile.class);
    when(mapper.qualifier()).thenReturn(MapperQualifier.INGRESS);
    when(mapper.archived()).thenReturn(false);
    when(mapperService.getLatestMapperProfileMeta(MAPPER_ID)).thenReturn(mapper);
    when(layerAccess.userRole(USER_ID, LAYER_ID)).thenReturn(LayerRole.ADMIN);
    when(layerManager.get(LAYER_ID))
        .thenReturn(
            new Layer.Builder().withId(LAYER_ID).withArchivedAt(java.time.Instant.now()).build());

    var exception =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.importData(ACTOR, LAYER_ID, request));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    verify(blobClient, never()).upload(any(), any());
    verify(mediaStore, never()).upload(any(), any(), anyLong(), any(), any());
    verify(mediaRepository, never()).save(any());
    verify(layerMediaRepository, never()).save(any(), any());
    verify(brokerProducer, never()).send(any(), any());
  }

  @Test
  void importData_whenLayerIsLockedForMerge_returnsConflictBeforeBlobUpload() {
    var request = new DataImportRequest(MAPPER_ID, new byte[] {1, 2, 3}, "file.csv", "text/csv");
    var mapper = mock(MapperProfile.class);
    when(mapper.qualifier()).thenReturn(MapperQualifier.INGRESS);
    when(mapper.archived()).thenReturn(false);
    when(mapperService.getLatestMapperProfileMeta(MAPPER_ID)).thenReturn(mapper);
    when(layerAccess.userRole(USER_ID, LAYER_ID)).thenReturn(LayerRole.ADMIN);
    when(layerManager.get(LAYER_ID))
        .thenReturn(
            new Layer.Builder()
                .withId(LAYER_ID)
                .withLifecycleState(LayerLifecycleState.MERGE_SOURCE_LOCKED)
                .build());

    var exception =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.importData(ACTOR, LAYER_ID, request));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    verify(blobClient, never()).upload(any(), any());
    verify(mediaStore, never()).upload(any(), any(), anyLong(), any(), any());
    verify(mediaRepository, never()).save(any());
    verify(layerMediaRepository, never()).save(any(), any());
    verify(brokerProducer, never()).send(any(), any());
  }

  @Test
  void importData_whenMapperIsAnEgressMapper_throwsBadRequestBeforeBlobUpload() {
    var request = new DataImportRequest(MAPPER_ID, new byte[] {1}, "file.csv", "text/csv");
    when(mapperService.getLatestMapperProfileMeta(MAPPER_ID))
        .thenReturn(ingressMapper().toBuilder().withQualifier(MapperQualifier.EGRESS).build());

    var exception =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.importData(ACTOR, LAYER_ID, request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    verify(blobClient, never()).upload(any(), any());
    verify(brokerProducer, never()).send(any(), any());
  }

  private static MapperProfile ingressMapper() {
    return MapperProfile.builder()
        .withId(MAPPER_ID)
        .withName("Import mapper")
        .withQualifier(MapperQualifier.INGRESS)
        .withArchived(false)
        .build();
  }
}
