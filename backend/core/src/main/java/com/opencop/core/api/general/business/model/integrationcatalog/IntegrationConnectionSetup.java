// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * What one connection schema version accepts when a client sets up a connection to it, for each
 * protocol it can set a connection up over.
 *
 * <p>Carries a choice set, not a decision: a client picks an extractor, mappers and, for TCP, a
 * framing from what each protocol offers and submits them, and the submitted choice is checked
 * separately against mapper availability, archival state and access-mode eligibility.
 *
 * @param schema the connection schema version these choices belong to
 * @param protocols every protocol a connection can be set up over, at least one, in protocol order
 */
public record IntegrationConnectionSetup(
    IntegrationConnectionSchemaKey schema, List<ProtocolConnectionSetup> protocols) {

  /**
   * Orders the protocols by their declaration order rather than as supplied, so the response is the
   * same on every request and restart and can be cached.
   */
  public IntegrationConnectionSetup {
    Objects.requireNonNull(schema, "schema");
    protocols =
        Objects.requireNonNull(protocols, "protocols").stream()
            .sorted(Comparator.comparing(ProtocolConnectionSetup::protocol))
            .toList();
    if (protocols.isEmpty()) {
      throw new IllegalArgumentException("protocols must not be empty");
    }
  }
}
