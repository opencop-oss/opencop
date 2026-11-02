// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.mapping;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.mapping.ConnectionMappingDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.FramingDefinition;
import java.util.Map;
import java.util.Set;

/** Integration-owned mapper/framing rules for its declared connection protocols. */
public interface ConnectionMappingContribution {
  /** Returns the connection schema version these rules belong to. */
  IntegrationConnectionSchema schema();

  Map<TransportProtocol, ? extends ConnectionMappingDeclaration> declarations();

  /** Framings this integration provides in code; its declarations may reference only these. */
  Set<FramingDefinition> framings();
}
