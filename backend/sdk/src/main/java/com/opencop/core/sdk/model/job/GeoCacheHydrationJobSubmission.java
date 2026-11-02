// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.job;

import com.opencop.core.sdk.model.cache.GeoCacheHydrationJob;
import java.util.Objects;
import java.util.UUID;

public record GeoCacheHydrationJobSubmission(
    UUID jobId,
    JobIssuer issuer,
    UUID sourceId,
    GeoCacheHydrationJob.Mode mode,
    GeoCacheHydrationJob.Trigger trigger)
    implements JobSubmission {

  public GeoCacheHydrationJobSubmission {
    Objects.requireNonNull(jobId, "jobId");
    Objects.requireNonNull(issuer, "issuer");
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(trigger, "trigger");
  }

  public static GeoCacheHydrationJobSubmission forSource(
      JobIssuer issuer, UUID sourceId, GeoCacheHydrationJob.Trigger trigger) {
    return new GeoCacheHydrationJobSubmission(
        UUID.randomUUID(), issuer, sourceId, GeoCacheHydrationJob.Mode.LATEST_ACTIVE_ONLY, trigger);
  }

  @Override
  public JobType jobType() {
    return JobType.GEO_CACHE_HYDRATION;
  }

  @Override
  public String key() {
    return sourceId.toString();
  }
}
