// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.account;

import com.opencop.core.sdk.observability.BoundedMetricTagValue;

public enum CurrentAccountAccessDenialReason implements BoundedMetricTagValue {
  INACTIVE("inactive", "The current account is inactive"),
  STALE_GENERATION("stale_generation", "The session generation is stale"),
  MISSING_ACCOUNT("missing_account", "The current account is missing");

  private final String value;
  private final String message;

  CurrentAccountAccessDenialReason(String value, String message) {
    this.value = value;
    this.message = message;
  }

  @Override
  public String value() {
    return value;
  }

  String message() {
    return message;
  }
}
