// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.input;

/** Stable host-facing failure families for connection-input extractor execution. */
public enum ConnectionInputFailure {
  INVALID_INPUT,
  RESOURCE_ACCESS_FAILED,
  EXTRACTION_FAILED,
  INVALID_RESULT
}
