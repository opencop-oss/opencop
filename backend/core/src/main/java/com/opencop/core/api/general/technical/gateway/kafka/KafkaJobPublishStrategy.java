// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.kafka;

import com.opencop.core.sdk.model.job.JobSubmission;

public interface KafkaJobPublishStrategy {

  boolean supports(JobSubmission submission);

  void publish(JobSubmission submission, byte[] payload);
}
