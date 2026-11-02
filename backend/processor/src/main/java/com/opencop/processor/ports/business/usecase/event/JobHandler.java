// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.business.usecase.event;

import com.opencop.core.sdk.model.job.JobSubmission;

public interface JobHandler<T extends JobSubmission> {

  Class<T> jobType();

  void handle(T job);
}
