// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.extension;

import java.util.Map;

/**
 * The one secret entry the host assigns an extension on a connection, for secrets that are not part
 * of the connection's setup, such as a password a user gave once. The extension owns the shape of
 * its entries; the host chooses where the entry lives, so it can never reach another's.
 *
 * <p>Archiving the connection deletes the entry, once its delete hooks have run.
 */
public interface ExtensionSecretEntry {

  /** The entry's secrets, empty when nothing is stored. */
  Map<String, String> read();

  /**
   * Replaces the entry's secrets whole; an empty document deletes it.
   *
   * @throws IllegalStateException when the connection is deleted, and its entry only readable
   */
  void replace(Map<String, String> secrets);

  /**
   * Deletes the entry; deleting a missing one does nothing.
   *
   * @throws IllegalStateException when the connection is deleted, and its entry only readable
   */
  void delete();
}
