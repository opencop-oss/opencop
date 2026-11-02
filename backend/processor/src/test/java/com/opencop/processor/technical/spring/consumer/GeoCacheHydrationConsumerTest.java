// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.cache.GeoCacheHydrationJob;
import com.opencop.core.sdk.model.job.GeoCacheHydrationJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.usecase.event.JobHandlerRegistry;
import com.opencop.processor.business.usecase.event.JobStatusNotificationPublisher;
import com.opencop.processor.ports.business.usecase.event.JobHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GeoCacheHydrationConsumerTest {

  @SuppressWarnings("unchecked")
  private final JobHandler<GeoCacheHydrationJobSubmission> hydrator = mock(JobHandler.class);

  private final JobStatusNotificationPublisher jobStatusNotificationPublisher =
      mock(JobStatusNotificationPublisher.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JobHandlerRegistry handlerRegistry;
  private final GeoCacheHydrationConsumer consumer;

  GeoCacheHydrationConsumerTest() {
    when(hydrator.jobType()).thenReturn(GeoCacheHydrationJobSubmission.class);
    handlerRegistry = new JobHandlerRegistry(List.of(hydrator));
    consumer =
        new GeoCacheHydrationConsumer(
            handlerRegistry, jobStatusNotificationPublisher, objectMapper);
  }

  @Test
  void hydrate_deserializesJobSubmissionPayload_andDelegatesToMatchingHandler() throws Exception {
    var job =
        new GeoCacheHydrationJobSubmission(
            UUID.randomUUID(),
            JobIssuer.system("consumer-test"),
            UUID.randomUUID(),
            GeoCacheHydrationJob.Mode.LATEST_ACTIVE_ONLY,
            GeoCacheHydrationJob.Trigger.MANUAL);

    consumer.hydrate(objectMapper.writeValueAsBytes(job));

    verify(hydrator).handle(job);
    verify(jobStatusNotificationPublisher).notifySuccess(job);
  }

  @Test
  void hydrate_whenPayloadIsInvalid_throwsAndDoesNotDelegate() {
    assertThrows(IllegalStateException.class, () -> consumer.hydrate("{".getBytes()));

    verify(hydrator, never()).handle(any());
    verify(jobStatusNotificationPublisher, never()).notifyFailure(any(), any());
  }

  @Test
  void hydrate_whenNoHandlerMatches_throws() throws Exception {
    var unsupportedConsumer =
        new GeoCacheHydrationConsumer(
            new JobHandlerRegistry(java.util.List.of()),
            jobStatusNotificationPublisher,
            objectMapper);
    var job =
        GeoCacheHydrationJobSubmission.forSource(
            JobIssuer.system("consumer-test"),
            UUID.randomUUID(),
            GeoCacheHydrationJob.Trigger.MANUAL);

    assertThrows(
        IllegalStateException.class,
        () -> unsupportedConsumer.hydrate(objectMapper.writeValueAsBytes(job)));
    verify(jobStatusNotificationPublisher, never()).notifyFailure(any(), any());
  }
}
