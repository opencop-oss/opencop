// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.job;

import com.opencop.core.sdk.model.SubjectType;
import java.util.Objects;
import java.util.UUID;

public record HistoricalTracksSubject(UUID jobId, SubjectType subjectType, UUID subjectId) {

  public HistoricalTracksSubject {
    Objects.requireNonNull(jobId, "jobId");
    Objects.requireNonNull(subjectType, "subjectType");
    Objects.requireNonNull(subjectId, "subjectId");
  }
}
