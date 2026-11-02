// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.business.usecase.identity;

import com.opencop.core.api.general.business.model.user.AccountMutationNotFoundException;
import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.business.usecase.user.AccountMutationUsecase;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.iam.ports.usecase.identity.UserManager;
import com.opencop.core.api.iam.ports.usecase.identity.UserManagerException;
import com.opencop.core.sdk.model.iam.user.UserReadFilter;
import com.opencop.core.sdk.ports.gateway.idp.IdpException;
import com.opencop.core.sdk.ports.gateway.idp.IdpExceptionCodes;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

public class UserIdentityManager implements UserManager<InternalUser> {

  private final UserService userService;
  private final AccountMutationUsecase accountMutationUsecase;

  UserIdentityManager(UserService userService, AccountMutationUsecase accountMutationUsecase) {
    this.userService = userService;
    this.accountMutationUsecase = accountMutationUsecase;
  }

  @Override
  public InternalUser get(UUID userId) {
    try {
      return userService.get(userId);
    } catch (IdpException e) {
      throw new UserManagerException(e.getCode(), e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @Override
  public PageResponse<InternalUser> listUsers(UserReadFilter filter, Pageable pageable) {
    return userService.getPage(filter, pageable);
  }

  @Override
  public void updateAdminRole(UUID userId, boolean isAdmin) {
    try {
      accountMutationUsecase.setAdministrator(userId, isAdmin);
    } catch (AccountMutationNotFoundException _) {
      throw new UserManagerException(
          IdpExceptionCodes.NOT_FOUND.code(),
          IdpExceptionCodes.NOT_FOUND.description(),
          HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
