// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.mapping;

import com.opencop.core.sdk.observability.BoundedMetricTagValue;

public enum RealtimeMappingEntry implements BoundedMetricTagValue {
  BYOD("byod"),
  RAW_REALTIME("raw_realtime");

  private final String value;

  RealtimeMappingEntry(String value) {
    this.value = value;
  }

  @Override
  public String value() {
    return value;
  }
}
