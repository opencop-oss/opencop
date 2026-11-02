// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import com.opencop.core.sdk.ports.gateway.secrets.SecretReader;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionSecrets;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionSecretEntry;
import java.util.Map;
import java.util.Set;

/**
 * Lets an extension read the secret material its connection's setup references, which its own
 * integration stored, and refuses every other ref, so it can never read another connection's; and
 * reach its own secret entry on the connection.
 */
final class SetupScopedSecrets implements ConnectionSecrets {

  private final Set<String> referenced;
  private final SecretReader secrets;
  private final ExtensionSecretEntry entry;

  SetupScopedSecrets(Set<String> referenced, SecretReader secrets, ExtensionSecretEntry entry) {
    this.referenced = Set.copyOf(referenced);
    this.secrets = secrets;
    this.entry = entry;
  }

  @Override
  public Map<String, String> read(String ref) {
    if (!referenced.contains(ref)) {
      throw new IllegalArgumentException("The connection's setup does not reference this secret");
    }
    return secrets.read(ref);
  }

  @Override
  public ExtensionSecretEntry entry() {
    return entry;
  }
}
