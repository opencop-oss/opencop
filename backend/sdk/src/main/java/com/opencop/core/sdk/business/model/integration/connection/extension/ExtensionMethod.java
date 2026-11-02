// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.extension;

/** The HTTP method an extension operation answers on. */
public enum ExtensionMethod {
  /** Reads without changing anything; takes no request body. */
  GET,
  /** Creates, or runs an action. */
  POST,
  /** Replaces what the path names. */
  PUT,
  /** Changes part of what the path names. */
  PATCH,
  /** Removes what the path names. */
  DELETE
}
