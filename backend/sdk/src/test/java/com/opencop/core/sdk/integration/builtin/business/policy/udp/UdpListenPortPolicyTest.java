// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.business.policy.udp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UdpListenPortPolicyTest {

  @ParameterizedTest
  @ValueSource(ints = {1, 8087, 65535})
  void acceptsABindablePort(int port) throws ConnectionInputException {
    assertThat(UdpListenPortPolicy.requireBindable(port)).isEqualTo(port);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 65536})
  void rejectsAPortOutsideTheBindableRange(int port) {
    assertRejected(
        () -> UdpListenPortPolicy.requireBindable(port), UdpListenPortPolicy.INVALID_PORT);
  }

  @ParameterizedTest
  @NullSource
  void rejectsAMissingPort(Integer port) {
    assertRejected(
        () -> UdpListenPortPolicy.requireBindable(port), UdpListenPortPolicy.MISSING_PORT);
  }

  @Test
  void readsASuppliedPortAsText() throws ConnectionInputException {
    assertThat(UdpListenPortPolicy.requireBindable(" 8087 ")).isEqualTo(8087);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-a-port", "80.5", "0", "65536"})
  void rejectsSuppliedTextThatIsNotABindablePort(String port) {
    assertRejected(
        () -> UdpListenPortPolicy.requireBindable(port), UdpListenPortPolicy.INVALID_PORT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void rejectsBlankSuppliedTextAsAMissingPort(String port) {
    assertRejected(
        () -> UdpListenPortPolicy.requireBindable(port), UdpListenPortPolicy.MISSING_PORT);
  }

  private static void assertRejected(Rejection rejection, String detailCode) {
    assertThatThrownBy(rejection::run)
        .isInstanceOfSatisfying(
            ConnectionInputException.class,
            exception -> {
              assertThat(exception.failure()).isEqualTo(ConnectionInputFailure.INVALID_INPUT);
              assertThat(exception.providerDetailCode()).contains(detailCode);
              assertThat(exception.field()).contains(UdpListenPortPolicy.LISTEN_PORT_FIELD);
            });
  }

  @FunctionalInterface
  private interface Rejection {
    void run() throws ConnectionInputException;
  }
}
