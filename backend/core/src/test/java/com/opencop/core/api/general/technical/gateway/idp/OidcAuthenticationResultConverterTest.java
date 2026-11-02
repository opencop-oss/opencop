// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.idp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.auth.AdmittedOidcIdentity;
import com.opencop.core.api.general.business.model.auth.OidcProfile;
import com.opencop.core.sdk.business.model.Roles;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

class OidcAuthenticationResultConverterTest {

  private static final UUID USER_ID = UUID.fromString("8d065639-90b3-4991-adac-8dc497f13b35");
  private static final Instant AUTHENTICATED_AT = Instant.parse("2026-07-23T15:00:00Z");

  private final OidcAuthenticationResultConverter converter =
      new OidcAuthenticationResultConverter(new MinimalPrincipalFactory());

  @Test
  void convertsOnlyAnAdmittedCallbackResultToMinimalSessionAuthentication() {
    var callback = mock(OAuth2LoginAuthenticationToken.class);
    var registration = mock(ClientRegistration.class);
    when(registration.getRegistrationId()).thenReturn("keycloak");
    when(callback.getClientRegistration()).thenReturn(registration);
    when(callback.getPrincipal())
        .thenReturn(new AdmittedOidcUser(mock(OidcUser.class), admittedIdentity()));
    when(callback.getAuthorities())
        .thenReturn(
            List.of(
                new SimpleGrantedAuthority("OIDC_USER"),
                new SimpleGrantedAuthority("SCOPE_openid"),
                new SimpleGrantedAuthority(Roles.ADMIN),
                new SimpleGrantedAuthority("SCOPE_openid")));

    var result = converter.convert(callback);

    assertThat(result.getAuthorizedClientRegistrationId()).isEqualTo("keycloak");
    assertThat(result.getAuthorities())
        .extracting(authority -> authority.getAuthority())
        .containsExactly("OIDC_USER", "SCOPE_openid");
    assertThat(result.getAuthorities()).allMatch(SimpleGrantedAuthority.class::isInstance);
    assertThat(result.getPrincipal()).isInstanceOf(OpenCopPrincipal.class);
    var principal = (OpenCopPrincipal) result.getPrincipal();
    assertThat(principal.internalUserId()).isEqualTo(USER_ID);
    assertThat(principal.issuer()).isEqualTo("https://issuer.test");
    assertThat(principal.subject()).isEqualTo("verified-subject");
    assertThat(principal.idpUserId()).isEqualTo("verified-subject");
    assertThat(principal.preferredUsername()).isEqualTo("preferred");
    assertThat(principal.fullName()).isEqualTo("Profile Name");
    assertThat(principal.email()).isEqualTo("profile@example.test");
    assertThat(principal.sessionGeneration()).isEqualTo(17);
    assertThat(principal.authenticatedAt()).isEqualTo(AUTHENTICATED_AT);
    assertThat(principal.getAuthorities()).isEmpty();
    assertThat(principal.getAttributes())
        .doesNotContainKeys("access_token", "refresh_token", "id_token", "groups", "roles");
  }

  @Test
  void rejectsAResultThatDidNotPassOpenCopAdmission() {
    var callback = mock(OAuth2LoginAuthenticationToken.class);
    when(callback.getPrincipal()).thenReturn(mock(OAuth2User.class));

    var error =
        assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(callback));

    assertThat(error.getError().getErrorCode()).isEqualTo("opencop_admission_required");
  }

  private static AdmittedOidcIdentity admittedIdentity() {
    return new AdmittedOidcIdentity(
        USER_ID,
        "https://issuer.test",
        "verified-subject",
        "verified-subject",
        new OidcProfile(
            "preferred", "Profile Name", "Profile", "Name", "profile@example.test", true),
        17,
        AUTHENTICATED_AT);
  }
}
