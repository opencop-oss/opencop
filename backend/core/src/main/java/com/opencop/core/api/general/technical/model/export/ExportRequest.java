// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.export;

import com.opencop.core.sdk.model.SubjectType;
import java.util.List;
import java.util.UUID;

public record ExportRequest(Format format, List<Subject> subjects, List<UUID> elementIds) {

  public record Subject(SubjectType subjectType, UUID subjectId, Boolean includeTransitiveData) {

    public boolean includeTransitiveDataOrDefault() {
      return includeTransitiveData != null && includeTransitiveData;
    }
  }

  public enum Format {
    CSV
  }
}
