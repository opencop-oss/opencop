// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.access;

import com.opencop.core.sdk.model.datasource.AccessMode;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A connection that reads, decoding with its ingress mapper. */
public record ReadAccess(UUID ingressMapper) implements ConnectionAccess {

  public ReadAccess {
    Objects.requireNonNull(ingressMapper, "ingressMapper");
  }

  @Override
  public AccessMode mode() {
    return AccessMode.READ;
  }

  @Override
  public Optional<UUID> ingressMapperId() {
    return Optional.of(ingressMapper);
  }

  @Override
  public Optional<UUID> egressMapperId() {
    return Optional.empty();
  }
}
