// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.business.usecase.identity;

import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.business.usecase.user.AccountMutationUsecase;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.iam.ports.usecase.identity.UserManager;

public class UserManagersFactory {

  private UserManagersFactory() {}

  public static UserManager<InternalUser> userManager(
      final UserService userService, final AccountMutationUsecase accountMutationUsecase) {
    return new UserIdentityManager(userService, accountMutationUsecase);
  }
}
