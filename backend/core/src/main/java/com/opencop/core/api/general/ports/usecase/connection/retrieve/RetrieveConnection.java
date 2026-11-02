// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.retrieve;

import com.opencop.core.sdk.model.connection.ConnectionInfo;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import java.util.List;
import java.util.UUID;

public interface RetrieveConnection {

  ConnectionInfo get(UUID sourceId, UUID userId);

  default List<ConnectionInfo> getAll(UUID userId) {
    return getAll(userId, RecordVisibility.ACTIVE);
  }

  List<ConnectionInfo> getAll(UUID userId, RecordVisibility recordVisibility);
}
