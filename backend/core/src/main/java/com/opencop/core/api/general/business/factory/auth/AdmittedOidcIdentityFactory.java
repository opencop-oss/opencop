// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.factory.auth;

import com.opencop.core.api.general.business.model.auth.AdmittedOidcIdentity;
import com.opencop.core.api.general.business.model.auth.OidcAdmissionCommand;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import java.time.Clock;
import java.time.Instant;

public class AdmittedOidcIdentityFactory {

  private final Clock clock;

  public AdmittedOidcIdentityFactory(Clock clock) {
    this.clock = clock;
  }

  public AdmittedOidcIdentity create(
      OidcAdmissionCommand command, CurrentAccountAccess accountAccess) {
    return new AdmittedOidcIdentity(
        accountAccess.internalUserId(),
        command.issuer(),
        command.subject(),
        command.idpUserId(),
        command.profile(),
        accountAccess.sessionGeneration(),
        Instant.now(clock));
  }
}
