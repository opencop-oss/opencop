// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.extension;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A record an extension stores for one connection.
 *
 * @param document the record's JSON object, as plain values: maps, lists, strings, numbers,
 *     booleans and nulls
 * @param revision 1 when created, one more on every write
 * @param createdBy the user whose request created it, or null for a write without a user
 * @param updatedBy the user whose request last wrote it, or null for a write without a user
 */
public record ConnectionRecord(
    String collection,
    String key,
    Map<String, Object> document,
    long revision,
    Instant createdAt,
    UUID createdBy,
    Instant updatedAt,
    UUID updatedBy) {

  public ConnectionRecord {
    Objects.requireNonNull(collection, "collection");
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(document, "document");
  }
}
