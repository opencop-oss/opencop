// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

import com.opencop.core.sdk.business.model.profile.DelimiterModes;
import java.util.Objects;
import java.util.UUID;

/**
 * How a TCP stream is split into messages, as the integration offering it defines in code.
 *
 * <p>Carries the mode a client renders the choice by, and not the delimiter itself. An integration
 * provides its framings in code and no catalog records them, so a client that receives only a
 * framing identifier has nothing to resolve it against; the delimiter is how the stream is split
 * rather than something an operator chooses between, so it stays out of the setup form.
 *
 * @param id the identifier a mapper/framing choice references
 * @param mode how the stream is split
 */
public record ConnectionFraming(UUID id, DelimiterModes mode) {

  public ConnectionFraming {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(mode, "mode");
  }
}
