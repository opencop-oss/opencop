// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.factory.tcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnectorException;
import com.opencop.api.connect.technical.config.ConnectProperties;
import com.opencop.core.sdk.model.connection.spec.security.KeystoreRef;
import com.opencop.core.sdk.model.connection.spec.security.MtlsConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class MtlsTcpSessionsTest {

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final String CLIENT_REF = "opencop/shared/connect/source/generation/client";
  private static final String PKCS12 = "PKCS12";

  private final ConnectionStore connectionStore = mock(ConnectionStore.class);
  private final MtlsTcpSessions sessions =
      new MtlsTcpSessions(connectionStore, mock(ConnectProperties.class));
  private final Logger logger = (Logger) LoggerFactory.getLogger(MtlsTcpSessions.class);
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

  private Level previousLevel;

  @BeforeEach
  void captureLogs() {
    previousLevel = logger.getLevel();
    logger.setLevel(Level.ERROR);
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void releaseLogs() {
    logger.detachAppender(logs);
    logs.stop();
    logger.setLevel(previousLevel);
  }

  /**
   * A wrong password, corrupted material and an unreachable store all fail the same way for the
   * caller, so only the log tells them apart: it keeps the underlying cause.
   */
  @Test
  void unusableMaterialIsLoggedWithItsCause() {
    when(connectionStore.read(CLIENT_REF)).thenReturn(Map.of("b64", "%%%", "password", "p"));
    var mtls = new MtlsConfig(null, null, false, null, new KeystoreRef(CLIENT_REF, PKCS12));
    var tcp = new TcpConfig("remote.example", 8443, null, null, null, null, mtls);

    assertThatThrownBy(() -> sessions.opener(SOURCE_ID, tcp, mtls).open())
        .isInstanceOf(SocketConnectorException.class);

    var logged = assertThat(logs.list).singleElement().actual().getThrowableProxy();
    assertThat(logged).as("the logged failure").isNotNull();
    assertThat(logged.getCause().getClassName())
        .isEqualTo(IllegalArgumentException.class.getName());
  }
}
