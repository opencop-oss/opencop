// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.config;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Configuration for a single TCP connection.
 *
 * <p>Loaded from application-local.yml under tcp.connections[]. Supports configurable delimiters,
 * topics, and per-connection enable/disable.
 */
public class TcpConnectionConfig {

  private String id;
  private String name;
  private boolean enabled = true;
  private String host;
  private int port;
  private Framing framing;
  private KafkaConfig kafka;

  public UUID getId() {
    return UUID.fromString(id);
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public Framing getFraming() {
    return framing;
  }

  public void setFraming(Framing framing) {
    this.framing = framing;
  }

  public KafkaConfig getKafka() {
    return kafka;
  }

  public void setKafka(KafkaConfig kafka) {
    this.kafka = kafka;
  }

  /**
   * Framing configuration for message boundaries.
   *
   * <p>Mode options: - "binary": Length-prefixed framing (4-byte big-endian length prefix) -
   * "delimiterSplit": Delimiter-based framing, strip delimiter from message (e.g.,
   * newline-delimited) - "delimiterInclude": Delimiter-based framing, keep delimiter in message
   * (e.g., XML closing tag) - "passthrough": No framing, returns entire data (common for UDP)
   *
   * <p>The delimiter field is only used for delimiter-based modes.
   *
   * <p>Encoding: - Specifies character encoding for delimiter-based modes (e.g., "UTF-8", "ASCII")
   * - Required for delimiter detection: the delimiter string must be encoded to bytes for
   * comparison - Currently only UTF-8 is fully tested and supported - Non-UTF-8 encodings will
   * throw UnsupportedOperationException (TODO: add support if needed)
   */
  public static class Framing {

    private String mode =
        "delimiterInclude"; // Default: include delimiter (end delimiter like </ship>)
    private String delimiter;
    private String encoding =
        StandardCharsets.UTF_8.name(); // Default: UTF-8 (only tested encoding)

    public String getMode() {
      return mode;
    }

    public void setMode(String mode) {
      this.mode = mode;
    }

    public String getDelimiter() {
      return delimiter;
    }

    public void setDelimiter(String delimiter) {
      this.delimiter = delimiter;
    }

    public String getEncoding() {
      return encoding;
    }

    public void setEncoding(String encoding) {
      this.encoding = encoding;
    }
  }

  public static class KafkaConfig {

    private String topic;

    public String getTopic() {
      return topic;
    }

    public void setTopic(String topic) {
      this.topic = topic;
    }
  }
}
