// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.CONTENT_KIND;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.ICON_REFS;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.IMPORT_ID;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.LAYER_ID;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.MAPPER_ID;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.OBJECT_ID;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.SOURCE_ID;

import com.opencop.core.sdk.model.dataimport.ImportEnvelope;
import com.opencop.core.sdk.model.provenance.FeatureProvenance;
import com.opencop.core.sdk.model.provenance.FeatureProvenance.FeatureSourceKind;
import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.ports.gateway.broker.BrokerMessage;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedMappingResult;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.WriteOnlySourceException;
import com.opencop.core.sdk.technical.gateway.broker.kafka.ProvenanceHeaders;
import com.opencop.core.sdk.technical.gateway.extractor.KmzIconRef;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.EventDecoderRegistry;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.elements.Element;
import com.opencop.core.sdk.technical.model.opencop.elements.MediaIconElement;
import com.opencop.processor.business.model.mapping.MappingResourceKind;
import com.opencop.processor.business.model.mapping.MappingResourceRef;
import com.opencop.processor.business.model.mapping.RealtimeMappingAttempt;
import com.opencop.processor.business.model.mapping.RealtimeMappingEntry;
import com.opencop.processor.ports.business.observability.mapping.MappingObservationFailureSink;
import com.opencop.processor.ports.business.observability.mapping.RealtimeMappingOutcomeObserver;
import com.opencop.processor.ports.business.usecase.event.MissingHeaderException;
import com.opencop.processor.ports.business.usecase.event.RawEventHandler;
import com.opencop.processor.technical.spring.dlt.DeadLetterHeaders;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.geojson.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

public class MapRawToGeoHandler implements RawEventHandler<byte[]> {

  private static final Logger log = LoggerFactory.getLogger(MapRawToGeoHandler.class);
  private static final String MEDIA_HREF_PREFIX = "opencop:media:";
  private static final int MAX_SUMMARIZED_ERROR_LENGTH = 300;

  private final EventDecoderRegistry registry;
  private final MessageBrokerProducer<Feature> producer;
  private final MessageBrokerProducer<byte[]> rawDltProducer;
  private final ObjectMapper objectMapper;
  private final BlobClient blobClient;
  private final RealtimeMappingOutcomeObserver mappingObservability;
  private final MappingObservationFailureSink observationFailureReporter;

  public MapRawToGeoHandler(
      final EventDecoderRegistry registry,
      final MessageBrokerProducer<Feature> producer,
      final ObjectMapper objectMapper,
      final BlobClient blobClient,
      final MessageBrokerProducer<byte[]> rawDltProducer,
      final RealtimeMappingOutcomeObserver mappingObservability,
      final MappingObservationFailureSink observationFailureReporter) {
    this.registry = registry;
    this.producer = producer;
    this.rawDltProducer = rawDltProducer;
    this.objectMapper = objectMapper;
    this.blobClient = blobClient;
    this.mappingObservability = mappingObservability;
    this.observationFailureReporter = observationFailureReporter;
  }

  @Override
  public void handle(List<ConsumerRecord<String, byte[]>> messages) {
    var rawDltMessages = new ArrayList<BrokerMessage<byte[]>>();
    var mappedMessages = new ArrayList<BrokerMessage<Feature>>();

    for (ConsumerRecord<String, byte[]> message : messages) {
      try {
        var sourceId = getUuidHeader(message.headers(), SOURCE_ID.getName());
        var objectId = getUuidHeader(message.headers(), OBJECT_ID.getName());

        var mapperId = getOptionalUuidHeader(message.headers(), MAPPER_ID.getName());
        var importId = getOptionalUuidHeader(message.headers(), IMPORT_ID.getName());
        var layerId = getOptionalUuidHeader(message.headers(), LAYER_ID.getName());
        var iconManifest = deserializeIconRefs(message.headers());

        var props = new MappingProperties(sourceId, importId, false, iconManifest);

        // Header presence, not successful UUID parsing, excludes telemetry: on import/layer paths
        // the legacy sourceId may identify a target layer rather than a data source.
        var telemetryEligible =
            message.headers().lastHeader(IMPORT_ID.getName()) == null
                && message.headers().lastHeader(LAYER_ID.getName()) == null;
        var decoded = decodeFeatures(message, objectId, mapperId, props, telemetryEligible);
        var features = decoded.features();

        if (layerId != null && !iconManifest.isEmpty()) {
          resolveMediaHrefs(features, layerId, iconManifest);
        }

        mappedMessages.addAll(mappedMessages(features, provenanceFor(message, decoded, layerId)));
      } catch (RawImportBlobAccessException ex) {
        // Blob access failures are dependency failures, not proof that the raw record is invalid.
        // Retry the listener poll instead of parking a valid import because storage was
        // unavailable.
        throw ex;
      } catch (WriteOnlySourceException ex) {
        // A source that only writes decodes nothing it receives, such as bytes queued before it
        // became write-only. The payload is not faulty, so it is dropped rather than dead-lettered.
        log.debug(
            "Dropping record from partition {} offset {}: {}",
            message.partition(),
            message.offset(),
            ex.getMessage());
      } catch (Exception ex) {
        // We publish record-local content, mapping, and output-shape failures directly to the DLT.
        // Throwing BatchListenerFailedException would re-deliver all remaining records from the
        // failed offset with retry backoff, stalling real-time events whenever one import
        // fails.
        // See:
        // https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html#dead-letters
        log.error(
            "Failed to process record from partition {} offset {}, queueing for DLT publish: {}",
            message.partition(),
            message.offset(),
            ex.getMessage(),
            ex);
        rawDltMessages.add(rawDltMessage(message, ex));
      }
    }

    if (!rawDltMessages.isEmpty()) {
      rawDltProducer.sendAll(rawDltMessages);
    }
    if (!mappedMessages.isEmpty()) {
      producer.sendAll(mappedMessages);
    }
  }

