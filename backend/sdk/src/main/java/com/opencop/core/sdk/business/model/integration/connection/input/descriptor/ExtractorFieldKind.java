// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.input.descriptor;

/**
 * Kind of value one extractor field carries.
 *
 * <p>Cardinality is not a separate attribute: {@link #FILE_LIST} is the only multi-valued kind, and
 * whether a field may be omitted is carried by the field's required flag.
 */
public enum ExtractorFieldKind {

  /** Free text shown as typed. */
  TEXT,

  /** Free text that must never be echoed back or logged. */
  SECRET,

  /** Exactly one uploaded file. */
  FILE,

  /** One or more uploaded files supplied under the same field name. */
  FILE_LIST
}
