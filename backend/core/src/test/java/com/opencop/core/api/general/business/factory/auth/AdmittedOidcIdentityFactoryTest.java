// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.factory.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.general.business.model.auth.OidcAdmissionCommand;
import com.opencop.core.api.general.business.model.auth.OidcProfile;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdmittedOidcIdentityFactoryTest {

  @Test
  void assemblesTheAdmittedIdentityFromVerifiedInputAndCurrentAccountState() {
    var authenticatedAt = Instant.parse("2026-07-23T15:00:00Z");
    var internalUserId = UUID.fromString("8d065639-90b3-4991-adac-8dc497f13b35");
    var profile =
        new OidcProfile(
            "preferred", "Profile Name", "Profile", "Name", "profile@example.test", true);
    var command =
        new OidcAdmissionCommand(
            "https://issuer.test",
            "verified-subject",
            "stable-id",
            List.of("CORE-API"),
            List.of("group-a"),
            List.of("operator"),
            profile);
    var accountAccess = new CurrentAccountAccess(internalUserId, true, false, 17, 4);
    var factory = new AdmittedOidcIdentityFactory(Clock.fixed(authenticatedAt, ZoneOffset.UTC));

    var identity = factory.create(command, accountAccess);

    assertThat(identity.internalUserId()).isEqualTo(internalUserId);
    assertThat(identity.issuer()).isEqualTo("https://issuer.test");
    assertThat(identity.subject()).isEqualTo("verified-subject");
    assertThat(identity.idpUserId()).isEqualTo("stable-id");
    assertThat(identity.profile()).isEqualTo(profile);
    assertThat(identity.sessionGeneration()).isEqualTo(17);
    assertThat(identity.authenticatedAt()).isEqualTo(authenticatedAt);
  }
}
