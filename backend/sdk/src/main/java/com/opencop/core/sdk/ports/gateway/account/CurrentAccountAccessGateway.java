// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.account;

import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import java.util.Optional;
import java.util.UUID;

public interface CurrentAccountAccessGateway {

  Optional<CurrentAccountAccess> findByInternalUserId(UUID internalUserId);
}
