// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.mapping.ingress;

/**
 * Describes whether a Java ingress mapper can provide a stable OpenCOP object ID for transition
 * tracking.
 */
public enum ObjectIdStability {
  UNKNOWN,
  STABLE_PER_ENTITY
}
