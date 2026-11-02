// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.job;

import com.opencop.core.sdk.model.job.JobSubmission;

public interface JobDispatchGateway {

  void publish(JobSubmission submission);
}
