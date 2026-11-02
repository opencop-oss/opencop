// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.mapping;

import com.opencop.core.sdk.observability.BoundedMetricTagValue;

/**
 * Bounded telemetry values for the mapping mechanism, independent of mapper ownership or
 * editability. {@link #DYNAMIC} is JSON-rule-backed execution; {@link #JAVA} is execution by a
 * registered Java decoder. {@link #UNKNOWN} means the mechanism was never resolved, such as a
 * decode attempt that threw before or during mapper lookup.
 */
public enum MappingDecoderKind implements BoundedMetricTagValue {
  DYNAMIC("dynamic"),
  JAVA("java"),
  UNKNOWN("unknown");

  private final String value;

  MappingDecoderKind(String value) {
    this.value = value;
  }

  @Override
  public String value() {
    return value;
  }
}
