// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.kafka;

import com.opencop.core.api.event.technical.observability.history.HistoricalSubmissionTracing;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.core.sdk.observability.history.HistoricalTraceContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class HistoricalTracksKafkaJobPublishStrategy implements KafkaJobPublishStrategy {

  private static final Duration ACK_TIMEOUT = Duration.ofSeconds(10);

  private final KafkaTemplate<String, byte[]> kafkaTemplate;
  private final HistoricalSubmissionTracing tracing;

  public HistoricalTracksKafkaJobPublishStrategy(
      KafkaTemplate<String, byte[]> kafkaTemplate, HistoricalSubmissionTracing tracing) {
    this.kafkaTemplate = kafkaTemplate;
    this.tracing = tracing;
  }

  @Override
  public boolean supports(JobSubmission submission) {
    return submission instanceof HistoricalTracksJobSubmission;
  }

  @Override
  public void publish(JobSubmission submission, byte[] payload) {
    HistoricalTracksJobSubmission historical = (HistoricalTracksJobSubmission) submission;
    tracing.traceKafkaPublish(
        historical,
        () -> {
          ProducerRecord<String, byte[]> record =
              new ProducerRecord<>(historical.topic(), historical.key(), payload);
          addTraceHeaders(record, HistoricalTraceContext.captureCurrent());
          try {
            kafkaTemplate.send(record).get(ACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
          } catch (Exception e) {
            throw new IllegalStateException("Historical Kafka publish failed", e);
          }
        });
  }

  private void addTraceHeaders(ProducerRecord<String, byte[]> record, Map<String, String> headers) {
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      record
          .headers()
          .add(new RecordHeader(entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8)));
    }
  }
}
