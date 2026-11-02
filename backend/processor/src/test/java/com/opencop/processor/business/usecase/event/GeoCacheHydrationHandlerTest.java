// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.cache.GeoCacheHydrationJob;
import com.opencop.core.sdk.model.job.GeoCacheHydrationJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import com.opencop.processor.ports.technical.gateway.db.GeoCacheHydrationGateway.HydrationSnapshotStats;
import com.opencop.processor.ports.technical.gateway.lock.geo.GeoCacheHydrationLock;
import com.opencop.processor.technical.gateway.db.GeoCacheHydrationRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.geojson.Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GeoCacheHydrationHandlerTest {

  private static final UUID SOURCE_ID = UUID.fromString("fd8d9766-8f9d-4a0f-b621-6d4f5417f8ee");
  private static final JobIssuer ISSUER =
      JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446024"));
  private static final Duration LOCK_TTL = Duration.ofMinutes(30);

  private final GeoCacheHydrationRepository repository = mock(GeoCacheHydrationRepository.class);
  private final GeoCacheGateway cacheGateway = mock(GeoCacheGateway.class);
  private final GeoCacheHydrationLock lockGateway = mock(GeoCacheHydrationLock.class);

  private GeoCacheHydrationHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GeoCacheHydrationHandler(repository, cacheGateway, lockGateway);
  }

  @Test
  void hydrate_publishesUpdateForEveryPage() {
    var job =
        GeoCacheHydrationJobSubmission.forSource(
            ISSUER, SOURCE_ID, GeoCacheHydrationJob.Trigger.LAYER_CLONE);
    var page1 = List.of(new Feature(), new Feature());
    var page2 = List.of(new Feature());

    when(lockGateway.tryAcquire(any(), any(), any())).thenReturn(true);
    when(repository.hydrateSnapshot(eq(SOURCE_ID), eq(1000), any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<List<Feature>> consumer = invocation.getArgument(2, Consumer.class);
              consumer.accept(page1);
              consumer.accept(page2);
              return new HydrationSnapshotStats(2, 3, "object-3");
            });

    handler.hydrate(job);

    verify(lockGateway).tryAcquire(SOURCE_ID, job.jobId().toString(), LOCK_TTL);
    verify(cacheGateway).write(page1, true);
    verify(cacheGateway).write(page2, true);
    verify(cacheGateway, times(2)).write(any(), eq(true));
    verify(lockGateway).release(SOURCE_ID, job.jobId().toString());
  }

  @Test
  void hydrate_whenLockIsAcquired_loadsSnapshotAndWritesCachePages() {
    var job =
        GeoCacheHydrationJobSubmission.forSource(
            ISSUER, SOURCE_ID, GeoCacheHydrationJob.Trigger.MANUAL);
    var features = List.of(new Feature(), new Feature());

    when(lockGateway.tryAcquire(any(), any(), any())).thenReturn(true);
    when(repository.hydrateSnapshot(eq(SOURCE_ID), eq(1000), any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<List<Feature>> consumer = invocation.getArgument(2, Consumer.class);
              consumer.accept(features);
              return new HydrationSnapshotStats(1, features.size(), "object-2");
            });

    handler.hydrate(job);

    verify(lockGateway).tryAcquire(SOURCE_ID, job.jobId().toString(), LOCK_TTL);
    verify(repository).hydrateSnapshot(eq(SOURCE_ID), eq(1000), any());
    verify(cacheGateway).write(features, true);
    verify(lockGateway).release(SOURCE_ID, job.jobId().toString());
  }

  @Test
  void hydrate_whenLockIsAlreadyHeld_skipsHydration() {
    var job =
        GeoCacheHydrationJobSubmission.forSource(
            ISSUER, SOURCE_ID, GeoCacheHydrationJob.Trigger.MANUAL);

    when(lockGateway.tryAcquire(any(), any(), any())).thenReturn(false);

    handler.hydrate(job);

    verify(lockGateway).tryAcquire(SOURCE_ID, job.jobId().toString(), LOCK_TTL);
    verify(repository, never()).hydrateSnapshot(any(), eq(1000), any());
    verify(cacheGateway, never()).write(any(), eq(true));
    verify(lockGateway, never()).release(any(), any());
  }

  @Test
  void hydrate_whenSnapshotFails_releasesLockAndRethrows() {
    var job =
        GeoCacheHydrationJobSubmission.forSource(
            ISSUER, SOURCE_ID, GeoCacheHydrationJob.Trigger.MANUAL);

    when(lockGateway.tryAcquire(any(), any(), any())).thenReturn(true);
    when(repository.hydrateSnapshot(eq(SOURCE_ID), eq(1000), any()))
        .thenThrow(new IllegalStateException("boom"));

    assertThrows(IllegalStateException.class, () -> handler.hydrate(job));

    verify(lockGateway).release(SOURCE_ID, job.jobId().toString());
  }

  @Test
  void hydrate_passesSourceIdentityAndOwnerToLockBoundary() {
    var job =
        GeoCacheHydrationJobSubmission.forSource(
            ISSUER, SOURCE_ID, GeoCacheHydrationJob.Trigger.MANUAL);
    var handler = new GeoCacheHydrationHandler(repository, cacheGateway, lockGateway);
    when(lockGateway.tryAcquire(SOURCE_ID, job.jobId().toString(), LOCK_TTL)).thenReturn(true);
    when(repository.hydrateSnapshot(eq(SOURCE_ID), eq(1000), any()))
        .thenReturn(new HydrationSnapshotStats(0, 0, null));

    handler.hydrate(job);

    verify(lockGateway).tryAcquire(SOURCE_ID, job.jobId().toString(), LOCK_TTL);
    verify(lockGateway).release(SOURCE_ID, job.jobId().toString());
  }
}
