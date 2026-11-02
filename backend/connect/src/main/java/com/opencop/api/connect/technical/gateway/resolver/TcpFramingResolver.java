// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.resolver;

import com.opencop.api.connect.ports.gateway.socket.framing.FramingStrategy;
import com.opencop.api.connect.technical.config.ConnectProperties;
import com.opencop.api.connect.technical.gateway.socket.framing.DelimiterFramingStrategy;
import com.opencop.api.connect.technical.gateway.socket.framing.LengthPrefixedFramingStrategy;
import com.opencop.api.connect.technical.gateway.socket.framing.PassThroughFramingStrategy;
import com.opencop.core.sdk.business.model.profile.DelimiterModes;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.model.connection.spec.transport.CustomTcpFraming;
import com.opencop.core.sdk.model.connection.spec.transport.IntegrationTcpFraming;
import com.opencop.core.sdk.model.connection.spec.transport.TcpFraming;
import java.util.UUID;

/** Builds the framing a TCP connector splits its inbound byte stream with. */
public final class TcpFramingResolver {

  private final ConnectionMappingRegistry mappingRegistry;
  private final int maxMessageSizeBytes;

  public TcpFramingResolver(
      final ConnectionMappingRegistry mappingRegistry,
      final ConnectProperties.Socket socketConfig) {
    this.mappingRegistry = mappingRegistry;
    this.maxMessageSizeBytes = socketConfig.getMaxMessageSizeBytes();
  }

  public FramingStrategy get(UUID sourceId, TcpFraming framing) {
    return switch (framing) {
      case IntegrationTcpFraming integration ->
          mappingRegistry
              .findFraming(integration.framingId())
              .map(definition -> strategy(definition.mode(), definition.delimiter()))
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "No integration provides framing "
                              + integration.framingId()
                              + " for source "
                              + sourceId));
      case CustomTcpFraming custom -> strategy(custom.mode(), custom.delimiter());
      case null ->
          throw new IllegalArgumentException("No framing in the TCP spec of source " + sourceId);
    };
  }

  private FramingStrategy strategy(DelimiterModes mode, String delimiter) {
    return switch (mode) {
      case DELIMITER_INCLUDE -> new DelimiterFramingStrategy(delimiter, true, maxMessageSizeBytes);
      case DELIMITER_SPLIT -> new DelimiterFramingStrategy(delimiter, false, maxMessageSizeBytes);
      case BINARY -> new LengthPrefixedFramingStrategy(maxMessageSizeBytes);
      case PASSTHROUGH -> new PassThroughFramingStrategy();
    };
  }
}
