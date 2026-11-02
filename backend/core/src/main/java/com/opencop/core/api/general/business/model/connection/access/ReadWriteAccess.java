// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.access;

import com.opencop.core.sdk.model.datasource.AccessMode;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A connection that reads with its ingress mapper and writes with its egress mapper. */
public record ReadWriteAccess(UUID ingressMapper, UUID egressMapper) implements ConnectionAccess {

  public ReadWriteAccess {
    Objects.requireNonNull(ingressMapper, "ingressMapper");
    Objects.requireNonNull(egressMapper, "egressMapper");
  }

  @Override
  public AccessMode mode() {
    return AccessMode.READ_WRITE;
  }

  @Override
  public Optional<UUID> ingressMapperId() {
    return Optional.of(ingressMapper);
  }

  @Override
  public Optional<UUID> egressMapperId() {
    return Optional.of(egressMapper);
  }
}
