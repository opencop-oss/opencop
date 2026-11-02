// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway;

import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import java.util.Optional;
import java.util.UUID;

public interface CurrentAccountAccessCacheGateway {

  Optional<CurrentAccountAccess> findByInternalUserId(UUID internalUserId);

  void populate(CurrentAccountAccess access);

  void evict(UUID internalUserId);

  void scheduleEvictionAfterCommit(UUID internalUserId);
}
