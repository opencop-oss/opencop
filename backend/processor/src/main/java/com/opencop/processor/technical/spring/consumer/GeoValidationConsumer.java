// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import com.opencop.core.sdk.technical.gateway.broker.kafka.ProvenanceHeaders;
import com.opencop.processor.business.model.event.EventEnvelope;
import com.opencop.processor.ports.business.usecase.event.ValidateEvent;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.geojson.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class GeoValidationConsumer {

  private static final Logger log = LoggerFactory.getLogger(GeoValidationConsumer.class);

  private final ValidateEvent<Feature> usecase;

  GeoValidationConsumer(final ValidateEvent<Feature> usecase) {
    this.usecase = usecase;
  }

  /**
   * Consumes {@code ConsumerRecord}s rather than bare features so provenance attached upstream
   * survives this hop.
   *
   * <p>This is the Kafka boundary: it converts records into transport-neutral envelopes carrying
   * only the metadata {@link ProvenanceHeaders} allows through, so the validation use case forwards
   * provenance to the persist topic without naming a Kafka type or knowing what any header means.
   */
  @KafkaListener(
      id = "validationEventConsumer",
      topics = "${opencop.messaging-broker.listeners.validation.topic}",
      containerFactory = "validationFactory")
  public void validate(List<ConsumerRecord<String, Feature>> records) {
    long startedAtNanos = System.nanoTime();
    log.debug("Consuming validation batch: count={}", records.size());

    try {
      usecase.validate(records.stream().map(GeoValidationConsumer::envelope).toList());
      log.info(
          "Validation batch processed successfully: count={}, durationMs={}",
          records.size(),
          ConsumerLogSupport.elapsedMillisSince(startedAtNanos));
    } catch (Exception e) {
      log.error(
          "Validation batch failed: count={}, durationMs={}, featureIds={}",
          records.size(),
          ConsumerLogSupport.elapsedMillisSince(startedAtNanos),
          ConsumerLogSupport.featureIds(records.stream().map(ConsumerRecord::value).toList()),
          e);
      throw e;
    }
  }

  private static EventEnvelope<Feature> envelope(ConsumerRecord<String, Feature> record) {
    return new EventEnvelope<>(record.value(), ProvenanceHeaders.forward(record.headers()));
  }
}
