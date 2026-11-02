// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.selector;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.source.SourceRef;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecordSelector(SourceRef source, OnMissing onMissing) {
  public RecordSelector {
    onMissing = onMissing == null ? OnMissing.NULL : onMissing;
  }
}
