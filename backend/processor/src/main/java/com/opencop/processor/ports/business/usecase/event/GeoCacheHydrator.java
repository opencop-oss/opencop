// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.business.usecase.event;

import com.opencop.core.sdk.model.job.GeoCacheHydrationJobSubmission;

public interface GeoCacheHydrator extends JobHandler<GeoCacheHydrationJobSubmission> {

  void hydrate(GeoCacheHydrationJobSubmission job);

  @Override
  default Class<GeoCacheHydrationJobSubmission> jobType() {
    return GeoCacheHydrationJobSubmission.class;
  }

  @Override
  default void handle(GeoCacheHydrationJobSubmission job) {
    hydrate(job);
  }
}
