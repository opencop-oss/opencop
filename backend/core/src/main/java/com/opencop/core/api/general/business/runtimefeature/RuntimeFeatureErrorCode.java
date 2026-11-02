// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.runtimefeature;

public enum RuntimeFeatureErrorCode {
  RUNTIME_FEATURE_INVALID_REQUEST,
  RUNTIME_FEATURE_INVALID_USER_GROUP_TARGETS,
  RUNTIME_FEATURE_NOT_FOUND,
  RUNTIME_FEATURE_REVISION_CONFLICT,
  RUNTIME_FEATURE_BUSY,
  RUNTIME_FEATURE_REQUIRED_ROW_MISSING,

  /** The submitted name is absent, blank, over 64 characters, or outside the wire grammar. */
  RUNTIME_FEATURE_INVALID_NAME,

  /** The submitted name is reserved for backend enforcement by the BackendRuntimeFeature enum. */
  RUNTIME_FEATURE_RESERVED_NAME,

  /**
   * The name already exists under a consumer class registration cannot claim — a BACKEND_ENFORCED
   * row. Registration never reclassifies, so this is a collision rather than convergence.
   */
  RUNTIME_FEATURE_CLASS_CONFLICT
}
