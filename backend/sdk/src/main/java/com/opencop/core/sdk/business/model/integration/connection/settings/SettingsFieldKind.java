// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.settings;

/** The value type of a connection settings field, which decides its wire form and constraint. */
public enum SettingsFieldKind {
  /** Non-blank text of bounded length. */
  TEXT,
  /** A finite number within bounds, optionally whole. */
  NUMBER,
  /** True or false. */
  BOOLEAN,
  /** A length of time within bounds, written as an ISO-8601 duration on the wire. */
  DURATION,
  /** One of a fixed list of text options. */
  CHOICE
}
