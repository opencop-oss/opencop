// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.auth;

import java.util.List;

public record IdpRelationship(String group, List<String> roles) {

  public IdpRelationship {
    roles = List.copyOf(roles);
  }
}
