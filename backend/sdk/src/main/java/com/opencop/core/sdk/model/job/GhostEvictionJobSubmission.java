// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.job;

import java.util.Objects;
import java.util.UUID;

public record GhostEvictionJobSubmission(
    UUID jobId, JobIssuer issuer, UUID sourceId, String objectId, long deletedAtMs)
    implements JobSubmission {

  public GhostEvictionJobSubmission {
    Objects.requireNonNull(jobId, "jobId");
    Objects.requireNonNull(issuer, "issuer");
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(objectId, "objectId");
  }

  public static GhostEvictionJobSubmission create(
      JobIssuer issuer, UUID sourceId, String objectId) {
    return new GhostEvictionJobSubmission(
        UUID.randomUUID(), issuer, sourceId, objectId, System.currentTimeMillis());
  }

  @Override
  public JobType jobType() {
    return JobType.GHOST_EVICTION;
  }

  @Override
  public String key() {
    return sourceId.toString();
  }
}
