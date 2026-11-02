// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.mapping;

import com.opencop.core.sdk.business.model.profile.DelimiterModes;
import java.util.Objects;
import java.util.UUID;

/**
 * A TCP framing an integration provides in code. Connections reference it by {@code id}, which is
 * also the framing identity in the integration's mapper/framing pairs.
 */
public record FramingDefinition(UUID id, DelimiterModes mode, String delimiter) {
  public FramingDefinition {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(mode, "mode");
  }
}
