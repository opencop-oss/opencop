// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.setup;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.profile.MapperReference;
import java.util.Optional;
import java.util.UUID;

/**
 * Fully resolved, provider-neutral connection setup, carrying what the integration resolved from a
 * setup request into persistence.
 *
 * <p>This is the integration-resolved slice only. Source name, access mode, and source id stay on
 * the request path, because the source id is minted mid-flow so stored credentials can be scoped to
 * it.
 *
 * <p>There is no neutral connection material type: each variant exposes its own concrete material,
 * and consumers reach it through an exhaustive switch over this hierarchy.
 */
public sealed interface ResolvedConnectionSetup permits ResolvedTcpConnectionSetup {

  /** Returns the exact connection schema version that resolved this setup. */
  IntegrationConnectionSchemaReference integration();

  /** Returns the stable identifier of the extractor that produced the material. */
  UUID extractorId();

  /**
   * Returns the ingress mapper version this setup resolved with, empty for a connection that only
   * writes. It is provenance rather than lifetime runtime authority; see {@link MapperReference}.
   */
  Optional<MapperReference> ingressMapper();

  /** Returns the transport protocol this setup establishes, derived from the variant. */
  TransportProtocol protocol();
}
