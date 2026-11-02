// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration;

import java.util.Set;

/** One version of the connection contract an integration offers. A connection binds to one. */
public interface IntegrationConnectionSchema {

  Integration integration();

  int schemaVersion();

  /**
   * Returns the transport protocols this schema version supports for connection setup.
   *
   * <p>Registry construction checks this declaration against what actually registered: an extractor
   * may only register for a protocol declared here, and a declared TCP must have at least one
   * registered TCP extractor. Another protocol may be declared for its mapping rules alone. A
   * schema version that declares no protocol and registers no extractor is valid.
   */
  Set<TransportProtocol> protocols();

  default IntegrationConnectionSchemaReference reference() {
    return new IntegrationConnectionSchemaReference(integration().id(), schemaVersion());
  }
}
