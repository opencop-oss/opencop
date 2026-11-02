// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.job;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record LayerMergeJobSubmission(
    UUID jobId, JobIssuer issuer, UUID targetLayerId, List<UUID> sourceLayerIds)
    implements JobSubmission {

  public LayerMergeJobSubmission {
    Objects.requireNonNull(jobId, "jobId");
    Objects.requireNonNull(issuer, "issuer");
    Objects.requireNonNull(targetLayerId, "targetLayerId");
    Objects.requireNonNull(sourceLayerIds, "sourceLayerIds");
    if (sourceLayerIds.isEmpty()) {
      throw new IllegalArgumentException("sourceLayerIds must contain at least one layer");
    }
    sourceLayerIds = List.copyOf(sourceLayerIds);
  }

  public static LayerMergeJobSubmission create(
      JobIssuer issuer, UUID targetLayerId, List<UUID> sourceLayerIds) {
    return new LayerMergeJobSubmission(UUID.randomUUID(), issuer, targetLayerId, sourceLayerIds);
  }

  @Override
  public JobType jobType() {
    return JobType.LAYER_MERGE;
  }

  @Override
  public String key() {
    return targetLayerId.toString();
  }
}
