// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.extension;

import com.opencop.core.sdk.business.model.integration.connection.extension.ConnectionRecord;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExpectedRevision;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The records an extension stores for the connection it is invoked on, by collection and key. The
 * host confines it to this connection and this extension: nothing here can name another.
 *
 * <p>Records hold what users create through the extension, never secrets. The host caps each
 * document's size and the number of records the extension keeps on the connection.
 *
 * <p>Collection and key names are non-blank and at most {@value #MAX_NAME_LENGTH} characters; a
 * name breaking that is a bug in the extension, refused with {@link IllegalArgumentException}. A
 * refused write's {@link ConnectionExtensionException#code()} tells why it was refused: {@value
 * #REVISION_CONFLICT} or {@value #LIMIT_EXCEEDED}.
 */
public interface ConnectionRecordStore {

  /** The longest collection or key name the host stores. */
  int MAX_NAME_LENGTH = 200;

  /** The code of a write refused because the record is not at the expected revision. */
  String REVISION_CONFLICT = "CONNECTION_RECORD_REVISION_CONFLICT";

  /** The code of a write refused because it would exceed the host's caps. */
  String LIMIT_EXCEEDED = "CONNECTION_RECORD_LIMIT";

  /**
   * @throws IllegalArgumentException when a name is blank or longer than {@value #MAX_NAME_LENGTH}
   */
  Optional<ConnectionRecord> get(String collection, String key);

  /**
   * Every record of the collection, in key order.
   *
   * @throws IllegalArgumentException when the name is blank or longer than {@value
   *     #MAX_NAME_LENGTH}
   */
  List<ConnectionRecord> list(String collection);

  /**
   * Creates or replaces a record, provided it is at the expected revision.
   *
   * @param document a JSON object, as plain values
   * @return the stored record, with its new revision
   * @throws ConnectionExtensionException with {@link ExtensionFailure#CONFLICT} and the code
   *     {@value #REVISION_CONFLICT} when the record is not at the expected revision, or {@value
   *     #LIMIT_EXCEEDED} when the write would exceed the host's caps
   * @throws IllegalArgumentException when a name is blank or longer than {@value #MAX_NAME_LENGTH}
   */
  ConnectionRecord put(
      String collection, String key, Map<String, Object> document, ExpectedRevision expected)
      throws ConnectionExtensionException;

  /**
   * Deletes a record, provided it is at the expected revision. Deleting a missing record with
   * {@link ExpectedRevision#any()} does nothing.
   *
   * @throws ConnectionExtensionException with {@link ExtensionFailure#CONFLICT} and the code
   *     {@value #REVISION_CONFLICT} when the record is not at the expected revision
   * @throws IllegalArgumentException when a name is blank or longer than {@value #MAX_NAME_LENGTH},
   *     or when expecting the record to be absent, which no delete can satisfy
   */
  void delete(String collection, String key, ExpectedRevision expected)
      throws ConnectionExtensionException;
}
