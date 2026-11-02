// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.extension;

import com.opencop.core.api.general.business.model.connection.extension.RecordLimits;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.sdk.business.model.integration.connection.extension.ConnectionRecord;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExpectedRevision;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionException;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionFailure;
import java.util.Optional;

/** When an extension may write or delete one of its connection records. */
public final class ConnectionRecordPolicy {

  private static final long FIRST_REVISION = 1;

  private ConnectionRecordPolicy() {}

  /**
   * The revision a write stores the record at.
   *
   * @throws ConnectionExtensionException when the record is not at the expected revision
   */
  public static long nextRevision(Optional<ConnectionRecord> current, ExpectedRevision expected)
      throws ConnectionExtensionException {
    if (!isAt(current, expected)) {
      throw failure(ConnectionExceptionCodes.CONNECTION_RECORD_REVISION_CONFLICT);
    }
    return current.map(stored -> stored.revision() + 1).orElse(FIRST_REVISION);
  }

  /**
   * Whether a delete removes anything: deleting a missing record without expecting a revision does
   * nothing.
   *
   * @throws ConnectionExtensionException when the record is not at the expected revision
   * @throws IllegalArgumentException when expecting the record to be absent
   */
  public static boolean isDeletable(Optional<ConnectionRecord> current, ExpectedRevision expected)
      throws ConnectionExtensionException {
    if (expected instanceof ExpectedRevision.Absent) {
      throw new IllegalArgumentException("A delete cannot expect the record to be absent");
    }
    if (!isAt(current, expected)) {
      throw failure(ConnectionExceptionCodes.CONNECTION_RECORD_REVISION_CONFLICT);
    }
    return current.isPresent();
  }

  /**
   * Keeps one extension's records on a connection within the deployment's limits. Only a new record
   * counts against the number of records; replacing one does not change it.
   *
   * @param stored how many records the extension already keeps on the connection
   * @throws ConnectionExtensionException when the write would exceed a limit
   */
  public static void assertWithinLimits(
      long documentBytes, boolean isNewRecord, long stored, RecordLimits limits)
      throws ConnectionExtensionException {
    var tooLarge = documentBytes > limits.maxDocumentBytes();
    var tooMany = isNewRecord && stored >= limits.maxRecords();
    if (tooLarge || tooMany) {
      throw failure(ConnectionExceptionCodes.CONNECTION_RECORD_LIMIT);
    }
  }

  private static boolean isAt(Optional<ConnectionRecord> current, ExpectedRevision expected) {
    return switch (expected) {
      case ExpectedRevision.Any _ -> true;
      case ExpectedRevision.Absent _ -> current.isEmpty();
      case ExpectedRevision.Exactly(var revision) ->
          current.filter(stored -> stored.revision() == revision).isPresent();
    };
  }

  private static ConnectionExtensionException failure(ConnectionExceptionCodes code) {
    return new ConnectionExtensionException(
        ExtensionFailure.CONFLICT, code.code(), code.description());
  }
}
