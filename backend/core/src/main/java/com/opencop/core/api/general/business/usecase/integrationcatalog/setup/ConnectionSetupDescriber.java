// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog.setup;

import com.opencop.core.api.general.business.model.integrationcatalog.ProtocolConnectionSetup;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import java.util.Optional;

/**
 * Describes what one transport accepts when a connection is set up over it, for a schema version
 * that declares the transport.
 *
 * <p>A describer reads the connection registries without checking them again: their construction is
 * the only line of defence, so a lookup that comes back empty where construction should have
 * refused to start is a server fault, reported through {@link ConnectionSetupFaults}.
 */
public interface ConnectionSetupDescriber {

  TransportProtocol protocol();

  /**
   * Describes the transport for the schema version, which declares it.
   *
   * @return the setup, or empty when the transport is declared for its mapping rule alone and so
   *     has no form to render
   */
  Optional<ProtocolConnectionSetup> describe(IntegrationConnectionSchemaReference schema);
}
