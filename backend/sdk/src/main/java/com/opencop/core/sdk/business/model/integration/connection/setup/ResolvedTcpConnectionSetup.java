// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.setup;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpConnectionMaterial;
import com.opencop.core.sdk.business.model.profile.MapperReference;
import com.opencop.core.sdk.model.datasource.AccessMode;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolved connection setup for a TCP integration.
 *
 * <p>Framing lives on this variant rather than on the base, so a setup for a protocol that does not
 * frame its stream cannot express one at all. It is required: a TCP connection always frames.
 *
 * <p>The access mode decides which mappers are present: a reading connection has an ingress mapper,
 * a writing one an egress mapper, and one that does both has both. A setup holding any other
 * combination cannot be built.
 *
 * @param integration exact connection schema version that resolved this setup
 * @param extractorId stable identifier of the extractor that produced the material
 * @param material resolved TCP endpoint and any credentials
 * @param mode what the connection does with its stream
 * @param ingressMapper ingress mapper version this setup resolved with, as provenance
 * @param egressMapper egress mapper version this setup resolved with, as provenance
 * @param framing how the connection splits its stream into messages
 */
public record ResolvedTcpConnectionSetup(
    IntegrationConnectionSchemaReference integration,
    UUID extractorId,
    TcpConnectionMaterial material,
    AccessMode mode,
    Optional<MapperReference> ingressMapper,
    Optional<MapperReference> egressMapper,
    ResolvedTcpFraming framing)
    implements ResolvedConnectionSetup {

  public ResolvedTcpConnectionSetup {
    Objects.requireNonNull(integration, "integration");
    Objects.requireNonNull(extractorId, "extractorId");
    Objects.requireNonNull(material, "material");
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(ingressMapper, "ingressMapper");
    Objects.requireNonNull(egressMapper, "egressMapper");
    Objects.requireNonNull(framing, "framing");
    if (ingressMapper.isPresent() != mode.canRead()) {
      throw new IllegalArgumentException(mode + " access disagrees with its ingress mapper");
    }
    if (egressMapper.isPresent() != mode.canWrite()) {
      throw new IllegalArgumentException(mode + " access disagrees with its egress mapper");
    }
  }

  @Override
  public TransportProtocol protocol() {
    return TransportProtocol.TCP;
  }
}
