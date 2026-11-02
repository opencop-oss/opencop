// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway;

import com.opencop.core.api.general.business.model.user.AccountMutationState;
import java.util.Optional;
import java.util.UUID;

public interface AccountMutationGateway {

  Optional<AccountMutationState> findByInternalUserIdForUpdate(UUID internalUserId);

  void saveAndFlush(AccountMutationState account);
}
