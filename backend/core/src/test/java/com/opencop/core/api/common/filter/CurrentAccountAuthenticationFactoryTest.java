// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.business.model.Roles;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.component.security.TestPrincipals;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

class CurrentAccountAuthenticationFactoryTest {

  private static final UUID USER_ID = UUID.fromString("b70d53f2-87e1-45da-a354-6072849f4602");

  private final CurrentAccountAuthenticationFactory factory =
      new CurrentAccountAuthenticationFactory();

  @Test
  void currentNonAdministrator_usesTokenAuthoritiesAndStripsEveryStoredAdministratorAuthority() {
    var sessionPrincipal = TestPrincipals.oidcPrincipal(USER_ID, true);
    var sessionAuthentication =
        authentication(
            sessionPrincipal,
            List.of(
                authority("SCOPE_profile"),
                authority(Roles.ADMIN),
                authority("OIDC_USER"),
                authority(Roles.ADMIN)));
    var details = Map.of("request", "details");
    sessionAuthentication.setDetails(details);

    var requestAuthentication =
        (OAuth2AuthenticationToken) factory.create(sessionAuthentication, currentAccess(false));

    assertEquals(List.of("SCOPE_profile", "OIDC_USER"), authorityNames(requestAuthentication));
    var requestPrincipal = (OpenCopPrincipal) requestAuthentication.getPrincipal();
    assertFalse(requestPrincipal.isGlobalAdmin());
    assertEquals("oidc", requestAuthentication.getAuthorizedClientRegistrationId());
    assertSame(details, requestAuthentication.getDetails());

    assertTrue(sessionPrincipal.isGlobalAdmin());
    assertEquals(
        List.of("SCOPE_profile", Roles.ADMIN, "OIDC_USER", Roles.ADMIN),
        authorityNames(sessionAuthentication));
  }

  @Test
  void currentAdministrator_preservesNonAdminTokenAuthoritiesAndAddsAdministratorAuthority() {
    var sessionPrincipal = TestPrincipals.oidcPrincipal(USER_ID);
    var sessionAuthentication =
        authentication(
            sessionPrincipal, List.of(authority("OIDC_USER"), authority("SCOPE_profile")));

    var requestAuthentication =
        (OAuth2AuthenticationToken) factory.create(sessionAuthentication, currentAccess(true));

    assertEquals(
        List.of("OIDC_USER", "SCOPE_profile", Roles.ADMIN), authorityNames(requestAuthentication));
    assertTrue(((OpenCopPrincipal) requestAuthentication.getPrincipal()).isGlobalAdmin());
    assertFalse(sessionPrincipal.isGlobalAdmin());
    assertEquals(List.of("OIDC_USER", "SCOPE_profile"), authorityNames(sessionAuthentication));
  }

  private static OAuth2AuthenticationToken authentication(
      OpenCopPrincipal principal, List<GrantedAuthority> authorities) {
    return new OAuth2AuthenticationToken(principal, authorities, "oidc");
  }

  private static CurrentAccountAccess currentAccess(boolean administrator) {
    return new CurrentAccountAccess(USER_ID, true, administrator, 0, 0);
  }

  private static SimpleGrantedAuthority authority(String value) {
    return new SimpleGrantedAuthority(value);
  }

  private static List<String> authorityNames(OAuth2AuthenticationToken authentication) {
    return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
  }
}
