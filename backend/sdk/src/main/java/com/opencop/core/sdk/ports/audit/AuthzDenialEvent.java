// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.audit;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One authorization denial. Construction clamps instead of throwing: events are built inside deny
 * paths, before the never-throwing reporter is reached, so a malformed argument must degrade to an
 * honest UNKNOWN/UNATTRIBUTED event rather than convert a 403 into a 500 (which would also skip the
 * final-status backstop and lose the audit entirely).
 *
 * <p>Vocabulary conventions:
 *
 * <ul>
 *   <li>{@link AuthzPermission#NONE} means "verified to hold no permissions" and may only appear as
 *       the sole effective permission of a rich event; it is never valid in required permissions.
 *   <li>Empty permission lists mean "not captured"; log rendering shows them as UNKNOWN. Garbage
 *       input degrades toward "not captured", never toward a stronger claim.
 *   <li>A null or blank actor email becomes the UNKNOWN sentinel; emails are sanitized and
 *       length-capped at construction and are log-only — identity never becomes a metric tag.
 *   <li>Action is the request line (method + route template), derived at emission rather than named
 *       by callers; absent (no handler matched, e.g. CSRF rejections) it is UNKNOWN.
 * </ul>
 *
 * <p>Clamps are silent by design: they guard deny paths against caller bugs, where surfacing the
 * bug must never cost the 403 or the audit event. Malformed inputs show up as UNKNOWN sentinels in
 * the AUTHZ_DENIED stream rather than as log noise at construction time.
 */
public record AuthzDenialEvent(
    AuthzPrincipalType principalType,
    UUID actorUserId,
    String actorEmail,
    AuthzResource resource,
    String action,
    AuthzReasonCode reasonCode,
    List<AuthzPermission> effectivePermissions,
    List<AuthzPermission> requiredPermissions) {

  public AuthzDenialEvent {
    principalType = principalType == null ? AuthzPrincipalType.UNKNOWN : principalType;
    actorEmail = sanitizeActorEmail(actorEmail);
    resource = resource == null ? AuthzResource.httpRequest("UNKNOWN", "UNKNOWN") : resource;
    action = sanitizeAction(action);
    reasonCode = reasonCode == null ? AuthzReasonCode.UNATTRIBUTED : reasonCode;
    effectivePermissions = clampEffectivePermissions(effectivePermissions);
    requiredPermissions = clampRequiredPermissions(requiredPermissions);
  }

  /**
   * Rich event from a business decision point. The action is the request line (method + route
   * template), derived by the emitter — callers supply only what HTTP cannot: reason codes,
   * permissions, actor. Pass {@code List.of(NONE)} only when the decision point verified the actor
   * holds no permissions; a missing/empty list means "not captured" and renders as UNKNOWN — never
   * upgraded to the stronger NONE claim.
   */
  public static AuthzDenialEvent rich(
      UUID actorUserId,
      String actorEmail,
      AuthzResource resource,
      String action,
      AuthzReasonCode reasonCode,
      List<AuthzPermission> effectivePermissions,
      List<AuthzPermission> requiredPermissions) {
    return new AuthzDenialEvent(
        AuthzPrincipalType.AUTHENTICATED,
        actorUserId,
        actorEmail,
        resource,
        action,
        reasonCode,
        effectivePermissions,
        requiredPermissions);
  }

  /**
   * Generic backstop event: best-effort principal and actor attribution (stashed by in-chain
   * classifiers when available, UNKNOWN otherwise), no permission detail.
   */
  public static AuthzDenialEvent generic(
      AuthzPrincipalType principalType,
      UUID actorUserId,
      String actorEmail,
      AuthzResource resource,
      String action,
      AuthzReasonCode reasonCode) {
    return new AuthzDenialEvent(
        principalType, actorUserId, actorEmail, resource, action, reasonCode, List.of(), List.of());
  }

  private static List<AuthzPermission> clampEffectivePermissions(
      List<AuthzPermission> permissions) {
    var cleaned = withoutNulls(permissions);
    if (cleaned.contains(AuthzPermission.NONE)) {
      var real = cleaned.stream().filter(p -> p != AuthzPermission.NONE).toList();
      // NONE asserts "verified to hold no permissions"; mixed with real
      // permissions the real ones win, and duplicate NONEs collapse to the
      // sole-sentinel form rather than degrading to "not captured".
      return real.isEmpty() ? List.of(AuthzPermission.NONE) : List.copyOf(real);
    }
    return cleaned;
  }

  private static List<AuthzPermission> clampRequiredPermissions(List<AuthzPermission> permissions) {
    // NONE is never a requirement; drop it rather than fabricate one.
    return List.copyOf(
        withoutNulls(permissions).stream().filter(p -> p != AuthzPermission.NONE).toList());
  }

  private static List<AuthzPermission> withoutNulls(List<AuthzPermission> permissions) {
    if (permissions == null) {
      return List.of();
    }
    return List.copyOf(permissions.stream().filter(Objects::nonNull).toList());
  }

  // Action is the request line: "METHOD /route/{template}". Bounded by the
  // deployed route table (server-controlled, the same source Spring's own
  // http.server.requests metric tags by), sanitized and capped here so a
  // malformed value can never make the tag unbounded or injectable.
  private static String sanitizeAction(String value) {
    if (value == null || value.isBlank()) {
      return "UNKNOWN";
    }
    var sanitized =
        value
            .strip()
            .codePoints()
            .mapToObj(AuthzDenialEvent::sanitizeActionCodePoint)
            .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
            .toString();
    return capCodepoints(sanitized, 125, 128);
  }

  private static String sanitizeActionCodePoint(int codePoint) {
    if (isAsciiLetterOrDigit(codePoint)
        || codePoint == ' '
        || codePoint == '/'
        || codePoint == '{'
        || codePoint == '}'
        || codePoint == '.'
        || codePoint == '_'
        || codePoint == '-') {
      return new String(Character.toChars(codePoint));
    }
    return "_";
  }

  /**
   * Canonical email sanitizer, shared with emitters and classifiers that surface identity outside
   * event construction (e.g. stashed request attributes). Null/blank becomes the UNKNOWN sentinel.
   */
  public static String sanitizeActorEmail(String value) {
    if (value == null || value.isBlank()) {
      return "UNKNOWN";
    }
    var sanitized =
        value
            .strip()
            .codePoints()
            .filter(codePoint -> !Character.isWhitespace(codePoint))
            .mapToObj(AuthzDenialEvent::sanitizeEmailCodePoint)
            .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
            .toString();
    return capCodepoints(sanitized, 251, 254);
  }

  private static String sanitizeEmailCodePoint(int codePoint) {
    if (isAsciiLetterOrDigit(codePoint)
        || codePoint == '@'
        || codePoint == '.'
        || codePoint == '_'
        || codePoint == '-'
        || codePoint == '+') {
      return new String(Character.toChars(codePoint));
    }
    return "_";
  }

  private static boolean isAsciiLetterOrDigit(int codePoint) {
    return (codePoint >= 'A' && codePoint <= 'Z')
        || (codePoint >= 'a' && codePoint <= 'z')
        || (codePoint >= '0' && codePoint <= '9');
  }

  private static String capCodepoints(String value, int retainedCodepoints, int maxCodepoints) {
    if (value.codePointCount(0, value.length()) <= maxCodepoints) {
      return value;
    }
    return value
            .codePoints()
            .limit(retainedCodepoints)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString()
        + "...";
  }
}
