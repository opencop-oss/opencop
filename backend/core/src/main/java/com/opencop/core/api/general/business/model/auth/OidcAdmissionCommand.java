// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.auth;

import java.util.List;

public record OidcAdmissionCommand(
    String issuer,
    String subject,
    String idpUserId,
    List<String> audiences,
    List<String> groups,
    List<String> roles,
    OidcProfile profile) {

  public OidcAdmissionCommand {
    audiences = List.copyOf(audiences);
    groups = List.copyOf(groups);
    roles = List.copyOf(roles);
  }
}
