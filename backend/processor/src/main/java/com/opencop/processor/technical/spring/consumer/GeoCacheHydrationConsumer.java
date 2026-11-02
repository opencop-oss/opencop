// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.processor.business.usecase.event.JobHandlerRegistry;
import com.opencop.processor.business.usecase.event.JobStatusNotificationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class GeoCacheHydrationConsumer {

  private static final Logger log = LoggerFactory.getLogger(GeoCacheHydrationConsumer.class);

  private final JobHandlerRegistry handlerRegistry;
  private final JobStatusNotificationPublisher jobStatusNotificationPublisher;
  private final ObjectMapper objectMapper;

  GeoCacheHydrationConsumer(
      JobHandlerRegistry handlerRegistry,
      JobStatusNotificationPublisher jobStatusNotificationPublisher,
      ObjectMapper objectMapper) {
    this.handlerRegistry = handlerRegistry;
    this.jobStatusNotificationPublisher = jobStatusNotificationPublisher;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      id = "jobsConsumer",
      topics = "${opencop.messaging-broker.listeners.jobs.topic}",
      containerFactory = "jobsFactory")
  public void hydrate(byte[] payload) {
    JobSubmission job = null;
    try {
      job = objectMapper.readValue(payload, JobSubmission.class);
      log.info(
          "Dispatching job {} of type {} initiated by {} from payload ({} bytes)",
          job.jobId(),
          job.getClass().getSimpleName(),
          job.issuer(),
          payload.length);
      handlerRegistry.dispatch(job);
      jobStatusNotificationPublisher.notifySuccess(job);
    } catch (Exception e) {
      log.error("Failed to dispatch job from payload ({} bytes)", payload.length, e);
      throw new IllegalStateException("Failed to dispatch job", e);
    }
  }
}
