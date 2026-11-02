// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.history;

import com.opencop.core.sdk.model.SubjectType;
import java.util.Objects;
import java.util.UUID;

public record HistoricalSubjectRequest(SubjectType subjectType, UUID subjectId) {

  public HistoricalSubjectRequest {
    Objects.requireNonNull(subjectType, "subjectType");
    Objects.requireNonNull(subjectId, "subjectId");
  }
}
