// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.idp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class OidcAdmissionCommandFactoryTest {

  @Test
  void createsTypedCommandFromVerifiedTokenAndMergedProfile() {
    var idToken =
        idToken(
            Map.of(
                "iss", "https://verified-issuer.test",
                "sub", "verified-subject",
                "aud", List.of("CORE-API")));
    var userInfo =
        new OidcUserInfo(
            Map.ofEntries(
                Map.entry("iss", "https://untrusted-user-info.test"),
                Map.entry("sub", "overridden-subject"),
                Map.entry("groups", List.of("group-a", "group-a", "group-b")),
                Map.entry("roles", List.of("operator", "operator")),
                Map.entry("preferred_username", "preferred"),
                Map.entry("name", "Profile Name"),
                Map.entry("given_name", "Profile"),
                Map.entry("family_name", "Name"),
                Map.entry("email", "profile@example.test"),
                Map.entry("email_verified", true),
                Map.entry("arbitrary", "must-not-survive")));
    var loadedUser = new DefaultOidcUser(List.of(), idToken, userInfo);

    var command =
        new OidcAdmissionCommandFactory("sub", "CORE-API").create(userRequest(idToken), loadedUser);

    assertThat(command.issuer()).isEqualTo("https://verified-issuer.test");
    assertThat(command.subject()).isEqualTo("verified-subject");
    assertThat(command.idpUserId()).isEqualTo("verified-subject");
    assertThat(command.audiences()).containsExactly("CORE-API");
    assertThat(command.groups()).containsExactly("group-a", "group-b");
    assertThat(command.roles()).containsExactly("operator");
    assertThat(command.profile().preferredUsername()).isEqualTo("preferred");
    assertThat(command.profile().fullName()).isEqualTo("Profile Name");
    assertThat(command.profile().givenName()).isEqualTo("Profile");
    assertThat(command.profile().familyName()).isEqualTo("Name");
    assertThat(command.profile().email()).isEqualTo("profile@example.test");
    assertThat(command.profile().emailVerified()).isTrue();
  }

  @Test
  void malformedOptionalProfileValuesAreOmitted() {
    var idToken = idToken(Map.of("iss", "https://issuer.test", "sub", "subject", "aud", List.of()));
    var userInfo =
        new OidcUserInfo(
            Map.of(
                "preferred_username",
                42,
                "name",
                false,
                "email",
                List.of("not", "a", "string"),
                "email_verified",
                "true"));

    var command =
        new OidcAdmissionCommandFactory("sub", "")
            .create(userRequest(idToken), new DefaultOidcUser(List.of(), idToken, userInfo));

    assertThat(command.profile().preferredUsername()).isNull();
    assertThat(command.profile().fullName()).isNull();
    assertThat(command.profile().email()).isNull();
    assertThat(command.profile().emailVerified()).isNull();
  }

  @Test
  void malformedRelationshipClaimFailsClosed() {
    var idToken = idToken(Map.of("iss", "https://issuer.test", "sub", "subject"));
    var userInfo = new OidcUserInfo(Map.of("groups", "group-a"));

    assertThrows(
        OAuth2AuthenticationException.class,
        () ->
            new OidcAdmissionCommandFactory("sub", "")
                .create(userRequest(idToken), new DefaultOidcUser(List.of(), idToken, userInfo)));
  }

  @Test
  void configuredAudienceMustBePresentInTheVerifiedIdToken() {
    var idToken =
        idToken(
            Map.of(
                "iss",
                "https://verified-issuer.test",
                "sub",
                "verified-subject",
                "aud",
                List.of("test-client")));
    var loadedUser = new DefaultOidcUser(List.of(), idToken);

    var error =
        assertThrows(
            OAuth2AuthenticationException.class,
            () ->
                new OidcAdmissionCommandFactory("sub", "CORE-API")
                    .create(userRequest(idToken), loadedUser));

    assertThat(error.getError().getErrorCode()).isEqualTo("invalid_oidc_audience");
  }

  @Test
  void blankAudienceConfigurationKeepsTheVerifiedAudienceRuleOptional() {
    var idToken =
        idToken(
            Map.of(
                "iss",
                "https://verified-issuer.test",
                "sub",
                "verified-subject",
                "aud",
                List.of("test-client")));
    var loadedUser = new DefaultOidcUser(List.of(), idToken);

    var command =
        new OidcAdmissionCommandFactory("sub", "").create(userRequest(idToken), loadedUser);

    assertThat(command.audiences()).containsExactly("test-client");
  }

  private static OidcUserRequest userRequest(OidcIdToken idToken) {
    var accessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "access-token",
            Instant.parse("2026-07-23T14:00:00Z"),
            Instant.parse("2026-07-23T14:05:00Z"),
            Set.of("openid"));
    return new OidcUserRequest(clientRegistration(), accessToken, idToken);
  }

  private static ClientRegistration clientRegistration() {
    return ClientRegistration.withRegistrationId("oidc")
        .clientId("client")
        .clientSecret("secret")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .authorizationUri("https://issuer.test/authorize")
        .tokenUri("https://issuer.test/token")
        .jwkSetUri("https://issuer.test/jwks")
        .userInfoUri("https://issuer.test/userinfo")
        .userNameAttributeName("sub")
        .scope("openid")
        .clientName("OIDC")
        .build();
  }

  private static OidcIdToken idToken(Map<String, Object> claims) {
    return new OidcIdToken(
        "id-token",
        Instant.parse("2026-07-23T14:00:00Z"),
        Instant.parse("2026-07-23T14:05:00Z"),
        claims);
  }
}
