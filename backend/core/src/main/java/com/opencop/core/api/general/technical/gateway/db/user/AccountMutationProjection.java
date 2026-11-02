// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.user;

import java.time.Instant;
import java.util.UUID;

public interface AccountMutationProjection {

  UUID getInternalUserId();

  String getEmail();

  boolean isActive();

  boolean isAdministrator();

  long getSessionGeneration();

  long getAccountRevision();

  Instant getDeletedAt();
}
