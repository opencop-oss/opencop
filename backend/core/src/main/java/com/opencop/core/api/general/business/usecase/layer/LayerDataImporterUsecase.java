// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layer;

import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.CONTENT_KIND;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.ICON_REFS;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.IMPORT_ID;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.LAYER_ID;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.MAPPER_ID;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.OBJECT_ID;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.SOURCE_ID;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.policy.MapperQualifierPolicy;
import com.opencop.core.api.general.business.policy.ResourceLifecyclePolicy;
import com.opencop.core.api.general.business.usecase.media.ImageDimensions;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.media.LayerMediaRepository;
import com.opencop.core.api.general.ports.gateway.media.MediaRepository;
import com.opencop.core.api.general.ports.usecase.layer.LayerDataImporter;
import com.opencop.core.api.general.technical.model.data.DataImportRequest;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.model.dataimport.ImportEnvelope;
import com.opencop.core.sdk.model.dataimport.LayerImportProperties;
import com.opencop.core.sdk.model.media.BlobKeyGenerator;
import com.opencop.core.sdk.model.media.BlobKeyPrefix;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaAttributes;
import com.opencop.core.sdk.model.media.MediaBlobKey;
import com.opencop.core.sdk.model.media.MediaContentType;
import com.opencop.core.sdk.model.media.MediaRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.ports.gateway.iam.AccessRepository;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.media.MediaStore;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.extractor.KmzAssetResolver;
import com.opencop.core.sdk.technical.gateway.extractor.KmzIconRef;
import com.opencop.core.sdk.technical.gateway.extractor.ZipSiphon;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

public class LayerDataImporterUsecase implements LayerDataImporter {

  private static final Logger log = LoggerFactory.getLogger(LayerDataImporterUsecase.class);

  private static final int MAX_EXTRACTED_FILES = 2_000;

  private final MapperProfileService mapperService;
  private final AccessRepository<LayerRole> layerAccess;
  private final MessageBrokerProducer<byte[]> messageBrokerProducer;
  private final MediaStore mediaStore;
  private final BlobClient blobClient;
  private final MediaRepository mediaRepository;
  private final LayerMediaRepository layerMediaRepository;
  private final ObjectMapper objectMapper;
  private final LayerImportProperties layerImportProperties;
  private final LayerManager<Layer, LayerMember> layerManager;
  private final AuditRecorder auditRecorder;
  private final LayerAuditProjection projection;
  private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

  public LayerDataImporterUsecase(
      MapperProfileService mapperService,
      AccessRepository<LayerRole> layerAccess,
      MessageBrokerProducer<byte[]> messageBrokerProducer,
      MediaStore mediaStore,
      BlobClient blobClient,
      MediaRepository mediaRepository,
      LayerMediaRepository layerMediaRepository,
      ObjectMapper objectMapper,
      LayerImportProperties layerImportProperties,
      LayerManager<Layer, LayerMember> layerManager,
      AuditRecorder auditRecorder,
      LayerAuditProjection projection,
      PlatformTransactionManager transactionManager) {
    this.mapperService = mapperService;
    this.layerAccess = layerAccess;
    this.messageBrokerProducer = messageBrokerProducer;
    this.mediaStore = mediaStore;
    this.blobClient = blobClient;
    this.mediaRepository = mediaRepository;
    this.layerMediaRepository = layerMediaRepository;
    this.objectMapper = objectMapper;
    this.layerImportProperties = layerImportProperties;
    this.layerManager = layerManager;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
    this.transactionTemplate =
        new org.springframework.transaction.support.TransactionTemplate(transactionManager);
  }

