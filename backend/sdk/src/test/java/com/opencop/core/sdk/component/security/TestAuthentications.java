// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.component.security;

import com.opencop.core.sdk.business.model.Roles;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class TestAuthentications {

  private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority(Roles.ADMIN);

  private TestAuthentications() {}

  public static UsernamePasswordAuthenticationToken oidcUser() {
    return oidcUser(UUID.randomUUID());
  }

  public static UsernamePasswordAuthenticationToken oidcUser(UUID userId) {
    return new UsernamePasswordAuthenticationToken(
        TestPrincipals.oidcPrincipal(userId), null, List.of());
  }

  public static UsernamePasswordAuthenticationToken oidcAdmin() {
    return oidcAdmin(UUID.randomUUID());
  }

  public static UsernamePasswordAuthenticationToken oidcAdmin(UUID userId) {
    return new UsernamePasswordAuthenticationToken(
        TestPrincipals.oidcPrincipal(userId), null, List.of(ADMIN));
  }

  /**
   * Global-admin caller for request-level authorization decisions: the administrator decision is
   * stamped on the principal itself (what {@code OpenCopPrincipal#isGlobalAdmin} reads), not only
   * on the token authorities that {@link #oidcAdmin(UUID)} grants for URL rules.
   */
  public static UsernamePasswordAuthenticationToken oidcGlobalAdmin(UUID userId) {
    return new UsernamePasswordAuthenticationToken(
        TestPrincipals.oidcPrincipal(userId, true), null, List.of(ADMIN));
  }
}
