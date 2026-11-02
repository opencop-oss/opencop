// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.factory.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentAccountAccessFactoryTest {

  @Test
  void createsCompleteCurrentAccountAccessFromInternalUser() {
    var userId = UUID.fromString("cc3c7e89-d44a-48b1-85fe-3d905685ef28");
    var now = Instant.parse("2026-07-22T12:00:00Z");
    var user =
        new InternalUser(
            userId,
            "idp-user",
            "idp-username",
            "https://issuer.test",
            "username",
            "user@opencop.test",
            "Open",
            "Cop",
            "Open Cop",
            false,
            true,
            7,
            11,
            now,
            now,
            null);

    var access = new CurrentAccountAccessFactory().create(user);

    assertEquals(new CurrentAccountAccess(userId, false, true, 7, 11), access);
  }
}
