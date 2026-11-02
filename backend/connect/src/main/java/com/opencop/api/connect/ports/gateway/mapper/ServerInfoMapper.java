// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.ports.gateway.mapper;

public interface ServerInfoMapper<I, O> {

  O toNewDatabaseEntity(I businessEntity);

  O toDatabaseEntity(I businessEntity);

  I toBusinessEntity(O dbEntity);
}
