// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.behavior;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;

/**
 * Common identity and applicability contract for one focused connection behavior.
 *
 * <p>Implementations are published as ordinary dependency-injection beans. They must be immutable,
 * stateless, and safe for concurrent use by multiple connections. A single implementation may also
 * implement several capability interfaces when those triggers express one cohesive behavior.
 *
 * <p>Every value a contribution resolves for a plan is derived from the connection's effective
 * settings, supplied as {@code S} each time a plan is compiled. A contribution never stores
 * per-connection settings, so one instance serves every connection of its schema.
 *
 * <p>Names must be non-blank and unique within one connection schema version. Lower order values
 * execute first; equal values are ordered by name. The registry evaluates {@link
 * #isEnabled(Object)} exactly once while compiling a connection plan.
 *
 * @param <S> the effective settings type of the contribution's connection schema
 */
public interface ConnectionBehaviorContribution<S> {

  /** Returns the connection schema version this behavior belongs to. */
  IntegrationConnectionSchema schema();

  /**
   * Returns the effective settings type this contribution reads. A schema without connection
   * settings uses {@link NoConnectionSettings}.
   */
  Class<S> settingsType();

  /** Returns the stable behavior name used for ordering, diagnostics, and uniqueness validation. */
  String name();

  /** Returns the deterministic execution order; lower values execute first. */
  default int order() {
    return 0;
  }

  /** Returns whether all capabilities implemented by this contribution are enabled. */
  default boolean isEnabled(S settings) {
    return true;
  }
}
