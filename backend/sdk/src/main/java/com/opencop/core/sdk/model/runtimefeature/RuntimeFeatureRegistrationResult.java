// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.runtimefeature;

import java.util.Objects;

/**
 * Outcome of a create-missing-only runtime feature registration.
 *
 * <p>Deliberately separate from {@link RuntimeFeatureWriteResult}: registration never mutates an
 * existing row, so it shares no outcome with the audience-replacement path and must not force that
 * sealed hierarchy to grow a case its exhaustive switches would have to handle.
 *
 * <p>{@link AlreadyExists} is not an error by itself — a repeated registration of a {@code
 * FRONTEND_ONLY} name is successful convergence. Callers inspect {@link
 * RuntimeFeatureState#consumerClass()} to detect an ownership collision with a {@code
 * BACKEND_ENFORCED} row.
 */
public sealed interface RuntimeFeatureRegistrationResult
    permits RuntimeFeatureRegistrationResult.Created,
        RuntimeFeatureRegistrationResult.AlreadyExists {

  /** The canonical stored row, whether this caller created it or found it. */
  RuntimeFeatureState state();

  /** This caller's insert won; the row is {@code FRONTEND_ONLY} / {@code NONE} / revision 0. */
  record Created(RuntimeFeatureState state) implements RuntimeFeatureRegistrationResult {
    public Created {
      Objects.requireNonNull(state, "state is required");
    }
  }

  /** The name was already registered; nothing was written. */
  record AlreadyExists(RuntimeFeatureState state) implements RuntimeFeatureRegistrationResult {
    public AlreadyExists {
      Objects.requireNonNull(state, "state is required");
    }
  }
}
