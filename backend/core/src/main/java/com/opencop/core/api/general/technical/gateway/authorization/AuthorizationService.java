// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.authorization;

import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("authz")
public class AuthorizationService {

  public boolean isAdmin(Authentication auth) {
    var principal = (OpenCopPrincipal) auth.getPrincipal();
    return isAdmin(principal);
  }

  public boolean isAdmin(OpenCopPrincipal principal) {
    if (principal == null) {
      return false;
    }
    return principal.isGlobalAdmin();
  }
}
