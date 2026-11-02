// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.gateway.db.mapper;

public interface FilterInfoMapper<I, O> {

  O toNewDatabaseEntity(I businessEntity);

  O toDatabaseEntity(I businessEntity);

  I toBusinessEntity(O dbEntity);
}
