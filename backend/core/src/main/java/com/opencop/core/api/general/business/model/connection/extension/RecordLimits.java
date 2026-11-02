// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.extension;

/**
 * What one extension may store on one connection.
 *
 * @param maxDocumentBytes the largest document, as stored JSON
 * @param maxRecords the most records, across all of the extension's collections
 */
public record RecordLimits(long maxDocumentBytes, int maxRecords) {

  public RecordLimits {
    if (maxDocumentBytes < 1 || maxRecords < 1) {
      throw new IllegalArgumentException("Record limits must be positive");
    }
  }
}
