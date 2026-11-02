// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.processor.ports.business.usecase.event.JobHandler;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JobHandlerRegistry {

  private final Map<Class<? extends JobSubmission>, JobHandler<? extends JobSubmission>> handlers;

  public JobHandlerRegistry(List<JobHandler<? extends JobSubmission>> handlers) {
    this.handlers = indexHandlers(handlers);
  }

  public void dispatch(JobSubmission job) {
    JobHandler<? extends JobSubmission> handler = handlers.get(job.getClass());
    if (handler == null) {
      throw new IllegalStateException(
          "No job handler registered for submission type " + job.getClass().getName());
    }
    dispatchTyped(job, handler);
  }

  private static Map<Class<? extends JobSubmission>, JobHandler<? extends JobSubmission>>
      indexHandlers(List<JobHandler<? extends JobSubmission>> handlers) {
    var indexedHandlers =
        new LinkedHashMap<Class<? extends JobSubmission>, JobHandler<? extends JobSubmission>>();
    for (JobHandler<? extends JobSubmission> handler : handlers) {
      JobHandler<? extends JobSubmission> previous =
          indexedHandlers.putIfAbsent(handler.jobType(), handler);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate job handler registered for submission type " + handler.jobType().getName());
      }
    }
    return Map.copyOf(indexedHandlers);
  }

  private static <T extends JobSubmission> void dispatchTyped(
      JobSubmission job, JobHandler<T> handler) {
    handler.handle(handler.jobType().cast(job));
  }
}
