// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.audit;

import com.opencop.core.sdk.observability.BoundedMetricTagValue;

public enum AuthzPrincipalType implements BoundedMetricTagValue {
  AUTHENTICATED("AUTHENTICATED"),
  ANONYMOUS("ANONYMOUS"),
  UNKNOWN("UNKNOWN");

  private final String value;

  AuthzPrincipalType(String value) {
    this.value = value;
  }

  @Override
  public String value() {
    return value;
  }
}
