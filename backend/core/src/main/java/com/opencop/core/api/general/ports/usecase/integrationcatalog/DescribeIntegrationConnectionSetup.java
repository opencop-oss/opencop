// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.integrationcatalog;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetup;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetupUnavailableException;
import java.util.UUID;

public interface DescribeIntegrationConnectionSetup {

  /**
   * Describes how a client sets up a TCP connection to one integration schema version.
   *
   * @throws IntegrationConnectionSetupUnavailableException when the reference is unknown, inactive,
   *     unavailable in this runtime, or declares no TCP connection setup
   */
  IntegrationConnectionSetup describe(UUID integrationId, int schemaVersion);
}
