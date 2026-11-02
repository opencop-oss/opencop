// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.runtimefeature;

import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureActor;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureRegistrationResult;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureWriteResult;
import java.util.OptionalLong;

public interface RuntimeFeatureMutationGateway {

  /**
   * Replaces the audience for a registered feature.
   *
   * @throws RuntimeFeatureBusyException when the mutation cannot safely proceed without waiting
   */
  RuntimeFeatureWriteResult replaceAudience(
      String name,
      RuntimeFeatureAudience audience,
      OptionalLong expectedRevision,
      RuntimeFeatureActor actor);

  /**
   * Registers {@code name} as {@code FRONTEND_ONLY} / {@code NONE} / revision 0 if — and only if —
   * no row with that name exists.
   *
   * <p>Create-missing-only by construction: an existing row is returned untouched, never updated,
   * reclassified, enabled, or given history. Implementations must reach that outcome without
   * provoking a constraint violation, so a losing caller in a race still observes a healthy
   * transaction rather than a rollback.
   */
  RuntimeFeatureRegistrationResult register(String name, RuntimeFeatureActor actor);
}
