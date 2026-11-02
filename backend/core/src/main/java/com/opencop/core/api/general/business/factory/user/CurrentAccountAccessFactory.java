// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.factory.user;

import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import org.springframework.stereotype.Component;

@Component
public class CurrentAccountAccessFactory {

  public CurrentAccountAccess create(InternalUser user) {
    return new CurrentAccountAccess(
        user.id(),
        user.active(),
        user.admin(),
        user.sessionGeneration(),
        user.accountAccessRevision());
  }
}
