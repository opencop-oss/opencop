// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import com.opencop.core.sdk.model.job.GhostEvictionJobSubmission;
import com.opencop.processor.ports.business.usecase.event.JobHandler;
import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GhostEvictionJobHandler implements JobHandler<GhostEvictionJobSubmission> {

  private static final Logger log = LoggerFactory.getLogger(GhostEvictionJobHandler.class);

  private final GeoCacheGateway cacheGateway;

  GhostEvictionJobHandler(GeoCacheGateway cacheGateway) {
    this.cacheGateway = cacheGateway;
  }

  @Override
  public Class<GhostEvictionJobSubmission> jobType() {
    return GhostEvictionJobSubmission.class;
  }

  @Override
  public void handle(GhostEvictionJobSubmission job) {
    log.info(
        "Processing ghost eviction job {}: source={}, objectId={}",
        job.jobId(),
        job.sourceId(),
        job.objectId());
    cacheGateway.evictObject(job.sourceId(), job.objectId(), job.deletedAtMs());
  }
}
