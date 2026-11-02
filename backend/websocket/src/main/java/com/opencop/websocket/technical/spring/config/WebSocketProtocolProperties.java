// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.config;

import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "websocket")
public final class WebSocketProtocolProperties implements InitializingBean {

  static final String MAX_INBOUND_MESSAGE_SIZE_PROPERTY = "websocket.max-inbound-message-size";
  static final String PROTOCOL_VIOLATION_LIMIT_PROPERTY = "websocket.protocol-violation-limit";
  static final String PROTOCOL_VIOLATION_WINDOW_PROPERTY = "websocket.protocol-violation-window";
  private static final Pattern EXPLICIT_DATA_SIZE = Pattern.compile("-?\\d+[a-zA-Z]{1,2}");
  private static final Pattern BARE_NUMBER = Pattern.compile("[+-]?\\d+");
  private static final long MINIMUM_MESSAGE_BYTES = 1_024;
  private static final long MAXIMUM_MESSAGE_BYTES = Integer.MAX_VALUE;
  private static final int MINIMUM_VIOLATION_LIMIT = 1;
  private static final int MAXIMUM_VIOLATION_LIMIT = 10_000;
  private static final Duration MINIMUM_VIOLATION_WINDOW = Duration.ofSeconds(1);
  private static final Duration MAXIMUM_VIOLATION_WINDOW = Duration.ofHours(1);

  private DataSize maxInboundMessageSize = DataSize.ofKilobytes(64);
  private int protocolViolationLimit = 10;
  private Duration protocolViolationWindow = Duration.ofSeconds(60);
  private boolean initialized;

  public DataSize getMaxInboundMessageSize() {
    return maxInboundMessageSize;
  }

  public void setMaxInboundMessageSize(String configuredValue) {
    requireMutable();
    if (configuredValue == null || !EXPLICIT_DATA_SIZE.matcher(configuredValue).matches()) {
      throw new IllegalArgumentException(
          MAX_INBOUND_MESSAGE_SIZE_PROPERTY + " must include an explicit data-size unit");
    }
    this.maxInboundMessageSize = DataSize.parse(configuredValue);
  }

  public int getProtocolViolationLimit() {
    return protocolViolationLimit;
  }

  public void setProtocolViolationLimit(int protocolViolationLimit) {
    requireMutable();
    this.protocolViolationLimit = protocolViolationLimit;
  }

  public Duration getProtocolViolationWindow() {
    return protocolViolationWindow;
  }

  public void setProtocolViolationWindow(String configuredValue) {
    requireMutable();
    if (configuredValue == null || BARE_NUMBER.matcher(configuredValue).matches()) {
      throw new IllegalArgumentException(
          PROTOCOL_VIOLATION_WINDOW_PROPERTY + " must include an explicit duration unit");
    }
    this.protocolViolationWindow = DurationStyle.detectAndParse(configuredValue);
  }

  @Override
  public void afterPropertiesSet() {
    long bytes = maxInboundMessageSize.toBytes();
    if (bytes < MINIMUM_MESSAGE_BYTES || bytes > MAXIMUM_MESSAGE_BYTES) {
      throw new IllegalArgumentException(
          MAX_INBOUND_MESSAGE_SIZE_PROPERTY + " must be between 1KB and 2147483647B inclusive");
    }
    if (protocolViolationLimit < MINIMUM_VIOLATION_LIMIT
        || protocolViolationLimit > MAXIMUM_VIOLATION_LIMIT) {
      throw new IllegalArgumentException(
          PROTOCOL_VIOLATION_LIMIT_PROPERTY + " must be between 1 and 10000 inclusive");
    }
    if (protocolViolationWindow.compareTo(MINIMUM_VIOLATION_WINDOW) < 0
        || protocolViolationWindow.compareTo(MAXIMUM_VIOLATION_WINDOW) > 0) {
      throw new IllegalArgumentException(
          PROTOCOL_VIOLATION_WINDOW_PROPERTY + " must be between 1s and 1h inclusive");
    }
    initialized = true;
  }

  public int maxInboundMessageBytes() {
    return Math.toIntExact(maxInboundMessageSize.toBytes());
  }

  private void requireMutable() {
    if (initialized) {
      throw new IllegalStateException("WebSocket protocol properties are immutable after startup");
    }
  }
}
