// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.mapping;

import com.opencop.core.sdk.observability.BoundedMetricTagValue;

public enum MappingResourceKind implements BoundedMetricTagValue {
  DATA_SOURCE("data_source");

  private final String value;

  MappingResourceKind(String value) {
    this.value = value;
  }

  @Override
  public String value() {
    return value;
  }
}