  private List<BrokerMessage<Feature>> mappedMessages(
      List<Feature> features, FeatureProvenance provenance) {
    var headers = ProvenanceHeaders.toHeaders(provenance);
    var messages = new ArrayList<BrokerMessage<Feature>>(features.size());
    for (Feature feature : features) {
      // Validate before adding to the poll-wide mapped batch so synchronous Kafka serialization
      // failures remain tied to the raw record that produced the bad feature.
      objectMapper.writeValueAsString(feature);
      messages.add(BrokerMessage.of(feature, headers));
    }
    return messages;
  }

  /**
   * Provenance for everything one raw record produced.
   *
   * <p>{@code sourceReceivedAt} is the raw record's own Kafka timestamp — when Connect published
   * the inbound message — rather than now, so it survives a slow or retried poll unchanged. That is
   * what lets a replay write an old timestamp that the monotonic activity rule then ignores.
   *
   * <p>{@code mappingExecutionId} is derived rather than generated so an ordinary Kafka retry
   * recomputes the same value. A deliberate remap under a new mapper version therefore takes a new
   * execution id while keeping the ingress identity, which is the distinction the activity model
   * needs; a remap on the same version is indistinguishable, and does not need to be.
   */
  private FeatureProvenance provenanceFor(
      ConsumerRecord<String, byte[]> message, Decoded decoded, UUID layerId) {
    var ingressId = message.topic() + ":" + message.partition() + ":" + message.offset();
    return new FeatureProvenance(
        layerId != null ? FeatureSourceKind.LAYER : FeatureSourceKind.DATA_SOURCE,
        decoded.mapperId(),
        decoded.mapperVersion(),
        ingressId,
        ingressId + ":" + decoded.mapperVersion(),
        Instant.ofEpochMilli(message.timestamp()),
        Instant.now());
  }

  private BrokerMessage<byte[]> rawDltMessage(
      ConsumerRecord<String, byte[]> message, Throwable failure) {
    return BrokerMessage.withKey(
        message.key(),
        message.value(),
        stringHeaders(DeadLetterHeaders.forManualRaw(message, failure)));
  }

  private Map<String, String> stringHeaders(Headers headers) {
    var stringHeaders = new LinkedHashMap<String, String>();
    headers.forEach(
        header ->
            stringHeaders.put(header.key(), new String(header.value(), StandardCharsets.UTF_8)));
    return stringHeaders;
  }

  /**
   * Decoded output plus the identity of the mapper that produced it.
   *
   * <p>The mapper identity is carried out of decoding rather than discarded because it becomes
   * feature provenance, and nothing downstream of this point can recover it. {@code mapperVersion}
   * is null on the one path that decodes without returning a result — a layer- or import-targeted
   * raw record — where only the header's mapper id is known. Null there means "not reported", and
   * the persistence conflict rules treat it that way.
   */
  private record Decoded(List<Feature> features, UUID mapperId, Integer mapperVersion) {

    static Decoded of(DecodedMappingResult result) {
      return new Decoded(result.features(), result.mapperId(), result.mapperVersion());
    }
  }

