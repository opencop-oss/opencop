// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import io.swagger.v3.oas.annotations.media.Schema;

public record ConnectionPatchRequest(
    String name,
    Boolean defaultVisible,
    String access,
    @Schema(hidden = true) IntegrationConnectionSchemaReference integration) {
  public ConnectionPatchRequest(String name, Boolean defaultVisible) {
    this(name, defaultVisible, null, null);
  }

  public ConnectionPatchRequest(String name, Boolean defaultVisible, String access) {
    this(name, defaultVisible, access, null);
  }
}
