// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.input.descriptor;

import java.util.Optional;

/**
 * One declarative input slot of an extractor descriptor.
 *
 * <p>Sealed per kind rather than carrying a kind and a set of format fields that only some kinds
 * may populate. A scalar field has nowhere to declare a file format, so the contradiction is
 * unrepresentable instead of rejected at construction, and a file-bearing field cannot be built
 * without the extensions a client needs for its file picker.
 *
 * <p>Everything declared here, formats included, is a <strong>client hint only</strong>. The
 * extractor's {@code validate} remains the sole authority on whether supplied input is acceptable,
 * and the checks it performs are not duplicated here. A field may therefore be declared optional
 * and still be rejected in combination with another, which no field kind can express.
 */
public sealed interface ExtractorField permits TextField, SecretField, FileField, FileListField {

  /**
   * Returns the payload key the value is supplied under; case-sensitive, so multipart role
   * spellings such as {@code keyStore} stay natural.
   */
  String name();

  /** Returns the display text for the field. */
  String label();

  /** Returns whether the field must be supplied. */
  boolean required();

  /**
   * Returns what the field carries.
   *
   * <p>Derived from the implementing type rather than stored, so it can never disagree with the
   * shape of the record declaring it.
   */
  ExtractorFieldKind kind();

  /**
   * Returns the shared field this declares, when it is one. A client localizes a standard field by
   * this key and falls back to {@link #label()} without a translation.
   */
  Optional<StandardExtractorField> standard();
}
