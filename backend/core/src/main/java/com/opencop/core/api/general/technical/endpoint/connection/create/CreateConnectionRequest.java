// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.create;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.opencop.core.api.general.business.policy.connection.ConnectionNamePolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.UUID;

/**
 * The v2 request to create one connection, discriminated by {@code kind}. Each kind carries only
 * the fields meaningful to it; any other field is rejected.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = CreateConnectionRequest.Tcp.class, name = "TCP"),
  @JsonSubTypes.Type(value = CreateConnectionRequest.Udp.class, name = "UDP"),
  @JsonSubTypes.Type(value = CreateConnectionRequest.Rtsp.class, name = "RTSP"),
  @JsonSubTypes.Type(value = CreateConnectionRequest.Kafka.class, name = "KAFKA")
})
@Schema(
    description = "Connection to create, discriminated by kind.",
    discriminatorProperty = "kind",
    oneOf = {
      CreateConnectionRequest.Tcp.class,
      CreateConnectionRequest.Udp.class,
      CreateConnectionRequest.Rtsp.class,
      CreateConnectionRequest.Kafka.class
    })
public sealed interface CreateConnectionRequest {

  String SETTINGS_DESCRIPTION =
      "Integration settings the connection overrides, keyed by section then field, as described by"
          + " the integration's connection settings resource. Omit a field to inherit its"
          + " deployment default; omit the object, send it empty, or send null to override nothing."
          + " A null field value is rejected.";

  String name();

  String access();

  IntegrationSelection integration();

  UUID extractorId();

  Map<String, String> values();

  Map<String, Object> settings();

  /**
   * A TCP connection to a remote endpoint.
   *
   * @param name the connection's name
   * @param access the access mode
   * @param integration the integration schema version the connection binds to
   * @param extractorId the extractor that reads the values and files
   * @param values the values supplied for the extractor's declared fields
   * @param mapperId the ingress mapper the connection decodes with; required to read, rejected for
   *     W
   * @param egressMapperId the egress mapper the connection writes with; required to write, rejected
   *     for R
   * @param framing how the connection splits its stream into messages
   * @param settings the integration settings the connection overrides
   */
  @Schema(name = "CreateTcpConnectionRequest")
  record Tcp(
      @Schema(maxLength = ConnectionNamePolicy.MAX_NAME_CODE_POINTS) String name,
      String access,
      IntegrationSelection integration,
      UUID extractorId,
      Map<String, String> values,
      UUID mapperId,
      UUID egressMapperId,
      Framing framing,
      @Schema(description = SETTINGS_DESCRIPTION) Map<String, Object> settings)
      implements CreateConnectionRequest {}

  /**
   * A UDP listener.
   *
   * @param name the connection's name
   * @param access the access mode; a UDP connection only reads, so R
   * @param integration the integration schema version the connection binds to
   * @param extractorId the extractor that reads the values
   * @param values the values supplied for the extractor's declared fields
   * @param mapperId the ingress mapper the connection decodes with
   * @param settings the integration settings the connection overrides
   */
  @Schema(name = "CreateUdpConnectionRequest")
  record Udp(
      @Schema(maxLength = ConnectionNamePolicy.MAX_NAME_CODE_POINTS) String name,
      String access,
      IntegrationSelection integration,
      UUID extractorId,
      Map<String, String> values,
      UUID mapperId,
      @Schema(description = SETTINGS_DESCRIPTION) Map<String, Object> settings)
      implements CreateConnectionRequest {}

  /**
   * An RTSP stream.
   *
   * @param name the connection's name
   * @param access the access mode; an RTSP connection only reads, so R
   * @param integration the integration schema version the connection binds to
   * @param extractorId the extractor that reads the values
   * @param values the values supplied for the extractor's declared fields
   * @param mapperId the ingress mapper the connection decodes with
   * @param settings the integration settings the connection overrides
   */
  @Schema(name = "CreateRtspConnectionRequest")
  record Rtsp(
      @Schema(maxLength = ConnectionNamePolicy.MAX_NAME_CODE_POINTS) String name,
      String access,
      IntegrationSelection integration,
      UUID extractorId,
      Map<String, String> values,
      UUID mapperId,
      @Schema(description = SETTINGS_DESCRIPTION) Map<String, Object> settings)
      implements CreateConnectionRequest {}

  /**
   * A Kafka producer whose topic and credentials the platform provisions.
   *
   * @param name the connection's name
   * @param access the access mode; a Kafka connection only reads, so R
   * @param integration the integration schema version the connection binds to
   * @param extractorId the extractor that reads the values
   * @param values the values supplied for the extractor's declared fields
   * @param mapperId the ingress mapper the connection decodes with
   * @param settings the integration settings the connection overrides
   */
  @Schema(name = "CreateKafkaConnectionRequest")
  record Kafka(
      @Schema(maxLength = ConnectionNamePolicy.MAX_NAME_CODE_POINTS) String name,
      String access,
      IntegrationSelection integration,
      UUID extractorId,
      Map<String, String> values,
      UUID mapperId,
      @Schema(description = SETTINGS_DESCRIPTION) Map<String, Object> settings)
      implements CreateConnectionRequest {}

  /** How a TCP connection splits its stream into messages, discriminated by {@code type}. */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Framing.Custom.class, name = "CUSTOM"),
    @JsonSubTypes.Type(value = Framing.Integration.class, name = "INTEGRATION")
  })
  @Schema(
      name = "TcpFraming",
      discriminatorProperty = "type",
      oneOf = {Framing.Custom.class, Framing.Integration.class})
  sealed interface Framing {

    /**
     * A delimiter the connection supplies itself, stored exactly as sent.
     *
     * @param mode one of delimiterInclude, delimiterSplit, passthrough or binary
     * @param delimiter the delimiter's real characters, at most 16 bytes; required by the delimiter
     *     modes and rejected by the others
     */
    @Schema(name = "CustomTcpFraming")
    record Custom(String mode, String delimiter) implements Framing {}

    /**
     * A framing the integration provides in code.
     *
     * @param framingId the framing's identifier
     */
    @Schema(name = "IntegrationTcpFraming")
    record Integration(UUID framingId) implements Framing {}
  }

  /**
   * The exact connection schema version a connection binds to.
   *
   * @param id the integration's identifier
   * @param schemaVersion the version of its connection schema
   */
  record IntegrationSelection(UUID id, Integer schemaVersion) {}
}
