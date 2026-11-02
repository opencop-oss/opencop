// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.business.model.Roles;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

class OpenCopPrincipalTest {

  @Test
  void minimalPrincipal_omitsAbsentOptionalProfileFields() {
    var principal =
        new OpenCopPrincipal(
            UUID.randomUUID(),
            "https://idp.example.test",
            "stable-id",
            "subject",
            new OpenCopPrincipal.Profile(null, null, null, null, null, null),
            0,
            Instant.EPOCH);

    assertEquals(
        Map.of("iss", "https://idp.example.test", "idp_user_id", "stable-id", "sub", "subject"),
        principal.getAttributes());
  }

  @Test
  void minimalPrincipal_roundTripsOnlyExplicitIdentityAndAllowlistedAttributes() throws Exception {
    var internalUserId = UUID.fromString("6a0596bf-f97b-447b-b9f0-c82fc77657b7");
    var authenticatedAt = Instant.parse("2026-07-23T13:15:00Z");
    var principal =
        new OpenCopPrincipal(
            internalUserId,
            "https://idp.example.test/realms/opencop",
            "stable-id",
            "verified-subject",
            new OpenCopPrincipal.Profile(
                "preferred-user",
                "Preferred User",
                "Preferred",
                "User",
                "preferred.user@example.test",
                true),
            11,
            authenticatedAt);

    var serialized = serialize(principal);
    var roundTripped = deserialize(serialized);

    assertInstanceOf(OAuth2User.class, roundTripped);
    assertFalse(OidcUser.class.isInstance(roundTripped));
    assertEquals(internalUserId, roundTripped.internalUserId());
    assertEquals("https://idp.example.test/realms/opencop", roundTripped.issuer());
    assertEquals("stable-id", roundTripped.idpUserId());
    assertEquals("verified-subject", roundTripped.getName());
    assertEquals(11, roundTripped.sessionGeneration());
    assertEquals(authenticatedAt, roundTripped.authenticatedAt());
    assertEquals(
        Map.ofEntries(
            Map.entry("iss", "https://idp.example.test/realms/opencop"),
            Map.entry("idp_user_id", "stable-id"),
            Map.entry("sub", "verified-subject"),
            Map.entry("preferred_username", "preferred-user"),
            Map.entry("name", "Preferred User"),
            Map.entry("given_name", "Preferred"),
            Map.entry("family_name", "User"),
            Map.entry("email", "preferred.user@example.test"),
            Map.entry("email_verified", true)),
        roundTripped.getAttributes());
    assertTrue(roundTripped.getAuthorities().isEmpty());
    var serializedText = new String(serialized, StandardCharsets.ISO_8859_1);
    assertFalse(serializedText.contains("LEGACY_ID_TOKEN"));
    assertFalse(serializedText.contains("arbitrary_legacy_claim"));
    assertFalse(serializedText.contains(Roles.ADMIN));
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove after no deployed release can write the legacy
   * representation and eight hours have elapsed since the last legacy writer left service. A
   * rollback to a legacy writer resets that gate.
   */
  @Test
  void baselineFixture_deserializesWithItsOriginalIdentityAuthoritiesAndGeneration()
      throws Exception {
    OpenCopPrincipal principal;
    try (var input =
        new ObjectInputStream(
            getClass()
                .getResourceAsStream(
                    "/com/opencop/core/sdk/technical/security/"
                        + "legacy-open-cop-principal-v1.ser"))) {
      principal = (OpenCopPrincipal) input.readObject();
    }

    assertEquals(
        UUID.fromString("a57eb176-3930-459a-a0d3-4bfdef113740"), principal.internalUserId());
    assertEquals("legacy-subject", principal.getName());
    assertEquals("https://legacy-idp.example.test/realms/opencop", principal.issuer());
    assertEquals("legacy-stable-oid", principal.idpUserId());
    assertEquals(7, principal.sessionGeneration());
    assertEquals(
        List.of(Roles.ADMIN, "SCOPE_openid", "SCOPE_profile"),
        principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList());
  }

  @Test
  void getAuthorities_delegatesToOidcUserByDefault() {
    // AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove with the retained legacy constructors only
    // after no deployed release can write the legacy representation and eight hours have elapsed
    // since the last legacy writer left service. A rollback to a legacy writer resets that gate.
    var oidcUser = mock(OidcUser.class);
    doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("SCOPE_openid")))
        .when(oidcUser)
        .getAuthorities();

