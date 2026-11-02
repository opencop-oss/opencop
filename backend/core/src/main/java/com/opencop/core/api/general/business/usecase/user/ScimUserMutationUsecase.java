// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.user;

import com.opencop.core.api.general.business.model.user.AccountAlreadyActiveException;
import com.opencop.core.api.general.business.model.user.ScimUserMutationConflictException;
import com.opencop.core.api.general.ports.technical.gateway.ScimUserMutationGateway;
import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.types.UserResource;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ScimUserMutationUsecase {

  private final ScimUserMutationGateway scimUserGateway;
  private final AccountMutationUsecase accountMutationUsecase;

  public ScimUserMutationUsecase(
      ScimUserMutationGateway scimUserGateway, AccountMutationUsecase accountMutationUsecase) {
    this.scimUserGateway = scimUserGateway;
    this.accountMutationUsecase = accountMutationUsecase;
  }

  @Transactional
  public UserResource create(UserResource request) {
    var mutation = scimUserGateway.create(request);
    if (mutation.reactivation()) {
      try {
        accountMutationUsecase.reactivate(
            mutation.internalUserId(), mutation.intendedEmail(), mutation.active());
      } catch (AccountAlreadyActiveException exception) {
        throw new ScimUserMutationConflictException(mutation.conflictDetail(), exception);
      }
    } else {
      accountMutationUsecase.create(mutation.internalUserId(), mutation.active());
    }
    return mutation.response();
  }

  @Transactional
  public UserResource replace(String internalUserId, UserResource request) {
    var mutation = scimUserGateway.replace(internalUserId, request);
    accountMutationUsecase.setActive(mutation.internalUserId(), mutation.active());
    return mutation.response();
  }

  @Transactional
  public UserResource patch(UUID internalUserId, PatchRequest request) {
    var mutation = scimUserGateway.patch(internalUserId, request);
    for (boolean active : mutation.requestedActiveStates()) {
      accountMutationUsecase.setActive(mutation.internalUserId(), active);
    }
    return mutation.response();
  }

  @Transactional
  public void delete(UUID internalUserId) {
    var existingUserId = scimUserGateway.requireExisting(internalUserId);
    accountMutationUsecase.delete(existingUserId, Instant.now());
  }
}
