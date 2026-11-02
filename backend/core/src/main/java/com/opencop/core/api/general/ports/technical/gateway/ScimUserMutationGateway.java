// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.technical.gateway;

import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.types.UserResource;
import java.util.List;
import java.util.UUID;

public interface ScimUserMutationGateway {

  CreateMutation create(UserResource request);

  ActiveMutation replace(String internalUserId, UserResource request);

  PatchMutation patch(UUID internalUserId, PatchRequest request);

  UUID requireExisting(UUID internalUserId);

  record CreateMutation(
      UUID internalUserId,
      String intendedEmail,
      boolean active,
      boolean reactivation,
      String conflictDetail,
      UserResource response) {}

  record ActiveMutation(UUID internalUserId, boolean active, UserResource response) {}

  record PatchMutation(
      UUID internalUserId, List<Boolean> requestedActiveStates, UserResource response) {

    public PatchMutation {
      requestedActiveStates = List.copyOf(requestedActiveStates);
    }
  }
}
