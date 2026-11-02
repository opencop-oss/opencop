// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.model.datasource.AccessMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConnectionAccessChangePolicyTest {

  @Test
  void acceptsAChangeBetweenReadOnlyAccesses() {
    assertThatCode(
            () -> ConnectionAccessChangePolicy.assertSupported(AccessMode.READ, AccessMode.READ))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @CsvSource({"R,W", "R,RW", "W,R", "RW,R", "W,RW", "RW,W"})
  void rejectsAChangeToOrFromAWritableAccess(String current, String requested) {
    assertThatThrownBy(
            () ->
                ConnectionAccessChangePolicy.assertSupported(
                    AccessMode.fromString(current), AccessMode.fromString(requested)))
        .isInstanceOfSatisfying(
            ConnectionManagementException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("ACCESS_CHANGE_UNSUPPORTED"));
  }

  @ParameterizedTest
  @CsvSource({
    "R,RW,Access cannot change from READ to READ_WRITE: a patch cannot make a connection writable"
        + " or change a writable connection's access",
    "RW,R,Access cannot change from READ_WRITE to READ: a patch cannot make a connection writable"
        + " or change a writable connection's access"
  })
  void explainsTheRejectionWhicheverSideIsWritable(
      String current, String requested, String message) {
    assertThatThrownBy(
            () ->
                ConnectionAccessChangePolicy.assertSupported(
                    AccessMode.fromString(current), AccessMode.fromString(requested)))
        .hasMessage(message);
  }
}
