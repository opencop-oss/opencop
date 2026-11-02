// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.profile;

import java.util.Objects;
import java.util.UUID;

/**
 * Reference to one exact mapper profile version, matching the {@code (id, version)} primary key of
 * {@code profiles.data_mapper}.
 *
 * <p>The version is always carried: a mapper id alone does not identify one revision of the
 * mapping. Versions start at one, so a non-positive version identifies nothing and is rejected.
 *
 * <p>The version is provenance, not lifetime runtime authority. It records the exact revision
 * resolved at that moment; it does not freeze what a connection built from it will decode with. A
 * connection stores only its mapper id and always runs that mapper's latest revision, so an
 * existing connection adopts an edit once the runtime caches converge. Code that must know which
 * mapping runs now resolves the connection's mapper id rather than replaying this version.
 *
 * @param id mapper profile id
 * @param version mapper profile version resolved at that moment, always positive
 */
public record MapperReference(UUID id, int version) {

  public MapperReference {
    Objects.requireNonNull(id, "id");
    if (version <= 0) {
      throw new IllegalArgumentException("version must be positive");
    }
  }
}
