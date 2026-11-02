// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.iam;

import java.util.UUID;

public interface AccessRepository<T> {

  boolean userHasAccess(UUID userId, UUID sourceId);

  T userRole(UUID userId, UUID sourceId);
}
