// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import static org.springframework.kafka.support.KafkaHeaders.DLT_EXCEPTION_MESSAGE;

import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.core.sdk.technical.model.serializer.kafka.KafkaGeoJsonDeserializer;
import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import com.opencop.processor.technical.spring.dlt.DeadLetterHeaders;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.geojson.Feature;
import org.geojson.GeoJsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CacheEvictionConsumer {

  private static final Logger log = LoggerFactory.getLogger(CacheEvictionConsumer.class);
  private static final String DUPLICATE_ERROR =
      "ON CONFLICT DO UPDATE command cannot affect row a second time";

  private final GeoCacheGateway cacheGateway;
  private final KafkaGeoJsonDeserializer deserializer = new KafkaGeoJsonDeserializer();

  CacheEvictionConsumer(GeoCacheGateway cacheGateway) {
    this.cacheGateway = cacheGateway;
  }

  // Evicts ghosts that failed to persist: only acts on PERSIST-stage failures, skips true
  // duplicates (data IS in PostGIS — no ghost).
  @KafkaListener(
      id = "persistDltEvictionConsumer",
      topics = "${opencop.messaging-broker.listeners.persist.topic}.dlt",
      groupId = "opencop-persist-dlt-eviction",
      containerFactory = "kafkaListenerContainerFactory")
  public void evictFromDlt(ConsumerRecord<String, byte[]> record) {
    String stage = headerString(record, DeadLetterHeaders.STAGE);
    if (!DeadLetterHeaders.Stage.PERSIST.name().equals(stage)) {
      return;
    }
    String exceptionMessage = headerString(record, DLT_EXCEPTION_MESSAGE);
    if (exceptionMessage != null && exceptionMessage.contains(DUPLICATE_ERROR)) {
      log.debug(
          "Skipping true-duplicate DLT message: partition={}, offset={}",
          record.partition(),
          record.offset());
      return;
    }
    evictFromRecord(record);
  }

  private void evictFromRecord(ConsumerRecord<String, byte[]> record) {
    if (record.value() == null) {
      return;
    }
    try {
      GeoJsonObject geoJson = deserializer.deserialize(record.topic(), record.value());
      if (!(geoJson instanceof Feature feature)) {
        return;
      }
      OpenCopProperties props = OpenCopPropertiesParser.readFromFeature(feature);
      if (props == null || props.getSource() == null || props.getObjectId() == null) {
        return;
      }
      if (props.getTimestampz() == null) {
        log.warn(
            "Skipping ghost eviction — event has no trustworthy timestamp: partition={}, offset={}",
            record.partition(),
            record.offset());
        return;
      }
      cacheGateway.evictObject(
          props.getSource(), props.getObjectId(), props.getTimestampz().getTime());
    } catch (Exception e) {
      log.error(
          "Failed to evict ghost from Redis: partition={}, offset={}",
          record.partition(),
          record.offset(),
          e);
    }
  }

  private static String headerString(ConsumerRecord<?, ?> record, String headerName) {
    Header header = record.headers().lastHeader(headerName);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }
}
