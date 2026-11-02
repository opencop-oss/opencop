// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.patch;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import java.util.UUID;

public interface PatchConnection {

  void patch(
      UUID sourceId,
      String name,
      Boolean defaultVisible,
      String access,
      IntegrationConnectionSchemaReference integration,
      Caller actor);
}
