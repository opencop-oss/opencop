// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.config;

import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.technical.extractor.kafka.NativeKafkaProducerExtractor;
import com.opencop.core.sdk.integration.builtin.technical.extractor.rtsp.NativeRtspStreamExtractor;
import com.opencop.core.sdk.integration.builtin.technical.extractor.tcp.NativeMtlsTcpExtractor;
import com.opencop.core.sdk.integration.builtin.technical.extractor.tcp.NativePlainTcpExtractor;
import com.opencop.core.sdk.integration.builtin.technical.extractor.udp.NativeUdpListenerExtractor;
import com.opencop.core.sdk.ports.integration.connection.input.KafkaConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.input.RtspConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.input.TcpConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.input.UdpConnectionExtractorCapability;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Native's connection extractors, one per way its input may be supplied.
 *
 * <p>Every bean lives on this class because the capability import index lists it by name; a
 * separate configuration class would need adding to that index or it would never be imported.
 */
@Configuration(proxyBeanMethods = false)
public class NativeConnectionInputConfiguration {

  @Bean
  TcpConnectionExtractorCapability nativePlainTcpExtractor(NativeConnectionSchema schema) {
    return new NativePlainTcpExtractor(schema);
  }

  @Bean
  TcpConnectionExtractorCapability nativeMtlsTcpExtractor(NativeConnectionSchema schema) {
    return new NativeMtlsTcpExtractor(schema);
  }

  @Bean
  UdpConnectionExtractorCapability nativeUdpListenerExtractor(NativeConnectionSchema schema) {
    return new NativeUdpListenerExtractor(schema);
  }

  @Bean
  RtspConnectionExtractorCapability nativeRtspStreamExtractor(NativeConnectionSchema schema) {
    return new NativeRtspStreamExtractor(schema);
  }

  @Bean
  KafkaConnectionExtractorCapability nativeKafkaProducerExtractor(NativeConnectionSchema schema) {
    return new NativeKafkaProducerExtractor(schema);
  }
}
