// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.audit;

import java.util.Objects;
import java.util.UUID;

/**
 * Sanitized, log-only resource reference. Only the static factories construct instances, so every
 * id is character-allowlisted and codepoint-capped — never raw user input. Construction clamps
 * instead of throwing (see {@link AuthzDenialEvent}).
 */
public final class AuthzResource {

  private static final int MAX_RESOURCE_ID_CODEPOINTS = 128;

  private final AuthzResourceType type;
  private final String id;

  private AuthzResource(AuthzResourceType type, String id) {
    this.type = type == null ? AuthzResourceType.UNKNOWN : type;
    this.id = id == null || id.isBlank() ? "UNKNOWN" : id;
  }

  public static AuthzResource event(UUID sourceId, String objectId) {
    return new AuthzResource(
        AuthzResourceType.EVENT,
        "sourceId="
            + (sourceId == null ? "UNKNOWN" : sourceId)
            + ",objectId="
            + sanitize(objectId, false));
  }

  public static AuthzResource httpRequest(String method, String path) {
    return new AuthzResource(
        AuthzResourceType.UNKNOWN,
        "method=" + sanitize(method, false) + ",path=" + sanitize(path, true));
  }

  public AuthzResourceType type() {
    return type;
  }

  public String id() {
    return id;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AuthzResource resource
        && type == resource.type
        && id.equals(resource.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, id);
  }

  @Override
  public String toString() {
    return "AuthzResource[type=" + type + ", id=" + id + "]";
  }

  private static String sanitize(String value, boolean allowPathSeparators) {
    if (value == null || value.isBlank()) {
      return "UNKNOWN";
    }
    var sanitized =
        value
            .strip()
            .codePoints()
            .mapToObj(codePoint -> sanitizeCodePoint(codePoint, allowPathSeparators))
            .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
            .toString();
    return capCodepoints(sanitized);
  }

  private static String sanitizeCodePoint(int codePoint, boolean allowPathSeparators) {
    if (isAsciiLetterOrDigit(codePoint)
        || codePoint == '.'
        || codePoint == '_'
        || codePoint == '-'
        || (allowPathSeparators && (codePoint == '/' || codePoint == '{' || codePoint == '}'))) {
      return new String(Character.toChars(codePoint));
    }
    return "_";
  }

  private static boolean isAsciiLetterOrDigit(int codePoint) {
    return (codePoint >= 'A' && codePoint <= 'Z')
        || (codePoint >= 'a' && codePoint <= 'z')
        || (codePoint >= '0' && codePoint <= '9');
  }

  private static String capCodepoints(String value) {
    if (value.codePointCount(0, value.length()) <= MAX_RESOURCE_ID_CODEPOINTS) {
      return value;
    }
    // Inclusive cap (truncated form fits the max, matching the email cap
    // convention in AuthzDenialEvent).
    return value
            .codePoints()
            .limit(MAX_RESOURCE_ID_CODEPOINTS - 3)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString()
        + "...";
  }
}
