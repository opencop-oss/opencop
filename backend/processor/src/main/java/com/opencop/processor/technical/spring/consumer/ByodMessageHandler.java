// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import com.opencop.core.sdk.model.provenance.FeatureProvenance;
import com.opencop.core.sdk.model.provenance.FeatureProvenance.FeatureSourceKind;
import com.opencop.core.sdk.ports.gateway.broker.BrokerMessage;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedMappingResult;
import com.opencop.core.sdk.technical.gateway.broker.kafka.ProvenanceHeaders;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.EventDecoderRegistry;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.processor.business.model.mapping.MappingResourceKind;
import com.opencop.processor.business.model.mapping.MappingResourceRef;
import com.opencop.processor.business.model.mapping.RealtimeMappingAttempt;
import com.opencop.processor.business.model.mapping.RealtimeMappingEntry;
import com.opencop.processor.business.usecase.event.EventPartitionKeys;
import com.opencop.processor.ports.business.observability.mapping.MappingObservationFailureSink;
import com.opencop.processor.ports.business.observability.mapping.RealtimeMappingOutcomeObserver;
import com.opencop.processor.technical.spring.config.PipelinePublishProperties;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.geojson.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

/**
 * Processes BYOD Kafka records: decode raw bytes → GeoJSON features → mapped producer. Shared
 * singleton passed to each per-topic listener container via {@code setMessageListener()}.
 */
@Component
public class ByodMessageHandler implements MessageListener<String, byte[]> {

  private static final Logger log = LoggerFactory.getLogger(ByodMessageHandler.class);

  /**
   * Leaves room for {@code :partition:offset} inside the {@code varchar(255)} columns an ingress
   * identity reaches: a 10-digit partition and a 19-digit offset with their separators.
   */
  private static final int MAX_TOPIC_LENGTH = 224;

  private final EventDecoderRegistry registry;
  private final MessageBrokerProducer<Feature> producer;
  private final ByodRuntimeObservationCoalescer runtimeObservationCoalescer;
  private final RealtimeMappingOutcomeObserver mappingObservability;
  private final MappingObservationFailureSink observationFailureReporter;
  private final boolean keyedPublish;

  ByodMessageHandler(
      EventDecoderRegistry registry,
      @Qualifier("mappedProducer") MessageBrokerProducer<Feature> producer,
      ByodRuntimeObservationCoalescer runtimeObservationCoalescer,
      RealtimeMappingOutcomeObserver mappingObservability,
      MappingObservationFailureSink observationFailureReporter,
      PipelinePublishProperties publishProperties) {
    this.registry = registry;
    this.producer = producer;
    this.runtimeObservationCoalescer = runtimeObservationCoalescer;
    this.mappingObservability = mappingObservability;
    this.observationFailureReporter = observationFailureReporter;
    this.keyedPublish = publishProperties.isKeyed();
  }

  @Override
  public void onMessage(ConsumerRecord<String, byte[]> consumerRecord) {
    // No message size check — Kafka broker enforces max.message.bytes at ingest.
    var sourceId = extractSourceId(consumerRecord.topic());
    int payloadBytes = consumerRecord.value() == null ? 0 : consumerRecord.value().length;
    log.debug(
        "Consuming BYOD message: topic={}, partition={}, offset={}, sourceId={}, bytes={}",
        consumerRecord.topic(),
        consumerRecord.partition(),
        consumerRecord.offset(),
        sourceId,
        payloadBytes);
    // objectId generated here, not from message — consistent with Connect's RawBytesEventHandler
    var objectId = UUID.randomUUID();
    // Thrown decode failures still propagate to DefaultErrorHandler → retries → DLT; the catch
    // records only the bounded failed_thrown outcome before rethrowing.
    final DecodedMappingResult result;
    try {
      result =
          registry.decodeAndMapRealTimeResult(
              objectId, new MappingProperties(sourceId, null, false), consumerRecord.value());
    } catch (RuntimeException e) {
      observeThrownMapping(sourceId, consumerRecord.partition(), consumerRecord.offset());
      throw e;
    }
    observeMapping(sourceId, consumerRecord.partition(), consumerRecord.offset(), result);
    result.throwIfErrorDiagnostic();
    var features = result.features();
    if (!features.isEmpty()) {
      var headers = ProvenanceHeaders.toHeaders(provenanceFor(consumerRecord, result));
      producer.sendAll(features.stream().map(feature -> mappedMessage(feature, headers)).toList());
    }
    log.debug(
        "BYOD message processed successfully: topic={}, sourceId={}, bytes={}, featureCount={}",
        consumerRecord.topic(),
        sourceId,
        payloadBytes,
        features.size());
    // topic:partition:offset is the message's own identity and survives a redelivery unchanged,
    // so replaying it cannot manufacture new activity for this source.
    runtimeObservationCoalescer.recordSuccess(sourceId, Instant.now(), ingressId(consumerRecord));
  }

