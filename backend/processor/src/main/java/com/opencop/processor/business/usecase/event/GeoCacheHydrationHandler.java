// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import com.opencop.core.sdk.model.job.GeoCacheHydrationJobSubmission;
import com.opencop.processor.ports.business.usecase.event.GeoCacheHydrator;
import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import com.opencop.processor.ports.technical.gateway.lock.geo.GeoCacheHydrationLock;
import com.opencop.processor.technical.gateway.db.GeoCacheHydrationRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GeoCacheHydrationHandler implements GeoCacheHydrator {

  private static final Logger log = LoggerFactory.getLogger(GeoCacheHydrationHandler.class);
  private static final int PAGE_SIZE = 1000;
  private static final Duration LOCK_TTL = Duration.ofMinutes(30);

  private final GeoCacheHydrationRepository repository;
  private final GeoCacheGateway cacheWriter;
  private final GeoCacheHydrationLock lockGateway;

  public GeoCacheHydrationHandler(
      GeoCacheHydrationRepository repository,
      GeoCacheGateway cacheWriter,
      GeoCacheHydrationLock lockGateway) {
    this.repository = repository;
    this.cacheWriter = cacheWriter;
    this.lockGateway = lockGateway;
  }

  @Override
  public void hydrate(GeoCacheHydrationJobSubmission job) {
    String lockValue = job.jobId().toString();
    Instant startedAt = Instant.now();

    log.info(
        "Starting geo cache hydration job {} for source {} initiated by {} with mode {} and trigger {}",
        job.jobId(),
        job.sourceId(),
        job.issuer(),
        job.mode(),
        job.trigger());

    if (!lockGateway.tryAcquire(job.sourceId(), lockValue, LOCK_TTL)) {
      log.info(
          "Skipping geo cache hydration job {} for source {} initiated by {} because another job already holds its hydration lock",
          job.jobId(),
          job.sourceId(),
          job.issuer());
      return;
    }

    try {
      log.info(
          "Acquired hydration lock for job {} on source {} initiated by {}",
          job.jobId(),
          job.sourceId(),
          job.issuer());
      var stats =
          repository.hydrateSnapshot(
              job.sourceId(), PAGE_SIZE, page -> cacheWriter.write(page, true));

      log.info(
          "Completed geo cache hydration job {} for source {} initiated by {} with mode {}, pages={}, features={}, lastObjectId={}, durationMs={}",
          job.jobId(),
          job.sourceId(),
          job.issuer(),
          job.mode(),
          stats.pageCount(),
          stats.featureCount(),
          stats.lastObjectId(),
          Duration.between(startedAt, Instant.now()).toMillis());
    } catch (Exception e) {
      log.error(
          "Geo cache hydration job {} failed for source {} initiated by {} after {} ms",
          job.jobId(),
          job.sourceId(),
          job.issuer(),
          Duration.between(startedAt, Instant.now()).toMillis(),
          e);
      throw e;
    } finally {
      lockGateway.release(job.sourceId(), lockValue);
      log.debug("Released hydration lock for source {} and owner {}", job.sourceId(), lockValue);
    }
  }
}
