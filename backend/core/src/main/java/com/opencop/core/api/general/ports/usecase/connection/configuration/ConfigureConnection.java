// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.configuration;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.connection.configuration.ConnectionConfigurationChange;
import com.opencop.core.sdk.model.connection.ConnectionInfo;
import java.util.UUID;

/** Changes what a connection applies while it runs, without reconnecting it. */
public interface ConfigureConnection {

  /**
   * @return the connection as the requester sees it once the change is applied
   */
  ConnectionInfo configure(UUID sourceId, ConnectionConfigurationChange change, Caller actor);
}
