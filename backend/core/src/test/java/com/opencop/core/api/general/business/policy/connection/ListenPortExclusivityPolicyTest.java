// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ListenPortExclusivityPolicyTest {

  private static final int HELD_LISTEN_PORT = 9100;
  private static final String LISTEN_PORT_IN_USE_CODE = "CORE-CONNECTION-1007";
  private static final UUID HOLDER_ID = UUID.fromString("6d5f9b0a-6a41-4e2a-9f0c-1c1e0f6a1b22");

  @Test
  void udpReservesItsListenPort() {
    assertThat(ListenPortExclusivityPolicy.reservedListenPort(new UdpConfig("0.0.0.0", 9000, null)))
        .hasValue(9000);
  }

  @Test
  void udpReservesItsLocalPortWhenSet() {
    assertThat(ListenPortExclusivityPolicy.reservedListenPort(new UdpConfig("0.0.0.0", 9000, 9001)))
        .hasValue(9001);
  }

  @Test
  void outboundTransportsReserveNothing() {
    assertThat(
            ListenPortExclusivityPolicy.reservedListenPort(
                new TcpConfig("127.0.0.1", 8087, 3000, 15000, true, null, new NoSecurity())))
        .isEmpty();
    assertThat(
            ListenPortExclusivityPolicy.reservedListenPort(
                new RtspConfig("rtsp://192.168.1.100:8554/stream", null)))
        .isEmpty();
  }

  @Test
  void aFreePortIsAvailable() {
    assertThatCode(
            () -> ListenPortExclusivityPolicy.assertAvailable(HELD_LISTEN_PORT, Optional.empty()))
        .doesNotThrowAnyException();
  }

  @Test
  void aPortAnotherActiveSourceHoldsIsRefusedAsAConflict() {
    assertThatThrownBy(
            () ->
                ListenPortExclusivityPolicy.assertAvailable(
                    HELD_LISTEN_PORT, Optional.of(HOLDER_ID)))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode()).isEqualTo(LISTEN_PORT_IN_USE_CODE);
              assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(exception.getMessage()).contains(String.valueOf(HELD_LISTEN_PORT));
            });
  }

  @Test
  void theConflictNamesThePortWithoutNamingItsHolder() {
    var conflict = ListenPortExclusivityPolicy.portAlreadyReserved(HELD_LISTEN_PORT);

    assertThat(conflict.getCode()).isEqualTo(LISTEN_PORT_IN_USE_CODE);
    assertThat(conflict.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(conflict.getMessage())
        .contains(String.valueOf(HELD_LISTEN_PORT))
        .doesNotContain(HOLDER_ID.toString());
  }
}
