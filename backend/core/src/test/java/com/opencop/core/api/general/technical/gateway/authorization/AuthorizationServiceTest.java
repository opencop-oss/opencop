// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.authorization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class AuthorizationServiceTest {

  private final AuthorizationService service = new AuthorizationService();

  @Test
  void isAdmin_whenCurrentAuthorityGrantsAdminReturnsTrue() {
    assertTrue(service.isAdmin(principal(false, true)));
  }

  @Test
  void isAdmin_whenCurrentAuthorityDemotesLoginAdminReturnsFalse() {
    assertFalse(service.isAdmin(principal(true, false)));
  }

  @Test
  void isAdmin_whenAuthenticationContainsCurrentPrincipalUsesCurrentAuthority() {
    var authentication = mock(Authentication.class);
    when(authentication.getPrincipal()).thenReturn(principal(true, false));

    assertFalse(service.isAdmin(authentication));
  }

  @Test
  void isAdmin_whenPrincipalIsNullReturnsFalse() {
    assertFalse(service.isAdmin((OpenCopPrincipal) null));
  }

  private static OpenCopPrincipal principal(boolean loginAdmin, boolean currentAdmin) {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(
            UUID.randomUUID(), loginAdmin)
        .withRequestCurrentGlobalAdministrator(currentAdmin);
  }
}
