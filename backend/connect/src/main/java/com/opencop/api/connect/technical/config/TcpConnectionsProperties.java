// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Spring configuration properties for TCP connections.
 *
 * <p>Binds to application-local.yml section:
 *
 * <pre>
 * tcp:
 *   connections:
 *     - id: "uuid"
 *       name: "Connection Name"
 *       enabled: true
 *       host: "localhost"
 *       port: 9999
 *       framing:
 *          mode: "binary" | "delimiterInclude" | "delimiterSplit"
 *          delimiter: "\n" # or e.g. "</ship>", not applicable for "mode": "binary"
 *          encoding: "UTF-8"  # Character encoding (default: UTF-8, only UTF-8 tested/supported)
 *       kafka:
 *         topic: "cot-wip-events"
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "tcp")
public class TcpConnectionsProperties {

  private List<TcpConnectionConfig> connections = new ArrayList<>();

  public List<TcpConnectionConfig> getConnections() {
    return connections;
  }

  public void setConnections(List<TcpConnectionConfig> connections) {
    this.connections = connections;
  }
}
