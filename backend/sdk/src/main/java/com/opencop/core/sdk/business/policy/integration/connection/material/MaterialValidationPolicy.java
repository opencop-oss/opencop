// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy.integration.connection.material;

/** Reusable validation policy for material produced by any caller or integration capability. */
@FunctionalInterface
public interface MaterialValidationPolicy<T> {

  /**
   * Validates one complete material value.
   *
   * @throws IllegalArgumentException when the material is incomplete or invalid
   */
  void validate(T material);
}
