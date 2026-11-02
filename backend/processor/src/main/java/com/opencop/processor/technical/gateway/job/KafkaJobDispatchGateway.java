// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.job;

import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.processor.ports.technical.gateway.job.JobDispatchGateway;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class KafkaJobDispatchGateway implements JobDispatchGateway {

  private static final Logger log = LoggerFactory.getLogger(KafkaJobDispatchGateway.class);

  private final KafkaTemplate<String, byte[]> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final Duration ackTimeout = Duration.ofSeconds(10);

  public KafkaJobDispatchGateway(
      @Qualifier("jobKafkaTemplate") KafkaTemplate<String, byte[]> kafkaTemplate,
      ObjectMapper objectMapper) {
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public void publish(JobSubmission submission) {
    try {
      byte[] payload = objectMapper.writeValueAsBytes(submission);
      kafkaTemplate
          .send(submission.topic(), submission.key(), payload)
          .get(ackTimeout.toMillis(), TimeUnit.MILLISECONDS);
      log.info(
          "Published follow-up job {} of type {} to topic {} with key {}",
          submission.jobId(),
          submission.getClass().getSimpleName(),
          submission.topic(),
          submission.key());
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to publish follow-up job %s of type %s"
              .formatted(submission.jobId(), submission.getClass().getSimpleName()),
          e);
    }
  }
}
