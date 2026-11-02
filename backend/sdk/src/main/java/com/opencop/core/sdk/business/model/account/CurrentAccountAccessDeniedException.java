// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.account;

public class CurrentAccountAccessDeniedException extends RuntimeException {

  private final CurrentAccountAccessDenialReason reason;

  public CurrentAccountAccessDeniedException(CurrentAccountAccessDenialReason reason) {
    super(reason.message());
    this.reason = reason;
  }

  public CurrentAccountAccessDenialReason reason() {
    return reason;
  }
}
