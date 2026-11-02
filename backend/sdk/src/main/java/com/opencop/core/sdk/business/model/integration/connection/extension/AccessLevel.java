// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.extension;

/** What a caller must be allowed on a connection to invoke an extension operation on it. */
public enum AccessLevel {
  /** The operation only reads: anyone who can see the connection may invoke it. */
  READ,
  /** The operation changes something, here or remotely: only who administers the connection. */
  MANAGE
}
