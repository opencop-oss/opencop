// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.mapping;

import java.util.Objects;

public record RealtimeMappingAttempt(
    RealtimeMappingEntry ingress, MappingResourceRef resource, int partition, long offset) {

  public RealtimeMappingAttempt {
    Objects.requireNonNull(ingress, "ingress");
    Objects.requireNonNull(resource, "resource");
    if (partition < 0) {
      throw new IllegalArgumentException("partition must be nonnegative");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be nonnegative");
    }
  }
}
