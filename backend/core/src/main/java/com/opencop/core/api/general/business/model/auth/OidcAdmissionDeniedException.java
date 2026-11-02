// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.auth;

public class OidcAdmissionDeniedException extends RuntimeException {

  public OidcAdmissionDeniedException() {
    super("OIDC admission denied");
  }

  public OidcAdmissionDeniedException(Throwable cause) {
    super("OIDC admission denied", cause);
  }
}
