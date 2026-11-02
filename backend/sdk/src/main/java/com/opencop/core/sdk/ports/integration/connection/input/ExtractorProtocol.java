// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.input;

import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.TcpConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.material.kafka.KafkaConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.rtsp.RtspConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.udp.UdpConnectionMaterial;
import java.util.Objects;

/**
 * What an extractor of one transport protocol is: the input it reads, the material it produces, the
 * capability it implements, and whether a schema declaring the protocol needs one at all.
 *
 * <p>A lookup keyed by one of these constants returns extractors typed by their protocol, so a
 * caller never casts. Each protocol has its own capability interface, which fixes the input and
 * material types; the registry requires it of every extractor of the protocol, so the material type
 * follows from the capability.
 *
 * @param <I> the input view the protocol's extractors read
 * @param <M> the connection material the protocol's extractors produce
 */
public final class ExtractorProtocol<I extends ConnectionInput, M> {

  /**
   * A TCP connection that selects an integration reads its input only through that integration's
   * extractors, which may also be selected by the shape of the input.
   */
  public static final ExtractorProtocol<TcpConnectionInput, TcpConnectionMaterial> TCP =
      new ExtractorProtocol<>(TransportProtocol.TCP, TcpConnectionExtractorCapability.class, true);

  public static final ExtractorProtocol<ConnectionInput, UdpConnectionMaterial> UDP =
      new ExtractorProtocol<>(TransportProtocol.UDP, UdpConnectionExtractorCapability.class, false);

  public static final ExtractorProtocol<ConnectionInput, RtspConnectionMaterial> RTSP =
      new ExtractorProtocol<>(
          TransportProtocol.RTSP, RtspConnectionExtractorCapability.class, false);

  public static final ExtractorProtocol<ConnectionInput, KafkaConnectionMaterial> KAFKA =
      new ExtractorProtocol<>(
          TransportProtocol.KAFKA, KafkaConnectionExtractorCapability.class, false);

  private final TransportProtocol protocol;
  private final Class<?> capability;
  private final boolean requiresExtractor;

  private ExtractorProtocol(
      TransportProtocol protocol, Class<?> capability, boolean requiresExtractor) {
    this.protocol = protocol;
    this.capability = capability;
    this.requiresExtractor = requiresExtractor;
  }

  /** Returns the key of the protocol. */
  public static ExtractorProtocol<?, ?> of(TransportProtocol protocol) {
    Objects.requireNonNull(protocol, "protocol");
    return switch (protocol) {
      case TCP -> TCP;
      case UDP -> UDP;
      case RTSP -> RTSP;
      case KAFKA -> KAFKA;
    };
  }

  public TransportProtocol protocol() {
    return protocol;
  }

  /** Returns the capability interface every extractor of the protocol implements. */
  public Class<?> capability() {
    return capability;
  }

  /**
   * Whether a schema declaring the protocol must register an extractor for it. Where the host still
   * reads the protocol's input itself, a schema may declare it for its mapping rules alone.
   */
  public boolean requiresExtractor() {
    return requiresExtractor;
  }

  @Override
  public String toString() {
    return protocol.name();
  }
}
