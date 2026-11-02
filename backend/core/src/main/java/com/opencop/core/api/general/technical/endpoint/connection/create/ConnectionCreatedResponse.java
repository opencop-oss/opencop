// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.create;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.opencop.core.sdk.model.connection.ConnectionInfo;
import com.opencop.core.sdk.ports.gateway.kafka.KafkaCredentials;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The v2 answer to a connection creation, discriminated by {@code kind} like the request. Each kind
 * carries the stored connection and whatever its transport issued only once.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ConnectionCreatedResponse.Tcp.class, name = "TCP"),
  @JsonSubTypes.Type(value = ConnectionCreatedResponse.Udp.class, name = "UDP"),
  @JsonSubTypes.Type(value = ConnectionCreatedResponse.Rtsp.class, name = "RTSP"),
  @JsonSubTypes.Type(value = ConnectionCreatedResponse.Kafka.class, name = "KAFKA")
})
@Schema(
    description = "Created connection, discriminated by kind.",
    discriminatorProperty = "kind",
    oneOf = {
      ConnectionCreatedResponse.Tcp.class,
      ConnectionCreatedResponse.Udp.class,
      ConnectionCreatedResponse.Rtsp.class,
      ConnectionCreatedResponse.Kafka.class
    })
public sealed interface ConnectionCreatedResponse {

  /**
   * A created TCP connection.
   *
   * @param connectionInfo the connection as its owner now sees it, runtime state included
   */
  @Schema(name = "TcpConnectionCreatedResponse")
  record Tcp(@JsonUnwrapped ConnectionInfo connectionInfo) implements ConnectionCreatedResponse {}

  /**
   * A created UDP listener.
   *
   * @param connectionInfo the connection as its owner now sees it, runtime state included
   */
  @Schema(name = "UdpConnectionCreatedResponse")
  record Udp(@JsonUnwrapped ConnectionInfo connectionInfo) implements ConnectionCreatedResponse {}

  /**
   * A created RTSP stream.
   *
   * @param connectionInfo the connection as its owner now sees it, runtime state included
   */
  @Schema(name = "RtspConnectionCreatedResponse")
  record Rtsp(@JsonUnwrapped ConnectionInfo connectionInfo) implements ConnectionCreatedResponse {}

  /**
   * A created Kafka producer.
   *
   * @param connectionInfo the connection as its owner now sees it, runtime state included
   * @param kafkaCredentials the producer's credentials, returned only in this response
   */
  @Schema(name = "KafkaConnectionCreatedResponse")
  record Kafka(@JsonUnwrapped ConnectionInfo connectionInfo, KafkaCredentials kafkaCredentials)
      implements ConnectionCreatedResponse {}
}