  @Override
  public void importData(Caller requestor, UUID layerId, DataImportRequest request) {
    var requestorId = requestor.userId();
    if (request.mapperId() == null) {
      throw new GlobalOpenCopException("Mapper required", HttpStatus.NOT_FOUND);
    }
    var mapper = mapperService.getLatestMapperProfileMeta(request.mapperId());
    if (mapper == null) {
      throw new GlobalOpenCopException("Mapper not found", HttpStatus.BAD_REQUEST);
    }
    if (Boolean.TRUE.equals(mapper.archived())) {
      throw new GlobalOpenCopException(
          "Mapper is archived and cannot be used for import", HttpStatus.BAD_REQUEST);
    }
    if (!MapperQualifierPolicy.decodes(mapper)) {
      throw new GlobalOpenCopException(
          "Only an ingress mapper can decode imported data", HttpStatus.BAD_REQUEST);
    }

    var layer = layerManager.get(layerId);
    ResourceLifecyclePolicy.assertLayerActive(layer, "import data");

    // 413 check: reject before any processing
    if (request.payload().length > layerImportProperties.getMaxBytes()) {
      throw new GlobalOpenCopException(
          "Import file exceeds maximum size", HttpStatus.CONTENT_TOO_LARGE);
    }

    var importId = UUID.randomUUID();
    try {
      var role = layerAccess.userRole(requestorId, layerId);
      if (role == null || !role.isHigherThan(LayerRole.MEMBER)) {
        throw new GlobalOpenCopException(
            "User permissions insufficient, need to be an ADMIN or OWNER of the layer",
            HttpStatus.FORBIDDEN);
      }

      // FUTURE ARCHITECTURE IMPROVEMENT: client could upload with a presigned-url/SAS
      // FUTURE ARCHITECTURE IMPROVEMENT: kmz and others were handled differently for reasons that
      // are no longer valid, we should not be unzipping here - just upload the *real* zip (kmz) to
      // azure and have processor retrieve the blob and handle media and what not - this will
      // simplify the code and reduce the need for things like an icon manifest
      byte[] payloadToSend = request.payload();
      List<KmzIconRef> iconRefs = List.of();

      if (ZipSiphon.isZip(payloadToSend)) {
        ZipSiphon.Result res =
            ZipSiphon.siphon(
                payloadToSend,
                ZipSiphon.imagesOnly(),
                MAX_EXTRACTED_FILES,
                layerImportProperties.getMaxBytes());

        Map<String, KmzAssetResolver.MediaInfo> pathToMediaInfo = new HashMap<>();
        for (ZipSiphon.ZipAsset asset : res.extractedByPath().values()) {
          var mediaId = UUID.randomUUID();
          var blobKey = MediaBlobKey.generate(mediaId, asset.path());
          mediaStore.upload(
              blobKey,
              new ByteArrayInputStream(asset.bytes()),
              asset.bytes().length,
              asset.contentType(),
              mediaId);
          MediaAttributes attributes = null;
          Integer widthPx = null;
          Integer heightPx = null;
          var dims = ImageDimensions.read(asset.bytes());
          if (dims != null) {
            widthPx = dims[0];
            heightPx = dims[1];
            attributes = new MediaAttributes.Image(widthPx, heightPx);
          } else {
            log.warn(
                "KMZ import: could not read dimensions for extracted asset '{}' (contentType={})",
                asset.path(),
                asset.contentType());
          }
          mediaRepository.save(
              new MediaRecord(
                  mediaId,
                  blobKey,
                  asset.path(),
                  MediaContentType.IMAGE,
                  asset.bytes().length,
                  MediaAccess.SCOPED,
                  requestorId,
                  requestorId,
                  Instant.now(),
                  asset.contentType(),
                  attributes,
                  null,
                  MediaUsage.LAYER_ICON));
          layerMediaRepository.save(layerId, mediaId);
          pathToMediaInfo.put(
              asset.path(), new KmzAssetResolver.MediaInfo(mediaId, widthPx, heightPx));
        }

        iconRefs = KmzAssetResolver.resolve(res.strippedZip(), pathToMediaInfo);
        payloadToSend = res.strippedZip();
      }

      // send blobKey in Kafka message instead of file contents
      var blobKey = BlobKeyGenerator.generate(BlobKeyPrefix.IMPORT, importId, request.filename());
      blobClient.upload(blobKey, new ByteArrayInputStream(payloadToSend));

      var envelope =
          new ImportEnvelope(
              blobKey, request.filename(), request.contentType(), (long) payloadToSend.length);

      Map<String, String> headers = new HashMap<>();
      headers.put(CONTENT_KIND.getName(), ImportEnvelope.CONTENT_KIND);
      headers.put(SOURCE_ID.getName(), layerId.toString());
      headers.put(LAYER_ID.getName(), layerId.toString());
      headers.put(OBJECT_ID.getName(), UUID.randomUUID().toString());
      headers.put(MAPPER_ID.getName(), request.mapperId().toString());
      headers.put(IMPORT_ID.getName(), importId.toString());
      if (!iconRefs.isEmpty()) {
        headers.put(ICON_REFS.getName(), objectMapper.writeValueAsString(iconRefs));
      }
      // IMPORT_REQUESTED, not IMPORTED: the mapping runs in the processor and there is no import
      // status table, so the ledger records what was accepted rather than claiming what landed.
      // Committed before the publish, so an accepted import always has its row. A publish that
      // then fails leaves a row for a request that never ran, which is the safer error than a
      // processed import with no history.
      transactionTemplate.executeWithoutResult(
          _ ->
              auditRecorder.record(
                  new AuditEvent(
                      EntityType.LAYER,
                      layerId,
                      layer.getName(),
                      Operation.IMPORT_REQUESTED,
                      requestor,
                      projection.importRequested(
                          request.filename(), request.contentType(), request.mapperId(), importId),
                      AuditEvent.CURRENT_CHANGE_VERSION)));
      messageBrokerProducer.send(objectMapper.writeValueAsBytes(envelope), headers);
    } catch (IOException e) {
      log.error(
          "IO Exception while processing a layer import {} | cause: {}", importId, e.getMessage());
      throw new GlobalOpenCopException("IO exception", HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
