// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.patch;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.ConnectionMetadataPatch;
import com.opencop.core.api.general.business.model.connection.ValidatedConnectionMetadataPatch;
import com.opencop.core.api.general.business.policy.connection.TcpMapperFramingPolicy;
import com.opencop.core.api.general.business.policy.connection.access.AllowedAccessModesPolicy;
import com.opencop.core.api.general.business.policy.connection.access.ConnectionAccessChangePolicy;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpFraming;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TcpConnectionPatch implements ConnectionProtocolPatch {
  private static final Logger log = LoggerFactory.getLogger(TcpConnectionPatch.class);

  private final DataSourceRepository dataSources;
  private final ConnectionMappingRegistry mappingRegistry;

  public TcpConnectionPatch(
      DataSourceRepository dataSources, ConnectionMappingRegistry mappingRegistry) {
    this.dataSources = dataSources;
    this.mappingRegistry = mappingRegistry;
  }

  @Override
  public TransportKind transportKind() {
    return TransportKind.TCP;
  }

  @Override
  public boolean validateAndRequiresReconciliation(
      ConnectionMetadata current,
      ConnectionMetadataPatch requested,
      ValidatedConnectionMetadataPatch effective) {
    if (effective.access() != null) {
      AllowedAccessModesPolicy.assertTransportAllows(TransportKind.TCP, effective.access());
      ConnectionAccessChangePolicy.assertSupported(current.access(), effective.access());
    }
    // The declared mapper and framing pairs concern decoding, which a write-only connection skips.
    if (current.integration() != null && current.ingressMapperId() != null) {
      var reason =
          TcpMapperFramingPolicy.existingMismatch(
              mappingRegistry.findTcp(current.integration()),
              current.ingressMapperId(),
              storedFraming(current));
      if (reason != null) {
        log.warn(
            "CONNECTION_MAPPING_MISMATCH reason={} connectionId={}", reason.code(), current.id());
      }
    }
    return current.enabled() && (effective.access() != null || effective.integration() != null);
  }

  private TcpFraming storedFraming(ConnectionMetadata current) {
    return dataSources
        .findById(current.id())
        .map(
            source ->
                source instanceof SocketDataSource socket
                        && socket.spec() != null
                        && socket.spec().transport() instanceof TcpConfig tcp
                    ? tcp.framing()
                    : null)
        .orElse(null);
  }
}
