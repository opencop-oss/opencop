// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.extension;

/** Stable host-facing failure families for an extension operation. */
public enum ExtensionFailure {
  /** The request cannot be served as given. */
  BAD_REQUEST,
  /** What the request names does not exist. */
  NOT_FOUND,
  /** The request conflicts with the current state, here or remotely. */
  CONFLICT,
  /** The remote system failed or answered with something unusable. */
  UPSTREAM_FAILURE,
  /** The remote system did not answer in time. */
  UPSTREAM_TIMEOUT
}
