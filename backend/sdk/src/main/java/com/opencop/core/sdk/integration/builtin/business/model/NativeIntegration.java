// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.business.model;

import com.opencop.core.sdk.business.model.integration.Integration;
import java.util.UUID;

/**
 * The Native integration, which owns the mappers OpenCOP ships for open payload formats and the
 * connections set up from the host's own input rather than through a plugin.
 */
public record NativeIntegration() implements Integration {

  public static final UUID NATIVE_ID = UUID.fromString("5b96caf4-20dd-4a8a-8f1b-09fb594265be");

  @Override
  public UUID id() {
    return NATIVE_ID;
  }

  @Override
  public String name() {
    return "Native";
  }

  @Override
  public String description() {
    return "Mappers built into OpenCOP for open payload formats.";
  }
}
