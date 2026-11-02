// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.permissions;

import java.util.UUID;

public interface CreateSourcePermissions {

  void createPermissions(UUID sourceId, UUID userId);
}
