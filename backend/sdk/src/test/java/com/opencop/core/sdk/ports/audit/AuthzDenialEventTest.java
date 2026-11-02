// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthzDenialEventTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID SOURCE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

  @Test
  void generic_permitsNullActorAndEmptyPermissions() {
    var event =
        AuthzDenialEvent.generic(
            AuthzPrincipalType.UNKNOWN,
            null,
            null,
            AuthzResource.httpRequest("GET", "/v1/events/{sourceId}/attachments"),
            null,
            AuthzReasonCode.UNATTRIBUTED);

    assertEquals(AuthzPrincipalType.UNKNOWN, event.principalType());
    assertEquals("UNKNOWN", event.action());
    assertEquals("UNKNOWN", event.actorEmail());
    assertTrue(event.effectivePermissions().isEmpty());
    assertTrue(event.requiredPermissions().isEmpty());
  }

  @Test
  void construction_clampsInsteadOfThrowing_onDenyPathMisuse() {
    // Deny-path safety: a malformed rich event must degrade to honest
    // sentinels, never throw (a throw would turn a 403 into a 500 and skip
    // the final-status backstop entirely).
    var resource = AuthzResource.event(SOURCE_ID, "event-1");

    var nullActor =
        AuthzDenialEvent.rich(
            null,
            "user@example.com",
            resource,
            "GET /v1/events/{sourceId}/{objectId}/attachments",
            AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
            List.of(AuthzPermission.NONE),
            List.of(AuthzPermission.SERVER_VIEWER));
    assertNull(nullActor.actorUserId());
    assertEquals(AuthzPrincipalType.AUTHENTICATED, nullActor.principalType());

    // Empty means "not captured" (renders UNKNOWN) — never upgraded to the
    // stronger NONE claim ("verified to hold no permissions").
    var emptyEffective =
        AuthzDenialEvent.rich(
            USER_ID,
            "user@example.com",
            resource,
            "GET /v1/events/{sourceId}/{objectId}/attachments",
            AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
            List.of(),
            List.of(AuthzPermission.SERVER_VIEWER));
    assertTrue(emptyEffective.effectivePermissions().isEmpty());

    var emptyRequired =
        AuthzDenialEvent.rich(
            USER_ID,
            "user@example.com",
            resource,
            "GET /v1/events/{sourceId}/{objectId}/attachments",
            AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
            List.of(AuthzPermission.NONE),
            List.of());
    assertTrue(emptyRequired.requiredPermissions().isEmpty());

    var nullEverything = new AuthzDenialEvent(null, null, null, null, null, null, null, null);
    assertEquals(AuthzPrincipalType.UNKNOWN, nullEverything.principalType());
    assertEquals("UNKNOWN", nullEverything.action());
    assertEquals(AuthzReasonCode.UNATTRIBUTED, nullEverything.reasonCode());
    assertEquals("method=UNKNOWN,path=UNKNOWN", nullEverything.resource().id());
    assertEquals("UNKNOWN", nullEverything.actorEmail());
    assertTrue(nullEverything.effectivePermissions().isEmpty());
    assertTrue(nullEverything.requiredPermissions().isEmpty());
  }

  @Test
  void permissionLists_clampNullElementsAndInvalidNoneUsage() {
    var resource = AuthzResource.event(SOURCE_ID, "event-1");

    // NONE mixed with real permissions is contradictory; the real ones win.
    var mixedNone =
        new AuthzDenialEvent(
            AuthzPrincipalType.AUTHENTICATED,
            USER_ID,
            "user@example.com",
            resource,
            "GET /v1/events/{sourceId}/{objectId}/attachments",
            AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
            List.of(AuthzPermission.NONE, AuthzPermission.SERVER_VIEWER),
            List.of(AuthzPermission.SERVER_VIEWER));
    assertEquals(List.of(AuthzPermission.SERVER_VIEWER), mixedNone.effectivePermissions());

    // NONE is never a requirement.
    var noneRequired =
        new AuthzDenialEvent(
            AuthzPrincipalType.AUTHENTICATED,
            USER_ID,
            "user@example.com",
            resource,
            "GET /v1/events/{sourceId}/{objectId}/attachments",
            AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
            List.of(AuthzPermission.SERVER_VIEWER),
            List.of(AuthzPermission.NONE, AuthzPermission.SERVER_VIEWER));
    assertEquals(List.of(AuthzPermission.SERVER_VIEWER), noneRequired.requiredPermissions());

    var nullElement =
        new AuthzDenialEvent(
            AuthzPrincipalType.AUTHENTICATED,
            USER_ID,
            "user@example.com",
            resource,
            "GET /v1/events/{sourceId}/{objectId}/attachments",
            AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
            listWithNullPermission(),
            List.of(AuthzPermission.SERVER_VIEWER));
    assertTrue(nullElement.effectivePermissions().isEmpty());

    // The sole-NONE rich sentinel stays intact.
    var soleNone =
        new AuthzDenialEvent(
            AuthzPrincipalType.AUTHENTICATED,
            USER_ID,
            "user@example.com",
            resource,
            "GET /v1/events/{sourceId}/{objectId}/attachments",
            AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
            List.of(AuthzPermission.NONE),
            List.of(AuthzPermission.SERVER_VIEWER));
    assertEquals(List.of(AuthzPermission.NONE), soleNone.effectivePermissions());

    // Duplicate NONEs collapse to the sole-sentinel form, not "not captured".
    var duplicateNone =
        new AuthzDenialEvent(
            AuthzPrincipalType.AUTHENTICATED,
            USER_ID,
            "user@example.com",
            resource,
            "GET /v1/events/{sourceId}/{objectId}/attachments",
            AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
            List.of(AuthzPermission.NONE, AuthzPermission.NONE),
            List.of(AuthzPermission.SERVER_VIEWER));
    assertEquals(List.of(AuthzPermission.NONE), duplicateNone.effectivePermissions());
  }

  @Test
  void actorEmail_sanitizesWhitespaceAndUnsafeCharacters() {
    var event =
        AuthzDenialEvent.rich(
            USER_ID,
            " user\n+<tag>@example.com ",
            AuthzResource.event(SOURCE_ID, "event-1"),
            "GET /v1/events/{sourceId}/{objectId}/attachments",
            AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
            List.of(AuthzPermission.NONE),
            List.of(AuthzPermission.SERVER_VIEWER));

    assertEquals("user+_tag_@example.com", event.actorEmail());
    assertFalse(event.actorEmail().contains("\n"));
    assertFalse(event.actorEmail().contains("<"));
  }

  @Test
  void actorEmail_capsCodepointsAtBoundary() {
    // 254 codepoints exactly: unchanged.
    var atLimit = "a".repeat(242) + "@example.com"; // 242 + 12 = 254
    assertEquals(254, atLimit.codePointCount(0, atLimit.length()));
    assertEquals(atLimit, richEventWithEmail(atLimit).actorEmail());

    // One over: truncated to 251 codepoints + "..." = 254.
    var overLimit = "a".repeat(243) + "@example.com"; // 255 codepoints
    var capped = richEventWithEmail(overLimit).actorEmail();
    assertEquals(254, capped.codePointCount(0, capped.length()));
    assertTrue(capped.endsWith("..."));

    // Supplementary codepoints must count as one, not two chars; they are
    // outside the allowlist so each sanitizes to a single underscore.
    var supplementary = "😀".repeat(300) + "@example.com";
    var sanitized = richEventWithEmail(supplementary).actorEmail();
    assertEquals(254, sanitized.codePointCount(0, sanitized.length()));
    assertTrue(sanitized.startsWith("_"));
  }

  @Test
  void resources_sanitizeLogOnlyIdentifiers() {
    var attachment = AuthzResource.event(SOURCE_ID, "object/with space:and?query");
    var request = AuthzResource.httpRequest("GET\n", "/v1/events/{sourceId}/bad path?q=1");

    assertEquals(AuthzResourceType.EVENT, attachment.type());
    assertEquals(
        "sourceId=" + SOURCE_ID + ",objectId=object_with_space_and_query", attachment.id());
    assertEquals(AuthzResourceType.UNKNOWN, request.type());
    assertEquals("method=GET,path=/v1/events/{sourceId}/bad_path_q_1", request.id());
  }

  @Test
  void resources_clampBlankInputsAndCapLength() {
    assertEquals("sourceId=UNKNOWN,objectId=UNKNOWN", AuthzResource.event(null, " ").id());
    assertEquals("method=UNKNOWN,path=UNKNOWN", AuthzResource.httpRequest(null, null).id());

    // 128 codepoints exactly: unchanged; one over: capped with "..." suffix.
    var atLimit = "x".repeat(128);
    assertEquals("method=GET,path=" + atLimit, AuthzResource.httpRequest("GET", atLimit).id());
    var overLimit = "x".repeat(129);
    assertEquals(
        "method=GET,path=" + "x".repeat(125) + "...",
        AuthzResource.httpRequest("GET", overLimit).id());
  }

  @Test
  void resources_haveValueSemantics() {
    var first = AuthzResource.event(SOURCE_ID, "event-1");
    var second = AuthzResource.event(SOURCE_ID, "event-1");

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertEquals(
        AuthzDenialEvent.generic(
            AuthzPrincipalType.UNKNOWN, null, null, first, null, AuthzReasonCode.UNATTRIBUTED),
        AuthzDenialEvent.generic(
            AuthzPrincipalType.UNKNOWN, null, null, second, null, AuthzReasonCode.UNATTRIBUTED));
  }

  @Test
  void action_sanitizesAndCapsRequestLine() {
    var event =
        AuthzDenialEvent.generic(
            AuthzPrincipalType.UNKNOWN,
            null,
            null,
            AuthzResource.httpRequest("GET", "/v1/x"),
            "GET /v1/evil\r\ninjected",
            AuthzReasonCode.UNATTRIBUTED);
    assertEquals("GET /v1/evil__injected", event.action());

    var overLimit =
        AuthzDenialEvent.generic(
            AuthzPrincipalType.UNKNOWN,
            null,
            null,
            AuthzResource.httpRequest("GET", "/v1/x"),
            "GET /" + "x".repeat(200),
            AuthzReasonCode.UNATTRIBUTED);
    assertEquals(128, overLimit.action().codePointCount(0, overLimit.action().length()));
    assertTrue(overLimit.action().endsWith("..."));
  }

  private static AuthzDenialEvent richEventWithEmail(String email) {
    return AuthzDenialEvent.rich(
        USER_ID,
        email,
        AuthzResource.event(SOURCE_ID, "event-1"),
        "GET /v1/events/{sourceId}/{objectId}/attachments",
        AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
        List.of(AuthzPermission.NONE),
        List.of(AuthzPermission.SERVER_VIEWER));
  }

  private static List<AuthzPermission> listWithNullPermission() {
    var list = new java.util.ArrayList<AuthzPermission>();
    list.add(null);
    return list;
  }
}
