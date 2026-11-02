// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.access;

import com.opencop.core.sdk.model.datasource.AccessMode;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A connection that writes, encoding with its egress mapper. */
public record WriteAccess(UUID egressMapper) implements ConnectionAccess {

  public WriteAccess {
    Objects.requireNonNull(egressMapper, "egressMapper");
  }

  @Override
  public AccessMode mode() {
    return AccessMode.WRITE;
  }

  @Override
  public Optional<UUID> ingressMapperId() {
    return Optional.empty();
  }

  @Override
  public Optional<UUID> egressMapperId() {
    return Optional.of(egressMapper);
  }
}