  private Decoded decodeFeatures(
      ConsumerRecord<String, byte[]> message,
      UUID objectId,
      UUID mapperId,
      MappingProperties props,
      boolean telemetryEligible)
      throws Exception {
    var contentKind = getOptionalStringHeader(message.headers(), CONTENT_KIND.getName());
    if (ImportEnvelope.CONTENT_KIND.equals(contentKind)) {
      return handleImportMessage(message, objectId, mapperId, props);
    }

    if (!telemetryEligible) {
      var features =
          mapperId != null
              ? registry.decodeAndMap(mapperId, objectId, props, message.value())
              : registry.decodeAndMap(objectId, props, message.value());
      return new Decoded(features, mapperId, null);
    }

    // Raw bytes use an explicit mapper hint when present; otherwise decoder config resolves by
    // source. Thrown decode failures keep their existing manual-DLT routing; the catch records
    // only the bounded failed_thrown outcome before rethrowing.
    final DecodedMappingResult result;
    try {
      result =
          mapperId != null
              ? registry.decodeAndMapRealTimeResult(mapperId, objectId, props, message.value())
              : registry.decodeAndMapRealTimeResult(objectId, props, message.value());
    } catch (WriteOnlySourceException e) {
      // Nothing was mapped, so there is no mapping outcome to record.
      throw e;
    } catch (RuntimeException e) {
      observeThrownMapping(props.sourceId(), message.partition(), message.offset());
      throw e;
    }
    observeMapping(props.sourceId(), message.partition(), message.offset(), result);
    result.throwIfErrorDiagnostic();
    return Decoded.of(result);
  }

  private void observeMapping(
      UUID sourceId, int partition, long offset, DecodedMappingResult result) {
    try {
      mappingObservability.observe(
          new RealtimeMappingAttempt(
              RealtimeMappingEntry.RAW_REALTIME,
              new MappingResourceRef(MappingResourceKind.DATA_SOURCE, sourceId),
              partition,
              offset),
          result);
    } catch (RuntimeException e) {
      try {
        observationFailureReporter.report(e.getClass());
      } catch (RuntimeException _) {
        // Telemetry must not make a raw record eligible for manual DLT publication.
      }
    }
  }

  private void observeThrownMapping(UUID sourceId, int partition, long offset) {
    try {
      mappingObservability.observeThrown(
          new RealtimeMappingAttempt(
              RealtimeMappingEntry.RAW_REALTIME,
              new MappingResourceRef(MappingResourceKind.DATA_SOURCE, sourceId),
              partition,
              offset));
    } catch (RuntimeException e) {
      try {
        observationFailureReporter.report(e.getClass());
      } catch (RuntimeException _) {
        // Telemetry must not make a raw record eligible for manual DLT publication.
      }
    }
  }

  private Decoded handleImportMessage(
      ConsumerRecord<String, byte[]> message, UUID objectId, UUID mapperId, MappingProperties props)
      throws Exception {
    ImportEnvelope importEnvelope = null;
    try {
      if (mapperId == null) {
        throw new MissingHeaderException(MAPPER_ID.getName());
      }
      importEnvelope = objectMapper.readValue(message.value(), ImportEnvelope.class);
      byte[] payload = downloadImportBlob(importEnvelope);
      var result = registry.decodeAndMapResult(mapperId, objectId, props, payload);
      logImport(result, props, importEnvelope);
      return Decoded.of(result);
    } catch (Exception ex) {
      logImportFailure(props.importId(), props.sourceId(), mapperId, importEnvelope, ex);
      throw ex;
    }
  }

  private byte[] downloadImportBlob(ImportEnvelope importEnvelope) {
    try (InputStream stream = blobClient.download(importEnvelope.blobKey())) {
      return stream.readAllBytes();
    } catch (IOException | RuntimeException e) {
      throw new RawImportBlobAccessException("Failed to access raw import blob", e);
    }
  }

  private void logImport(
      DecodedMappingResult result, MappingProperties props, ImportEnvelope envelope) {
    if (result.errorCount() > 0) {
      if (log.isWarnEnabled()) {
        log.warn(
            "Import {} for source {} using mapper '{}' on file '{}' produced {} feature(s), {} mapped record(s), {} error(s), {} warning(s)",
            props.importId(),
            props.sourceId(),
            result.mapperName(),
            filename(envelope),
            result.featureCount(),
            result.recordCount(),
            result.errorCount(),
            result.warningCount());
      }
      result
          .firstError()
          .ifPresent(
              error -> {
                if (log.isWarnEnabled()) {
                  log.warn(
                      "Import {} first mapper error: {} :: {}",
                      props.importId(),
                      error.code(),
                      summarize(error.message(), MAX_SUMMARIZED_ERROR_LENGTH));
                }
              });
      return;
    }

    if (log.isInfoEnabled()) {
      log.info(
          "Import {} for source {} using mapper '{}' on file '{}' (features={}, mapped records={}, warnings={})",
          props.importId(),
          props.sourceId(),
          result.mapperName(),
          filename(envelope),
          result.featureCount(),
          result.recordCount(),
          result.warningCount());
    }
  }

  private void logImportFailure(
      UUID importId, UUID sourceId, UUID mapperId, ImportEnvelope envelope, Throwable ex) {
    if (log.isErrorEnabled()) {
      log.error(
          "Import {} for source {} using mapper {} on file '{}' failed before mapping completed: {}",
          importId,
          sourceId,
          mapperId,
          filename(envelope),
          summarize(
              ex.getClass().getSimpleName() + ": " + ex.getMessage(), MAX_SUMMARIZED_ERROR_LENGTH));
    }
  }

