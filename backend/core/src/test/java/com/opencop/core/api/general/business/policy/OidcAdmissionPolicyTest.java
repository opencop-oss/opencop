// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.opencop.core.api.general.business.model.auth.IdpRelationship;
import com.opencop.core.api.general.business.model.auth.OidcAdmissionCommand;
import java.util.List;
import org.junit.jupiter.api.Test;

class OidcAdmissionPolicyTest {

  @Test
  void preservesConfiguredGroupContainsUserGroupRelationshipSemantics() {
    var policy =
        new OidcAdmissionPolicy(
            List.of(new IdpRelationship("tenant-group-a", List.of("operator"))));
    var command =
        new OidcAdmissionCommand(
            "https://issuer.test",
            "verified-subject",
            "stable-id",
            List.of(),
            List.of("group-a"),
            List.of("operator"),
            null);

    assertDoesNotThrow(() -> policy.requireAllowed(command));
  }
}
