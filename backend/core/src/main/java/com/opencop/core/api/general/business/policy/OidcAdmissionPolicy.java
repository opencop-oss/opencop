// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import com.opencop.core.api.general.business.model.auth.IdpRelationship;
import com.opencop.core.api.general.business.model.auth.OidcAdmissionCommand;
import com.opencop.core.api.general.business.model.auth.OidcAdmissionDeniedException;
import java.util.List;

public class OidcAdmissionPolicy {

  private final List<IdpRelationship> allowedRelationships;

  public OidcAdmissionPolicy(List<IdpRelationship> allowedRelationships) {
    this.allowedRelationships = List.copyOf(allowedRelationships);
  }

  public void requireAllowed(OidcAdmissionCommand command) {
    if (!allowedRelationships.isEmpty()
        && allowedRelationships.stream()
            .noneMatch(relationship -> matches(command, relationship))) {
      throw new OidcAdmissionDeniedException();
    }
  }

  private boolean matches(OidcAdmissionCommand command, IdpRelationship relationship) {
    return command.groups().stream().anyMatch(relationship.group()::contains)
        && (relationship.roles().isEmpty()
            || relationship.roles().stream().anyMatch(command.roles()::contains));
  }
}