    var principal = new OpenCopPrincipal(UUID.randomUUID(), oidcUser);

    assertTrue(
        principal.getAuthorities().stream().anyMatch(a -> "SCOPE_openid".equals(a.getAuthority())));
    assertFalse(
        principal.getAuthorities().stream().anyMatch(a -> Roles.ADMIN.equals(a.getAuthority())));
  }

  /**
   * CustomAuthenticationSuccessHandler passes admin=true from the DB to the constructor. Endpoints
   * that use @AuthenticationPrincipal then call principal.getAuthorities() for manual role checks.
   * The principal must reflect the admin authority, not just the raw OidcUser delegate's.
   */
  @Test
  void constructor_withAdmin_addsRoleAdmin() {
    // AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove with the retained legacy constructors only
    // after no deployed release can write the legacy representation and eight hours have elapsed
    // since the last legacy writer left service. A rollback to a legacy writer resets that gate.
    var oidcUser = mock(OidcUser.class);
    doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("SCOPE_openid")))
        .when(oidcUser)
        .getAuthorities();

    var principal = new OpenCopPrincipal(UUID.randomUUID(), oidcUser, true);

    assertTrue(
        principal.getAuthorities().stream().anyMatch(a -> Roles.ADMIN.equals(a.getAuthority())),
        "Principal should have ROLE_admin when constructed with admin=true");
    assertTrue(
        principal.getAuthorities().stream().anyMatch(a -> "SCOPE_openid".equals(a.getAuthority())),
        "Principal should still have delegate's original authorities");
  }

  @Test
  void isGlobalAdmin_reflectsResolvedAuthorities() {
    // AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: delete only the legacy constructor assertions after no
    // deployed release can write the legacy representation and eight hours have elapsed since the
    // last legacy writer left service. A rollback to a legacy writer resets that gate; retain the
    // minimal assertions.
    var nonAdminUser = mock(OidcUser.class);
    doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("SCOPE_openid")))
        .when(nonAdminUser)
        .getAuthorities();

    var adminUser = mock(OidcUser.class);
    doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority(Roles.ADMIN)))
        .when(adminUser)
        .getAuthorities();

    assertFalse(new OpenCopPrincipal(UUID.randomUUID(), nonAdminUser).isGlobalAdmin());
    assertTrue(new OpenCopPrincipal(UUID.randomUUID(), adminUser).isGlobalAdmin());
    assertTrue(new OpenCopPrincipal(UUID.randomUUID(), nonAdminUser, true).isGlobalAdmin());
  }

  @Test
  void constructorsWithoutExplicitGeneration_useBaselineGenerationZero() {
    // AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove with the retained legacy constructors only
    // after no deployed release can write the legacy representation and eight hours have elapsed
    // since the last legacy writer left service. A rollback to a legacy writer resets that gate.
    var oidcUser = mock(OidcUser.class);
    doReturn(List.<GrantedAuthority>of()).when(oidcUser).getAuthorities();

    assertEquals(0, new OpenCopPrincipal(UUID.randomUUID(), oidcUser).sessionGeneration());
    assertEquals(0, new OpenCopPrincipal(UUID.randomUUID(), oidcUser, true).sessionGeneration());
  }

  @Test
  void requestCurrentAdministrator_overridesDelegateAndLoginSnapshot() {
    // AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: replace the legacy fixture with a minimal principal
    // only after no deployed release can write the legacy representation and eight hours have
    // elapsed since the last legacy writer left service. A rollback to a legacy writer resets that
    // gate.
    var oidcUser = mock(OidcUser.class);
    doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority(Roles.ADMIN)))
        .when(oidcUser)
        .getAuthorities();
    var loginPrincipal = new OpenCopPrincipal(UUID.randomUUID(), oidcUser, true);

    assertFalse(loginPrincipal.withRequestCurrentGlobalAdministrator(false).isGlobalAdmin());
    assertTrue(loginPrincipal.withRequestCurrentGlobalAdministrator(true).isGlobalAdmin());
  }

  private static byte[] serialize(OpenCopPrincipal principal) throws Exception {
    var output = new ByteArrayOutputStream();
    try (var objectOutput = new ObjectOutputStream(output)) {
      objectOutput.writeObject(principal);
    }
    return output.toByteArray();
  }

  private static OpenCopPrincipal deserialize(byte[] serialized) throws Exception {
    try (var input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      return (OpenCopPrincipal) input.readObject();
    }
  }
}
