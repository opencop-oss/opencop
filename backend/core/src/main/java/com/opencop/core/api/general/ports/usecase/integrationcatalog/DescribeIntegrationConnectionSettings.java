// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.integrationcatalog;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetupUnavailableException;
import com.opencop.core.sdk.business.model.integration.connection.settings.ConnectionSettingsDescriptor;
import java.util.UUID;

public interface DescribeIntegrationConnectionSettings {

  /**
   * Describes the settings a connection to one integration schema version may override, with the
   * current deployment defaults. A schema that declares no settings is described as empty.
   *
   * @throws IntegrationConnectionSetupUnavailableException when the reference is unknown, inactive
   *     or unavailable in this runtime
   */
  ConnectionSettingsDescriptor describe(UUID integrationId, int schemaVersion);
}
