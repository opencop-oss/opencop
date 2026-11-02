// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.runtimefeature;

import com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureState;
import java.util.List;
import java.util.Objects;

/**
 * Per-name outcome of reconciling a client's declared feature manifest against the catalog.
 *
 * <p>Reconciliation deliberately reports outcomes instead of failing as a unit. One malformed or
 * backend-owned name in a manifest must not block the rest, and every name that did register is
 * already durable — so an all-or-nothing contract would either lie about what happened or throw
 * away real progress. That makes the whole operation safe to re-run: the names that converged are
 * no-ops on the next attempt.
 *
 * <p>Lives in core rather than the sdk because a rejection carries a {@link
 * RuntimeFeatureErrorCode}, which is a business-layer concept the sdk models know nothing about.
 *
 * @param created names this request inserted, at {@code FRONTEND_ONLY} / {@code NONE} / revision 0
 * @param alreadyRegistered names that already existed as {@code FRONTEND_ONLY}, returned untouched
 * @param rejected names that could not be registered, each with the reason
 */
public record RuntimeFeatureReconciliation(
    List<RuntimeFeatureState> created,
    List<RuntimeFeatureState> alreadyRegistered,
    List<Rejection> rejected) {

  public RuntimeFeatureReconciliation {
    created = List.copyOf(Objects.requireNonNull(created, "created is required"));
    alreadyRegistered =
        List.copyOf(Objects.requireNonNull(alreadyRegistered, "alreadyRegistered is required"));
    rejected = List.copyOf(Objects.requireNonNull(rejected, "rejected is required"));
  }

  /** True when every submitted name is now registered as a frontend-only definition. */
  public boolean complete() {
    return rejected.isEmpty();
  }

  /**
   * A name the catalog would not accept.
   *
   * <p>The {@code code} is the same value the single-name {@code POST} would have returned as an
   * error body, so a client can apply one classification table to both paths — notably to tell a
   * deterministic rejection (fix the manifest) from a transient one (retry).
   */
  public record Rejection(String name, RuntimeFeatureErrorCode code, String message) {
    public Rejection {
      Objects.requireNonNull(name, "name is required");
      Objects.requireNonNull(code, "code is required");
      Objects.requireNonNull(message, "message is required");
    }
  }
}
