// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.auth;

import com.opencop.core.api.general.business.model.user.UserPreference;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MeResponse(
    boolean authenticated,
    User user,
    Idp idp,
    List<String> authorities,
    Map<String, Object> claims,
    UserPreference preference) {
  public record User(UUID id, String subject, String username, String displayName, String email) {}

  public record Idp(String issuer) {}
}
