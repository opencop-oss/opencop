// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.job;

import com.opencop.core.sdk.model.job.JobSubmission;
import java.util.List;
import java.util.function.Consumer;

public interface JobPublisher {

  void publishNow(JobSubmission submission);

  default void scheduleAfterCommit(JobSubmission submission) {
    scheduleBatchAfterCommit(List.of(submission), () -> {});
  }

  default void scheduleAfterCommit(
      JobSubmission submission, Consumer<Exception> afterFailedPublish) {
    scheduleBatchAfterCommit(List.of(submission), () -> {}, afterFailedPublish);
  }

  void scheduleBatchAfterCommit(
      List<? extends JobSubmission> submissions, Runnable afterSuccessfulBatch);

  default void scheduleBatchAfterCommit(
      List<? extends JobSubmission> submissions,
      Runnable afterSuccessfulBatch,
      Consumer<Exception> afterFailedPublish) {
    scheduleBatchAfterCommit(submissions, afterSuccessfulBatch);
  }
}
