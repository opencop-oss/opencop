// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.model.datasource.AccessMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * What a connection schema version accepts over one transport: the extractors that read a
 * connection's input, and the rule for the mappers it reads, and for TCP writes, with.
 *
 * <p>Sealed by transport kind, like a connection creation: only a TCP stream is framed and only TCP
 * writes, so a framing or an egress rule on any other transport is unrepresentable rather than left
 * empty.
 */
public sealed interface ProtocolConnectionSetup {

  TransportProtocol protocol();

  /** Registered extractors in the registry's stable registration order, at least one. */
  List<ExtractorDescriptor> extractors();

  /**
   * The access modes a connection over this transport may take, at least one, in the order R, W,
   * RW: the same answer creating the connection enforces.
   */
  List<AccessMode> accessModes();

  /**
   * A TCP setup.
   *
   * @param rule which mapper/framing combinations, and which egress mappers, the integration
   *     permits
   * @param framings every framing the rule references, resolved, ascending by identifier
   */
  record Tcp(
      List<ExtractorDescriptor> extractors,
      MapperFramingDeclaration rule,
      List<ConnectionFraming> framings,
      List<AccessMode> accessModes)
      implements ProtocolConnectionSetup {

    public Tcp {
      extractors = requireExtractors(extractors);
      accessModes = requireAccessModes(accessModes);
      Objects.requireNonNull(rule, "rule");
      framings =
          Objects.requireNonNull(framings, "framings").stream()
              .sorted(
                  Comparator.comparing(ConnectionFraming::id, CanonicalUuidOrder.BY_CANONICAL_TEXT))
              .toList();
    }

    @Override
    public TransportProtocol protocol() {
      return TransportProtocol.TCP;
    }
  }

  /**
   * A UDP setup.
   *
   * @param rule which mappers the integration permits
   */
  record Udp(
      List<ExtractorDescriptor> extractors, MapperDeclaration rule, List<AccessMode> accessModes)
      implements ProtocolConnectionSetup {

    public Udp {
      extractors = requireExtractors(extractors);
      accessModes = requireAccessModes(accessModes);
      Objects.requireNonNull(rule, "rule");
    }

    @Override
    public TransportProtocol protocol() {
      return TransportProtocol.UDP;
    }
  }

  /**
   * An RTSP setup.
   *
   * @param rule which mappers the integration permits
   */
  record Rtsp(
      List<ExtractorDescriptor> extractors, MapperDeclaration rule, List<AccessMode> accessModes)
      implements ProtocolConnectionSetup {

    public Rtsp {
      extractors = requireExtractors(extractors);
      accessModes = requireAccessModes(accessModes);
      Objects.requireNonNull(rule, "rule");
    }

    @Override
    public TransportProtocol protocol() {
      return TransportProtocol.RTSP;
    }
  }

  /**
   * A Kafka setup.
   *
   * @param rule which mappers the integration permits
   */
  record Kafka(
      List<ExtractorDescriptor> extractors, MapperDeclaration rule, List<AccessMode> accessModes)
      implements ProtocolConnectionSetup {

    public Kafka {
      extractors = requireExtractors(extractors);
      accessModes = requireAccessModes(accessModes);
      Objects.requireNonNull(rule, "rule");
    }

    @Override
    public TransportProtocol protocol() {
      return TransportProtocol.KAFKA;
    }
  }

  private static List<AccessMode> requireAccessModes(List<AccessMode> accessModes) {
    var copy = List.copyOf(Objects.requireNonNull(accessModes, "accessModes"));
    if (copy.isEmpty()) {
      throw new IllegalArgumentException("accessModes must not be empty");
    }
    return copy;
  }

  private static List<ExtractorDescriptor> requireExtractors(List<ExtractorDescriptor> extractors) {
    var copy = List.copyOf(Objects.requireNonNull(extractors, "extractors"));
    if (copy.isEmpty()) {
      throw new IllegalArgumentException("extractors must not be empty");
    }
    return copy;
  }
}
