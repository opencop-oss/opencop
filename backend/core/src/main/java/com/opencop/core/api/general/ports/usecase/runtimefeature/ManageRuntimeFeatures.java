// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.runtimefeature;

import com.opencop.core.api.general.business.model.runtimefeature.RuntimeFeatureReconciliation;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureActor;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureRegistrationResult;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureState;
import java.util.List;
import java.util.OptionalLong;

public interface ManageRuntimeFeatures {
  RuntimeFeatureState replaceAudience(
      String name,
      RuntimeFeatureAudience audience,
      OptionalLong expectedRevision,
      RuntimeFeatureActor actor);

  /**
   * Registers a frontend-only feature definition by name, creating it only when absent.
   *
   * <p>The caller supplies nothing but the name: consumer class, audience, and revision are
   * server-owned. Re-registering an existing frontend-only name is successful convergence, not an
   * error, which lets a client reconcile a whole manifest without branching on error responses.
   */
  RuntimeFeatureRegistrationResult register(String name, RuntimeFeatureActor actor);

  /**
   * Registers every absent name in one call, reporting per-name outcomes instead of failing as a
   * unit.
   *
   * <p>This is the operation a client uses to converge a whole declared manifest. Each name is
   * attempted independently, so one malformed or backend-owned entry cannot block the others, and
   * the names that did register stay registered — see {@link RuntimeFeatureReconciliation}.
   *
   * <p>Duplicates in {@code names} are collapsed; a name is attempted at most once per call.
   *
   * @throws IllegalArgumentException when {@code names} contains a null entry — a rejection is
   *     keyed by name, so there is nothing to report a null under; validate the envelope first
   */
  RuntimeFeatureReconciliation reconcile(List<String> names, RuntimeFeatureActor actor);
}