  private BrokerMessage<Feature> mappedMessage(Feature feature, Map<String, String> headers) {
    var key = keyedPublish ? EventPartitionKeys.keyFor(mappedProperties(feature)) : null;
    return key == null
        ? BrokerMessage.of(feature, headers)
        : BrokerMessage.withKey(key, feature, headers);
  }

  /**
   * Provenance for everything one BYOD record produced.
   *
   * <p>{@code sourceReceivedAt} is the record's own Kafka timestamp rather than now, so a slow or
   * retried poll reports when the message actually arrived. That is what lets a replay write an old
   * timestamp for the monotonic activity rule to ignore.
   *
   * <p>{@code mappingExecutionId} is derived so an ordinary retry recomputes the same value, while
   * a deliberate remap under a new mapper version takes a new one and keeps the ingress identity.
   */
  private FeatureProvenance provenanceFor(
      ConsumerRecord<String, byte[]> consumerRecord, DecodedMappingResult result) {
    var ingressId = ingressId(consumerRecord);
    return new FeatureProvenance(
        FeatureSourceKind.DATA_SOURCE,
        result.mapperId(),
        result.mapperVersion(),
        ingressId,
        ingressId + ":" + result.mapperVersion(),
        Instant.ofEpochMilli(consumerRecord.timestamp()),
        Instant.now());
  }

  /**
   * The message's permanent address, used both as its provenance ingress identity and as its
   * activity token — deliberately the same string, because they name the same thing: a redelivery
   * carries it unchanged, so neither can manufacture fresh activity.
   */
  /**
   * The message's own identity: stable across a redelivery, distinct per message.
   *
   * <p>The topic is bounded because a BYOD topic is named by the customer and Kafka allows 249
   * characters, while both columns this reaches — {@code geo.features.ingress_id} and {@code
   * profiles.source_runtime_observation.last_message_token} — are {@code varchar(255)}. Truncating
   * the topic rather than the whole value keeps {@code partition:offset} intact, which is the part
   * that distinguishes messages; a source reads one topic, so the same name always truncates the
   * same way, and activity identity is scoped per resource anyway.
   */
  private static String ingressId(ConsumerRecord<String, byte[]> consumerRecord) {
    var topic = consumerRecord.topic();
    if (topic.length() > MAX_TOPIC_LENGTH) {
      topic = topic.substring(0, MAX_TOPIC_LENGTH);
    }
    return topic + ":" + consumerRecord.partition() + ":" + consumerRecord.offset();
  }

  // KLV/mapper-engine decoders attach the parsed properties POJO, which the instanceof check
  // returns without conversion; GeoJSON passthrough attaches the producer's raw map and pays a
  // Jackson conversion here. Key derivation must be total: a malformed producer-controlled node
  // falls back to an unkeyed publish and still fails, exactly as before keying, at the validation
  // stage that owns feature rejection.
  private static OpenCopProperties mappedProperties(Feature feature) {
    Object node = feature.getProperty(OpenCopPropertiesParser.OPENCOP_NODE);
    if (node instanceof OpenCopProperties properties) {
      return properties;
    }
    try {
      return OpenCopPropertiesParser.readFromFeature(feature);
    } catch (JacksonException | IllegalArgumentException _) {
      return null;
    }
  }

  private void observeMapping(
      UUID sourceId, int partition, long offset, DecodedMappingResult result) {
    try {
      mappingObservability.observe(
          new RealtimeMappingAttempt(
              RealtimeMappingEntry.BYOD,
              new MappingResourceRef(MappingResourceKind.DATA_SOURCE, sourceId),
              partition,
              offset),
          result);
    } catch (RuntimeException e) {
      try {
        observationFailureReporter.report(e.getClass());
      } catch (RuntimeException _) {
        // Telemetry must not alter BYOD retry or runtime-success behavior.
      }
    }
  }

  private void observeThrownMapping(UUID sourceId, int partition, long offset) {
    try {
      mappingObservability.observeThrown(
          new RealtimeMappingAttempt(
              RealtimeMappingEntry.BYOD,
              new MappingResourceRef(MappingResourceKind.DATA_SOURCE, sourceId),
              partition,
              offset));
    } catch (RuntimeException e) {
      try {
        observationFailureReporter.report(e.getClass());
      } catch (RuntimeException _) {
        // Telemetry must not alter BYOD retry or runtime-success behavior.
      }
    }
  }

  /**
   * Extracts source UUID from topic name. Topic format: {@code opencop.byod.<slug>.<uuid>}. The
   * source UUID is always the last '.'-delimited segment.
   */
  static UUID extractSourceId(String topic) {
    var lastDot = topic.lastIndexOf('.');
    return UUID.fromString(topic.substring(lastDot + 1));
  }
}
