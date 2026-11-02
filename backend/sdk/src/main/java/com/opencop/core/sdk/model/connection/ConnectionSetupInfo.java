// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import java.util.Map;
import java.util.UUID;

/**
 * A connection's setup in the shape a create request takes, so a client fills the same form to
 * change it, discriminated by {@code kind}. Each kind carries only the fields meaningful to it.
 * Secret and file inputs are never returned; they are always supplied again.
 *
 * <p>The extractor is null and the values empty when the connection's setup was never captured, as
 * for a connection created through v1 or before it was recorded.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ConnectionSetupInfo.TcpConnectionSetupInfo.class, name = "TCP"),
  @JsonSubTypes.Type(value = ConnectionSetupInfo.UdpConnectionSetupInfo.class, name = "UDP"),
  @JsonSubTypes.Type(value = ConnectionSetupInfo.RtspConnectionSetupInfo.class, name = "RTSP"),
  @JsonSubTypes.Type(value = ConnectionSetupInfo.KafkaConnectionSetupInfo.class, name = "KAFKA")
})
public sealed interface ConnectionSetupInfo {

  /** The transport the connection uses. */
  TransportKind kind();

  /** The integration schema version the connection binds to. */
  SetupIntegration integration();

  /** The extractor the last submitted setup chose. */
  UUID extractorId();

  /** The text values the last submitted setup supplied for that extractor's fields. */
  Map<String, String> values();

  /**
   * A TCP connection's setup.
   *
   * @param mapperId the ingress mapper the connection reads with, or null when it only writes
   * @param egressMapperId the egress mapper the connection writes with, or null when it only reads
   * @param framing how the connection splits its stream, or null when it has none
   */
  record TcpConnectionSetupInfo(
      SetupIntegration integration,
      UUID extractorId,
      Map<String, String> values,
      UUID mapperId,
      UUID egressMapperId,
      SetupFraming framing)
      implements ConnectionSetupInfo {

    public TcpConnectionSetupInfo {
      values = values == null ? Map.of() : Map.copyOf(values);
    }

    @Override
    public TransportKind kind() {
      return TransportKind.TCP;
    }
  }

  /**
   * A UDP listener's setup.
   *
   * @param mapperId the ingress mapper the connection reads with
   */
  record UdpConnectionSetupInfo(
      SetupIntegration integration, UUID extractorId, Map<String, String> values, UUID mapperId)
      implements ConnectionSetupInfo {

    public UdpConnectionSetupInfo {
      values = values == null ? Map.of() : Map.copyOf(values);
    }

    @Override
    public TransportKind kind() {
      return TransportKind.UDP;
    }
  }

  /**
   * An RTSP stream's setup.
   *
   * @param mapperId the ingress mapper the connection reads with
   */
  record RtspConnectionSetupInfo(
      SetupIntegration integration, UUID extractorId, Map<String, String> values, UUID mapperId)
      implements ConnectionSetupInfo {

    public RtspConnectionSetupInfo {
      values = values == null ? Map.of() : Map.copyOf(values);
    }

    @Override
    public TransportKind kind() {
      return TransportKind.RTSP;
    }
  }

  /**
   * A Kafka producer's setup.
   *
   * @param mapperId the ingress mapper the connection reads with
   */
  record KafkaConnectionSetupInfo(
      SetupIntegration integration, UUID extractorId, Map<String, String> values, UUID mapperId)
      implements ConnectionSetupInfo {

    public KafkaConnectionSetupInfo {
      values = values == null ? Map.of() : Map.copyOf(values);
    }

    @Override
    public TransportKind kind() {
      return TransportKind.KAFKA;
    }
  }

  /** An integration schema version, as a create request names it. */
  record SetupIntegration(UUID id, int schemaVersion) {}

  /** How a TCP connection splits its stream, as a create request selects it. */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = SetupFraming.CustomSetupFraming.class, name = "CUSTOM"),
    @JsonSubTypes.Type(value = SetupFraming.IntegrationSetupFraming.class, name = "INTEGRATION")
  })
  sealed interface SetupFraming {

    /**
     * A delimiter the connection supplies itself.
     *
     * @param mode one of delimiterInclude, delimiterSplit, passthrough or binary
     * @param delimiter the delimiter's characters, or null for a mode that takes none
     */
    record CustomSetupFraming(String mode, String delimiter) implements SetupFraming {}

    /** A framing the integration provides in code. */
    record IntegrationSetupFraming(UUID framingId) implements SetupFraming {}
  }
}
