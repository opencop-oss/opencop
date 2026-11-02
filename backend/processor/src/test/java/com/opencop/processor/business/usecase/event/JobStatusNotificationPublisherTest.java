// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.cache.GeoCacheHydrationJob;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.GeoCacheHydrationJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.job.JobType;
import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.model.notification.content.JobStatusNotification;
import com.opencop.core.sdk.model.notification.content.JobStatusType;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class JobStatusNotificationPublisherTest {

  @SuppressWarnings("unchecked")
  private final MessageBrokerProducer<byte[]> notificationProducer =
      mock(MessageBrokerProducer.class);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JobStatusNotificationPublisher publisher =
      new JobStatusNotificationPublisher(notificationProducer, objectMapper);

  @Test
  void notifySuccess_whenIssuerIsUser_publishesNotification() {
    var job =
        GeoCacheHydrationJobSubmission.forSource(
            JobIssuer.user(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655446099")),
            java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655446001"),
            GeoCacheHydrationJob.Trigger.MANUAL);

    publisher.notifySuccess(job);

    var payloadCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(notificationProducer).send(payloadCaptor.capture());

    OpencopNotification notification =
        objectMapper.readValue(payloadCaptor.getValue(), OpencopNotification.class);
    var content = (JobStatusNotification) notification.content();
    assertEquals(job.jobId(), content.jobId());
    assertEquals(JobType.GEO_CACHE_HYDRATION, content.jobType());
    assertEquals(JobStatusType.SUCCEEDED, content.status());
  }

  @Test
  void notifyFailure_whenIssuerIsSystem_skipsNotification() {
    var job =
        GeoCacheHydrationJobSubmission.forSource(
            JobIssuer.system("job-runner"),
            java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655446001"),
            GeoCacheHydrationJob.Trigger.MANUAL);

    publisher.notifyFailure(job, new IllegalStateException("boom"));

    verify(notificationProducer, never()).send(any(byte[].class));
  }

  @Test
  void notifySuccess_whenHistoricalIssuerIsUser_publishesHistoricalJobNotification()
      throws Exception {
    var job =
        HistoricalTracksJobSubmission.create(
            java.util.UUID.randomUUID(),
            JobIssuer.user(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655446099")),
            SubjectType.SOURCE,
            java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655446001"),
            java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655446099"),
            java.time.Instant.parse("2026-04-07T10:00:00Z"),
            java.time.Instant.parse("2026-04-06T10:00:00Z"),
            java.time.Instant.parse("2026-04-06T11:00:00Z"),
            java.time.Instant.parse("2026-04-06T10:00:00Z"),
            java.time.Instant.parse("2026-04-06T11:00:00Z"),
            HistoricalResolution.HOUR_1,
            1,
            java.util.List.of());

    publisher.notifySuccess(job);

    var payloadCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(notificationProducer).send(payloadCaptor.capture());

    OpencopNotification notification =
        objectMapper.readValue(payloadCaptor.getValue(), OpencopNotification.class);
    var content = (JobStatusNotification) notification.content();
    assertEquals(job.jobId(), content.jobId());
    assertEquals(JobType.HISTORICAL_TRACKS, content.jobType());
    assertEquals(JobStatusType.SUCCEEDED, content.status());
  }
}