  /**
   * Post-mapping: replace a media icon's {@code opencop:media:{mediaId}} href with a stable Core
   * endpoint URL, {@code opencop:url:/v1/layers/{layerId}/media/{mediaId}#WxH}. The {@code
   * opencop:url:} prefix tells the client the image is stored by Core, and the fragment encodes the
   * image's dimensions as a client-side sizing hint (never sent to the server).
   */
  void resolveMediaHrefs(List<Feature> features, UUID layerId, List<KmzIconRef> iconManifest) {
    log.debug(
        "resolveMediaHrefs: {} features, layerId={}, manifest.size={}",
        features.size(),
        layerId,
        iconManifest.size());
    int resolved = 0;
    for (Feature feature : features) {
      Object ocObj = feature.getProperties().get("opencop");
      if (!(ocObj instanceof OpenCopProperties oc)) {
        log.debug(
            "Skipping feature: opencop property is {} (expected OpenCopProperties)",
            ocObj != null ? ocObj.getClass().getName() : "null");
      } else {
        Element element = oc.getElement();
        Object hrefObj = element == null ? null : element.get(MediaIconElement.HREF);
        if (hrefObj instanceof String href && href.startsWith(MEDIA_HREF_PREFIX)) {
          String mediaIdStr = href.substring(MEDIA_HREF_PREFIX.length());
          String fragment = buildDimensionFragment(mediaIdStr, iconManifest);
          String resolvedUrl =
              "opencop:url:/v1/layers/" + layerId + "/media/" + mediaIdStr + fragment;
          element.set(MediaIconElement.HREF, resolvedUrl);
          resolved++;
        }
      }
    }
    log.info("Resolved {}/{} media icon hrefs for layer {}", resolved, features.size(), layerId);
  }

  String buildDimensionFragment(String mediaIdStr, List<KmzIconRef> iconManifest) {
    try {
      UUID mediaId = UUID.fromString(mediaIdStr);
      for (KmzIconRef ref : iconManifest) {
        if (mediaId.equals(ref.mediaId()) && ref.widthPx() != null && ref.heightPx() != null) {
          return "#" + ref.widthPx() + "x" + ref.heightPx();
        }
      }
    } catch (IllegalArgumentException _) {
      // Static message intentional — mediaIdStr is not logged to avoid unbounded cardinality
      // in the hot path. DuplicateMessageFilter deduplicates repeated identical lines.
      log.warn("Skipping dimension lookup: media symbol contained an invalid UUID");
    }
    return "#64x64";
  }

  private String getOptionalStringHeader(Headers headers, String headerName) {
    Header header = headers.lastHeader(headerName);
    if (header != null) {
      var value = new String(header.value(), StandardCharsets.UTF_8);
      if (!value.isEmpty()) {
        return value;
      }
    }
    return null;
  }

  private UUID getOptionalUuidHeader(Headers headers, String headerName)
      throws MissingHeaderException {
    Header header = headers.lastHeader(headerName);
    if (header != null) {
      var value = new String(header.value(), StandardCharsets.UTF_8);
      if (isHeaderCompliant(value)) {
        return UUID.fromString(value);
      }
    }
    return null;
  }

  private UUID getUuidHeader(Headers headers, String headerName) throws MissingHeaderException {
    Header header = headers.lastHeader(headerName);
    if (header != null) {
      var value = new String(header.value(), StandardCharsets.UTF_8);
      if (isHeaderCompliant(value)) {
        return UUID.fromString(value);
      }
    }
    throw new MissingHeaderException(headerName);
  }

  private List<KmzIconRef> deserializeIconRefs(Headers headers) {
    Header header = headers.lastHeader(ICON_REFS.getName());
    if (header == null) {
      return List.of();
    }
    try {
      String json = new String(header.value(), StandardCharsets.UTF_8);
      return List.of(objectMapper.readValue(json, KmzIconRef[].class));
    } catch (Exception e) {
      log.warn("Failed to deserialize ICON_REFS header, ignoring manifest", e);
      return List.of();
    }
  }

  private boolean isHeaderCompliant(Object header) {
    return header instanceof String stringHeader && !stringHeader.isEmpty();
  }

  private String filename(ImportEnvelope envelope) {
    if (envelope == null || envelope.filename() == null || envelope.filename().isBlank()) {
      return "(unknown file)";
    }
    return envelope.filename();
  }

  private String summarize(String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return "(no message)";
    }
    String normalized = value.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= maxLength) {
      return normalized;
    }
    return normalized.substring(0, maxLength - 3) + "...";
  }

  private static final class RawImportBlobAccessException extends RuntimeException {
    private RawImportBlobAccessException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
