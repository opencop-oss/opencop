// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.setup;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreation;
import com.opencop.core.sdk.model.connection.ConnectionInfo;
import java.util.UUID;

/** Replaces how a connection connects, restarting it on the new setup. */
public interface ReplaceConnectionSetup {

  /**
   * @param setup the new setup, shaped as a creation request; its name is not read
   * @return the connection as the requester sees it once the setup is replaced
   */
  ConnectionInfo replace(UUID sourceId, ConnectionCreation setup, Caller caller);
}
