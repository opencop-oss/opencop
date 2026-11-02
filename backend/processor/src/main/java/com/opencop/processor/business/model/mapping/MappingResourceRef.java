// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.mapping;

import java.util.Objects;
import java.util.UUID;

public record MappingResourceRef(MappingResourceKind kind, UUID id) {

  public MappingResourceRef {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(id, "id");
  }
}
