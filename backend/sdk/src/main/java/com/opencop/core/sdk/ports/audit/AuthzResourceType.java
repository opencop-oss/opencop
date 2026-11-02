// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.audit;

import com.opencop.core.sdk.observability.BoundedMetricTagValue;

public enum AuthzResourceType implements BoundedMetricTagValue {
  UNKNOWN("UNKNOWN"),
  EVENT("EVENT");

  private final String value;

  AuthzResourceType(String value) {
    this.value = value;
  }

  @Override
  public String value() {
    return value;
  }
}
