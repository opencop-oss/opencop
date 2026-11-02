// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.integrationcatalog;

import java.util.UUID;

public interface ManageIntegrationActivation {

  void setActive(UUID integrationId, boolean active);
}
