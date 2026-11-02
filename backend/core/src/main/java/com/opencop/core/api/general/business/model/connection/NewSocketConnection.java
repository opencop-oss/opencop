// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.connection.setup.ConnectionSetup;
import java.util.Objects;
import java.util.UUID;

/**
 * A TCP, UDP or RTSP connection to create: who it is, and the validated setup it is created with.
 *
 * @param owner the user creating the connection, who becomes its owner
 */
public record NewSocketConnection(UUID sourceId, String name, Caller owner, ConnectionSetup setup) {

  public NewSocketConnection {
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(setup, "setup");
  }
}
