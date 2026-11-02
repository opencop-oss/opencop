// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.extension;

import com.opencop.core.sdk.ports.gateway.secrets.SecretReader;
import java.util.Map;

/**
 * The secrets an extension may reach on a connection: the material the connection's setup
 * references, read only, and the extension's own entry. What they mean, and how to use them, is the
 * extension's own business.
 */
public interface ConnectionSecrets extends SecretReader {

  /**
   * @return the material's entries, or null when nothing is stored under the ref
   * @throws IllegalArgumentException when the connection's setup does not reference the ref
   */
  @Override
  Map<String, String> read(String ref);

  /** The extension's own secret entry on this connection. */
  ExtensionSecretEntry entry();
}
