// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.connection.extension;

import com.opencop.core.api.general.business.model.connection.extension.RecordScope;
import com.opencop.core.sdk.business.model.integration.connection.extension.ConnectionRecord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Stores the records extensions keep on connections, always within one scope. */
public interface ConnectionRecordGateway {

  Optional<ConnectionRecord> find(RecordScope scope, String collection, String key);

  /** Every record of the collection, in key order. */
  List<ConnectionRecord> list(RecordScope scope, String collection);

  /** Whether any extension holds a record on the connection. */
  boolean existsFor(UUID sourceId);

  /** How many records the scope holds, across all of its collections. */
  long count(RecordScope scope);

  /** The size of the document as the host serializes it to JSON, which caps are measured in. */
  long storedSize(Map<String, Object> document);

  /**
   * Creates the record, or replaces it, at the revision given. Joins the caller's transaction.
   *
   * @param userId who wrote it, or null for a write without a user
   */
  ConnectionRecord save(
      RecordScope scope,
      String collection,
      String key,
      Map<String, Object> document,
      long revision,
      UUID userId);

  /** Joins the caller's transaction. */
  void delete(RecordScope scope, String collection, String key);
}
