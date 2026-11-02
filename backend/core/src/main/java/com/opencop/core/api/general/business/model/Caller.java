// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model;

import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;

/**
 * Who is asking, as their identity reads at the moment they ask.
 *
 * <p>One type for both jobs a caller's identity does: deciding what they may do, and recording what
 * they did. Those were separate carriers once — a bare id and flag for authorization, a copy of the
 * same facts for the ledger — and nothing forced the two to agree. The watchbox feature settled on
 * the same shape in {@code WatchboxCaller}.
 *
 * <p>Name and email are copied independently and either may be absent; no display label is derived
 * here, so a reader chooses {@code fullName -> email -> userId} without the stored record changing.
 * Both are sanitized and bounded on construction, because they are copied into a ledger column and
 * a control character or an overlong value must not reach it.
 *
 * @param globalAdmin whether the caller held the global admin role at that moment
 */
public record Caller(UUID userId, String fullName, String email, boolean globalAdmin) {

  private static final int FULL_NAME_MAXIMUM = 255;
  private static final int EMAIL_MAXIMUM = 320;

  public Caller {
    if (userId == null) {
      throw new IllegalArgumentException("userId is required");
    }
    fullName = sanitize(fullName, FULL_NAME_MAXIMUM);
    email = sanitize(email, EMAIL_MAXIMUM);
  }

  /** Copies the authenticated principal's identity as it reads now. */
  public static Caller of(OpenCopPrincipal principal) {
    return new Caller(
        principal.internalUserId(),
        principal.fullName(),
        principal.email(),
        principal.isGlobalAdmin());
  }

  /** Strips control characters, trims, and bounds by code point so a column cannot overflow. */
  private static String sanitize(String value, int maximum) {
    if (value == null) {
      return null;
    }
    var cleaned = value.replaceAll("\\p{Cntrl}", "").trim();
    return cleaned.isEmpty() ? null : BoundedText.truncate(cleaned, maximum);
  }
}
