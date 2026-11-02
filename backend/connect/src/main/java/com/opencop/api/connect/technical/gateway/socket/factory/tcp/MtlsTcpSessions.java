// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.factory.tcp;

import static com.opencop.api.connect.ports.gateway.socket.connector.SocketConnectorExceptionCodes.TLS_EXTRACTION_FAILED;

import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnectorException;
import com.opencop.api.connect.technical.config.ConnectProperties;
import com.opencop.api.connect.technical.gateway.socket.Session;
import com.opencop.api.connect.technical.gateway.socket.SessionOpener;
import com.opencop.core.sdk.model.connection.spec.security.MtlsConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import com.opencop.core.sdk.technical.gateway.secrets.MtlsMaterialException;
import com.opencop.core.sdk.technical.gateway.secrets.MtlsSslContextFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** Opens mTLS TCP sessions from the credentials a connection's security references. */
final class MtlsTcpSessions {

  private static final Logger log = LoggerFactory.getLogger(MtlsTcpSessions.class);

  private final MtlsSslContextFactory sslContexts;
  private final ConnectProperties connectProperties;

  MtlsTcpSessions(ConnectionStore connectionStore, ConnectProperties connectProperties) {
    this.sslContexts = new MtlsSslContextFactory(connectionStore);
    this.connectProperties = connectProperties;
  }

  SessionOpener opener(UUID sourceId, TcpConfig tcp, MtlsConfig mtls) {
    return () -> {
      SSLSocket socket = getSslSocket(tcp, mtls);

      // TBD if we keep
      if (mtls.hostnameVerification()) {
        SSLSession session = socket.getSession();
        if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(tcp.host(), session)) {
          try {
            socket.close();
          } catch (Exception _) {
          }
          throw new SSLHandshakeException("Hostname verification failed for " + tcp.host());
        }
      }

      InputStream in = new BufferedInputStream(socket.getInputStream());
      OutputStream out = new BufferedOutputStream(socket.getOutputStream());
      log.info(
          "mTLS TCP session opened for source {} local={} remote={}",
          sourceId,
          socket.getLocalSocketAddress(),
          socket.getRemoteSocketAddress());

      return new Session() {
        @Override
        public InputStream input() {
          return in;
        }

        @Override
        public OutputStream output() {
          return out;
        }

        @Override
        public void close() {
          try {
            socket.close();
            log.info(
                "mTLS TCP session closed for source {} local={} remote={}",
                sourceId,
                socket.getLocalSocketAddress(),
                socket.getRemoteSocketAddress());
          } catch (Exception e) {
            log.warn(
                "mTLS TCP session close failed for source {} local={} remote={}: {}",
                sourceId,
                socket.getLocalSocketAddress(),
                socket.getRemoteSocketAddress(),
                e.getMessage());
          }
        }
      };
    };
  }

  private SSLSocket getSslSocket(TcpConfig tcp, MtlsConfig mtls) throws IOException {
    var socketConfig = connectProperties.getSocket();

    SSLSocket socket = (SSLSocket) createTlsSocketFromZip(mtls).createSocket();

    // IMPORTANT: set SNI + hostname verification
    SSLParameters params = socket.getSSLParameters();
    params.setServerNames(List.of(new SNIHostName(tcp.host())));
    socket.setSSLParameters(params);

    // Per-connection config overrides global default
    int readTimeoutMs =
        tcp.readTimeoutMs() != null ? tcp.readTimeoutMs() : socketConfig.getReadTimeoutMs();
    socket.setSoTimeout(readTimeoutMs);
    socket.setKeepAlive(socketConfig.isTcpKeepalive());

    if (Boolean.TRUE.equals(tcp.tcpNoDelay())) {
      socket.setTcpNoDelay(true);
    }

    // Not sure if we want to enforce specific protocols
    // TBD but its there in case we do
    if (mtls.enabledProtocols() != null) {
      socket.setEnabledProtocols(mtls.enabledProtocols());
    }
    if (mtls.enabledCiphers() != null) {
      socket.setEnabledCipherSuites(mtls.enabledCiphers());
    }
    socket.connect(new InetSocketAddress(tcp.host(), tcp.port()), 10_000);

    socket.startHandshake(); // throws if cert invalid/hostname mismatch
    return socket;
  }

  private SSLSocketFactory createTlsSocketFromZip(MtlsConfig mtls) {
    try {
      return sslContexts.create(mtls).getSocketFactory();
    } catch (MtlsMaterialException e) {
      log.error("Error creating the tls socket context", e);
      throw new SocketConnectorException(TLS_EXTRACTION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
