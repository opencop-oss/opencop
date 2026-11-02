// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import com.opencop.core.sdk.model.cache.GeoCacheHydrationJob;
import com.opencop.core.sdk.model.job.GeoCacheHydrationJobSubmission;
import com.opencop.core.sdk.model.job.LayerCopyJobSubmission;
import com.opencop.processor.ports.business.usecase.event.JobHandler;
import com.opencop.processor.ports.technical.gateway.db.LayerCopyGateway;
import com.opencop.processor.ports.technical.gateway.job.JobDispatchGateway;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LayerCopyHandler implements JobHandler<LayerCopyJobSubmission> {

  private static final Logger log = LoggerFactory.getLogger(LayerCopyHandler.class);

  private final LayerCopyGateway layerCopyGateway;
  private final JobDispatchGateway jobDispatchGateway;

  public LayerCopyHandler(
      LayerCopyGateway layerCopyGateway, JobDispatchGateway jobDispatchGateway) {
    this.layerCopyGateway = layerCopyGateway;
    this.jobDispatchGateway = jobDispatchGateway;
  }

  @Override
  public Class<LayerCopyJobSubmission> jobType() {
    return LayerCopyJobSubmission.class;
  }

  @Override
  public void handle(LayerCopyJobSubmission job) {
    Instant startedAt = Instant.now();
    log.info(
        "Starting layer copy job {} from source {} to target {} initiated by {} with copyRemarks={} and copyMedia={}",
        job.jobId(),
        job.sourceLayerId(),
        job.targetLayerId(),
        job.issuer(),
        job.copyRemarks(),
        job.copyMedia());

    if (layerCopyGateway.targetHasFeatures(job.targetLayerId())) {
      log.info(
          "Skipping layer copy for job {} because target {} already has features; proceeding to hydration dispatch",
          job.jobId(),
          job.targetLayerId());
    } else {
      layerCopyGateway.copyLayer(
          job.sourceLayerId(), job.targetLayerId(), job.copyRemarks(), job.copyMedia());

      log.info(
          "Completed layer copy job {} from source {} to target {} initiated by {} in {} ms",
          job.jobId(),
          job.sourceLayerId(),
          job.targetLayerId(),
          job.issuer(),
          Duration.between(startedAt, Instant.now()).toMillis());
    }

    var hydrationJob =
        GeoCacheHydrationJobSubmission.forSource(
            job.issuer(), job.targetLayerId(), GeoCacheHydrationJob.Trigger.LAYER_CLONE);
    jobDispatchGateway.publish(hydrationJob);
    log.info(
        "Dispatched geo cache hydration job {} for copied layer {} after layer copy job {} initiated by {}",
        hydrationJob.jobId(),
        job.targetLayerId(),
        job.jobId(),
        job.issuer());
  }
}
