// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.audit;

import org.springframework.security.access.AccessDeniedException;

public class AuthzAccessDeniedException extends AccessDeniedException {

  private final AuthzReasonCode reasonCode;

  public AuthzAccessDeniedException(AuthzReasonCode reasonCode, String message) {
    super(message);
    this.reasonCode = reasonCode == null ? AuthzReasonCode.UNATTRIBUTED : reasonCode;
  }

  public AuthzReasonCode reasonCode() {
    return reasonCode;
  }
}
