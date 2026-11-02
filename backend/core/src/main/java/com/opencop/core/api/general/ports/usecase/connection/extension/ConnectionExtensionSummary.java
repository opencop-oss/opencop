// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.extension;

import java.util.List;

/**
 * One extension of a connection, as a client offers it.
 *
 * @param id the identifier the extension is served under
 * @param operations the operations the caller may invoke, in the order the extension declares them
 */
public record ConnectionExtensionSummary(
    String id, String label, String description, List<ExtensionOperationSummary> operations) {

  public ConnectionExtensionSummary {
    operations = List.copyOf(operations);
  }
}
