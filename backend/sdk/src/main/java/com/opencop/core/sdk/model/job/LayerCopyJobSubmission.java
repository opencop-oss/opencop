// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.job;

import java.util.Objects;
import java.util.UUID;

public record LayerCopyJobSubmission(
    UUID jobId,
    JobIssuer issuer,
    UUID sourceLayerId,
    UUID targetLayerId,
    boolean copyRemarks,
    boolean copyMedia)
    implements JobSubmission {

  public LayerCopyJobSubmission {
    Objects.requireNonNull(jobId, "jobId");
    Objects.requireNonNull(issuer, "issuer");
    Objects.requireNonNull(sourceLayerId, "sourceLayerId");
    Objects.requireNonNull(targetLayerId, "targetLayerId");
  }

  public static LayerCopyJobSubmission create(
      JobIssuer issuer,
      UUID sourceLayerId,
      UUID targetLayerId,
      boolean copyRemarks,
      boolean copyMedia) {
    return new LayerCopyJobSubmission(
        UUID.randomUUID(), issuer, sourceLayerId, targetLayerId, copyRemarks, copyMedia);
  }

  @Override
  public JobType jobType() {
    return JobType.LAYER_COPY;
  }

  @Override
  public String key() {
    return targetLayerId.toString();
  }
}
