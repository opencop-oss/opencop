// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.create;

import com.opencop.core.api.general.business.model.connection.create.ConnectionCreation;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationResult;

/** Creates a connection. */
public interface CreateConnection {

  ConnectionCreationResult create(ConnectionCreation creation);
}
