// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.user;

import java.util.UUID;

public class AccountAlreadyActiveException extends RuntimeException {

  public AccountAlreadyActiveException(UUID internalUserId) {
    super("Account is already active: " + internalUserId);
  }
}
