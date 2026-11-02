// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog.setup;

import com.opencop.core.api.general.business.model.integrationcatalog.ProtocolConnectionSetup;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Every transport's describer, indexed by transport.
 *
 * <p>Construction refuses to start unless each transport has exactly one describer. This is the
 * only line of defence: {@link #describe} looks a declared transport up without a fallback, so a
 * missing describer would otherwise surface as a server fault on a request.
 */
public class ConnectionSetupDescribers {

  private final Map<TransportProtocol, ConnectionSetupDescriber> byProtocol;

  public ConnectionSetupDescribers(List<ConnectionSetupDescriber> describers) {
    Objects.requireNonNull(describers, "describers");
    var indexed = new EnumMap<TransportProtocol, ConnectionSetupDescriber>(TransportProtocol.class);
    for (var describer : describers) {
      var protocol = Objects.requireNonNull(describer.protocol(), "describer.protocol");
      if (indexed.putIfAbsent(protocol, describer) != null) {
        throw new IllegalArgumentException("Duplicate connection setup describer for " + protocol);
      }
    }
    var missing = EnumSet.allOf(TransportProtocol.class);
    missing.removeAll(indexed.keySet());
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("Missing connection setup describer for " + missing);
    }
    byProtocol = indexed;
  }

  /**
   * Describes every transport the schema declares that a connection can be set up over, in
   * transport order.
   */
  public List<ProtocolConnectionSetup> describe(IntegrationConnectionSchema schema) {
    Objects.requireNonNull(schema, "schema");
    var declared =
        schema.protocols() == null ? EnumSet.noneOf(TransportProtocol.class) : schema.protocols();
    var reference = schema.reference();
    return declared.stream()
        .sorted()
        .map(protocol -> byProtocol.get(protocol).describe(reference))
        .flatMap(Optional::stream)
        .toList();
  }
}
