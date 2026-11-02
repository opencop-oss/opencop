// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.user;

import com.opencop.core.api.general.business.model.user.InternalUser;
import java.util.UUID;

/**
 * Minimal, picker-facing projection of a user for {@code GET /v1/users/search}. Carries only what
 * the drawer assign pickers need — selection identity ({@code id}) and display text ({@code
 * username} / {@code displayName}) — deliberately omitting internal fields (IdP identifiers,
 * issuer, email, admin/active state, timestamps) that the picker does not use, so the search
 * endpoint does not put them on the wire.
 */
public record UserSearchResult(UUID id, String username, String displayName) {

  static UserSearchResult from(InternalUser user) {
    return new UserSearchResult(user.id(), user.username(), user.displayName());
  }
}
