// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.mapping;

import com.opencop.core.sdk.observability.BoundedMetricTagValue;

public enum RealtimeMappingOutcome implements BoundedMetricTagValue {
  EMITTED("emitted"),
  PARTIAL_WITH_ERRORS("partial_with_errors"),
  FAILED_ALL_WITH_ERRORS("failed_all_with_errors"),
  FAILED_THROWN("failed_thrown"),
  CONFIG_SUPPRESSED("config_suppressed"),
  ACCEPTED_NO_OUTPUT("accepted_no_output"),
  NO_OUTPUT_UNCLASSIFIED("no_output_unclassified");

  private final String value;

  RealtimeMappingOutcome(String value) {
    this.value = value;
  }

  @Override
  public String value() {
    return value;
  }
}
