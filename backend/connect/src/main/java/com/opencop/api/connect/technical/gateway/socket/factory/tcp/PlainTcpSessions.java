// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.factory.tcp;

import com.opencop.api.connect.technical.config.ConnectProperties;
import com.opencop.api.connect.technical.gateway.socket.Session;
import com.opencop.api.connect.technical.gateway.socket.SessionOpener;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opens plain TCP sessions for a connection without security. */
final class PlainTcpSessions {

  private static final Logger log = LoggerFactory.getLogger(PlainTcpSessions.class);

  private final ConnectProperties connectProperties;

  PlainTcpSessions(ConnectProperties connectProperties) {
    this.connectProperties = connectProperties;
  }

  SessionOpener opener(UUID sourceId, TcpConfig tcpConfig) {
    var socketConfig = connectProperties.getSocket();

    return () -> {
      Socket socket = new Socket();
      socket.connect(
          new InetSocketAddress(tcpConfig.host(), tcpConfig.port()),
          tcpConfig.connectTimeoutMs() != null ? tcpConfig.connectTimeoutMs() : 3000);
      // Per-connection config overrides global default
      int readTimeoutMs =
          tcpConfig.readTimeoutMs() != null
              ? tcpConfig.readTimeoutMs()
              : socketConfig.getReadTimeoutMs();
      socket.setSoTimeout(readTimeoutMs);
      socket.setKeepAlive(socketConfig.isTcpKeepalive());
      if (Boolean.TRUE.equals(tcpConfig.tcpNoDelay())) socket.setTcpNoDelay(true);
      log.info(
          "TCP session opened for source {} local={} remote={}",
          sourceId,
          socket.getLocalSocketAddress(),
          socket.getRemoteSocketAddress());
      InputStream input = new BufferedInputStream(socket.getInputStream());
      OutputStream output = new BufferedOutputStream(socket.getOutputStream());
      return new Session() {
        @Override
        public InputStream input() {
          return input;
        }

        @Override
        public OutputStream output() {
          return output;
        }

        @Override
        public void close() {
          try {
            socket.close();
            log.info(
                "TCP session closed for source {} local={} remote={}",
                sourceId,
                socket.getLocalSocketAddress(),
                socket.getRemoteSocketAddress());
          } catch (Exception e) {
            log.warn(
                "TCP session close failed for source {} local={} remote={}: {}",
                sourceId,
                socket.getLocalSocketAddress(),
                socket.getRemoteSocketAddress(),
                e.getMessage());
          }
        }
      };
    };
  }
}
