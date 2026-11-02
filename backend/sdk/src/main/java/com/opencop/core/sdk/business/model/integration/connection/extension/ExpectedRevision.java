// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.extension;

/**
 * The revision a write expects the record to be at, so that two writers cannot silently overwrite
 * each other. Every write names one.
 */
public sealed interface ExpectedRevision {

  /** The record must not exist yet: the write creates it. */
  static ExpectedRevision absent() {
    return Absent.INSTANCE;
  }

  /** The record must exist at exactly this revision. */
  static ExpectedRevision of(long revision) {
    return new Exactly(revision);
  }

  /** Whatever the record's state: the last write wins. */
  static ExpectedRevision any() {
    return Any.INSTANCE;
  }

  /** The record must not exist yet. */
  enum Absent implements ExpectedRevision {
    INSTANCE
  }

  /** The record must exist at exactly this revision. */
  record Exactly(long revision) implements ExpectedRevision {

    public Exactly {
      if (revision < 1) {
        throw new IllegalArgumentException("revision must be at least 1");
      }
    }
  }

  /** Whatever the record's state. */
  enum Any implements ExpectedRevision {
    INSTANCE
  }
}
