// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.cache.GeoCacheHydrationJob;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.GeoCacheHydrationJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.ports.business.usecase.event.JobHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobHandlerRegistryTest {

  @Test
  void dispatch_routesToHandlerRegisteredForSubmissionClass() {
    @SuppressWarnings("unchecked")
    JobHandler<GeoCacheHydrationJobSubmission> handler = mock(JobHandler.class);
    when(handler.jobType()).thenReturn(GeoCacheHydrationJobSubmission.class);
    var registry = new JobHandlerRegistry(List.of(handler));
    var job =
        GeoCacheHydrationJobSubmission.forSource(
            JobIssuer.system("registry-test"),
            UUID.randomUUID(),
            GeoCacheHydrationJob.Trigger.MANUAL);

    registry.dispatch(job);

    verify(handler).handle(job);
  }

  @Test
  void dispatch_whenHandlerMissing_throws() {
    var registry = new JobHandlerRegistry(List.of());
    var job =
        GeoCacheHydrationJobSubmission.forSource(
            JobIssuer.system("registry-test"),
            UUID.randomUUID(),
            GeoCacheHydrationJob.Trigger.MANUAL);

    assertThrows(IllegalStateException.class, () -> registry.dispatch(job));
  }

  @Test
  void dispatch_routesToHistoricalHandlerRegisteredForSubmissionClass() {
    @SuppressWarnings("unchecked")
    JobHandler<HistoricalTracksJobSubmission> handler = mock(JobHandler.class);
    when(handler.jobType()).thenReturn(HistoricalTracksJobSubmission.class);
    var registry = new JobHandlerRegistry(List.of(handler));
    var job =
        HistoricalTracksJobSubmission.create(
            UUID.randomUUID(),
            JobIssuer.system("registry-test"),
            SubjectType.SOURCE,
            UUID.randomUUID(),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
            Instant.parse("2026-04-07T10:00:00Z"),
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            HistoricalResolution.HOUR_1,
            1,
            List.of());

    registry.dispatch(job);

    verify(handler).handle(job);
  }
}
