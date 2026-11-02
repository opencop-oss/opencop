// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.create;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.UUID;

/**
 * The documented body of a setup replacement: a {@link CreateConnectionRequest} without its {@code
 * name}, for the kinds whose setup can be replaced. It only describes the contract; the body is
 * read as a creation request by {@link CreateConnectionRequestReader#readSetup(String)}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ConnectionSetupRequest.Tcp.class, name = "TCP"),
  @JsonSubTypes.Type(value = ConnectionSetupRequest.Udp.class, name = "UDP"),
  @JsonSubTypes.Type(value = ConnectionSetupRequest.Rtsp.class, name = "RTSP")
})
@Schema(
    description =
        "Setup replacing a connection's, discriminated by kind; a Kafka connection's setup cannot"
            + " be replaced.",
    discriminatorProperty = "kind",
    oneOf = {
      ConnectionSetupRequest.Tcp.class,
      ConnectionSetupRequest.Udp.class,
      ConnectionSetupRequest.Rtsp.class
    })
public sealed interface ConnectionSetupRequest {

  /** The setup of a TCP connection, as {@link CreateConnectionRequest.Tcp} without its name. */
  @Schema(name = "TcpConnectionSetupRequest")
  record Tcp(
      String access,
      CreateConnectionRequest.IntegrationSelection integration,
      UUID extractorId,
      Map<String, String> values,
      UUID mapperId,
      UUID egressMapperId,
      CreateConnectionRequest.Framing framing,
      @Schema(description = CreateConnectionRequest.SETTINGS_DESCRIPTION)
          Map<String, Object> settings)
      implements ConnectionSetupRequest {}

  /** The setup of a UDP listener, as {@link CreateConnectionRequest.Udp} without its name. */
  @Schema(name = "UdpConnectionSetupRequest")
  record Udp(
      String access,
      CreateConnectionRequest.IntegrationSelection integration,
      UUID extractorId,
      Map<String, String> values,
      UUID mapperId,
      @Schema(description = CreateConnectionRequest.SETTINGS_DESCRIPTION)
          Map<String, Object> settings)
      implements ConnectionSetupRequest {}

  /** The setup of an RTSP stream, as {@link CreateConnectionRequest.Rtsp} without its name. */
  @Schema(name = "RtspConnectionSetupRequest")
  record Rtsp(
      String access,
      CreateConnectionRequest.IntegrationSelection integration,
      UUID extractorId,
      Map<String, String> values,
      UUID mapperId,
      @Schema(description = CreateConnectionRequest.SETTINGS_DESCRIPTION)
          Map<String, Object> settings)
      implements ConnectionSetupRequest {}
}
