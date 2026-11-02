// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.input;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import java.util.UUID;

/**
 * Host-owned capability contract an integration implements to accept one way of supplying a
 * connection's input for one transport protocol, and to turn that input into the protocol's
 * connection material.
 *
 * @param <I> the input view the extractor reads
 * @param <M> the connection material the extractor produces for its protocol
 */
public interface ConnectionExtractorCapability<I extends ConnectionInput, M> {

  /** Returns the connection schema version this extractor belongs to. */
  IntegrationConnectionSchema schema();

  /**
   * Returns the transport protocol this extractor produces material for. The schema must declare
   * it, which the registry enforces.
   */
  TransportProtocol protocol();

  /**
   * Returns the stable extractor identifier.
   *
   * <p>A contract value, not a display string: a client may receive it and send it back to select
   * this extractor, so changing it is a breaking change. Opaque by design, so the display text an
   * operator reads can be reworded without invalidating a stored selection. Unique within a
   * connection schema version across all its protocols, which the registry enforces.
   */
  UUID id();

  /**
   * Returns the fields this extractor consumes, so a client can render a setup form for it without
   * hardcoded knowledge of the integration.
   *
   * <p>Purely declarative: the answer is a property of the extractor, not of any request. It must
   * be resolvable without reading request state, opening a connection, or performing I/O of any
   * kind, because callers resolve it to describe an integration that is not being connected to.
   *
   * <p>The descriptor states what is wanted, never whether a supplied value is acceptable. {@link
   * #validate} remains the sole authority on that.
   */
  ExtractorDescriptor descriptor();

  /** Validates provider-specific input without performing connection-creation side effects. */
  void validate(I input) throws ConnectionInputException;

  /** Extracts the complete connection material from a previously validated request. */
  M extract(I input) throws ConnectionInputException;
}
