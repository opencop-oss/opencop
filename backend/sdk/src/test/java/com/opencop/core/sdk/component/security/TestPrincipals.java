// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.component.security;

import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.UUID;

public final class TestPrincipals {

  private TestPrincipals() {}

  public static OpenCopPrincipal mockPrincipal(UUID userId) {
    return oidcPrincipal(userId);
  }

  public static OpenCopPrincipal oidcPrincipal(UUID userId) {
    return oidcPrincipal(userId, 0);
  }

  public static OpenCopPrincipal oidcPrincipal(UUID userId, boolean globalAdministrator) {
    return oidcPrincipal(userId).withRequestCurrentGlobalAdministrator(globalAdministrator);
  }

  public static OpenCopPrincipal oidcPrincipal(UUID userId, long sessionGeneration) {
    return new OpenCopPrincipal(
        userId,
        "http://localhost/test-issuer",
        "test-subject",
        "test-subject",
        new OpenCopPrincipal.Profile(null, null, null, null, null, null),
        sessionGeneration,
        Instant.EPOCH);
  }
}
