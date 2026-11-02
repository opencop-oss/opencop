// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import com.opencop.core.sdk.business.model.integration.connection.extension.ConnectionRecord;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExpectedRevision;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionRecordStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A deleted connection's records: still readable, never written again. */
final class ReadOnlyRecordStore implements ConnectionRecordStore {

  private static final String READ_ONLY = "A deleted connection's records can no longer be written";

  private final ConnectionRecordStore records;

  ReadOnlyRecordStore(ConnectionRecordStore records) {
    this.records = records;
  }

  @Override
  public Optional<ConnectionRecord> get(String collection, String key) {
    return records.get(collection, key);
  }

  @Override
  public List<ConnectionRecord> list(String collection) {
    return records.list(collection);
  }

  @Override
  public ConnectionRecord put(
      String collection, String key, Map<String, Object> document, ExpectedRevision expected) {
    throw new IllegalStateException(READ_ONLY);
  }

  @Override
  public void delete(String collection, String key, ExpectedRevision expected) {
    throw new IllegalStateException(READ_ONLY);
  }
}
