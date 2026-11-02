// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class ConnectionNamePolicyTest {

  @Test
  void acceptsANameAtTheBound() {
    assertThatNoException()
        .isThrownBy(
            () ->
                ConnectionNamePolicy.require(
                    "a".repeat(ConnectionNamePolicy.MAX_NAME_CODE_POINTS)));
  }

  @Test
  void rejectsANameOverTheBoundNamingTheField() {
    assertRejected(
        () ->
            ConnectionNamePolicy.require("a".repeat(ConnectionNamePolicy.MAX_NAME_CODE_POINTS + 1)),
        ConnectionExceptionCodes.CONNECTION_NAME_TOO_LONG);
  }

  /** A name in any script gets the same room: characters count, not their UTF-16 or UTF-8 size. */
  @Test
  void countsCharactersOutsideTheBasicPlaneOnce() {
    var emoji = "🛰"; // satellite, two UTF-16 units
    assertThatNoException()
        .isThrownBy(
            () ->
                ConnectionNamePolicy.require(
                    emoji.repeat(ConnectionNamePolicy.MAX_NAME_CODE_POINTS)));
    assertRejected(
        () ->
            ConnectionNamePolicy.require(
                emoji.repeat(ConnectionNamePolicy.MAX_NAME_CODE_POINTS + 1)),
        ConnectionExceptionCodes.CONNECTION_NAME_TOO_LONG);
  }

  @Test
  void rejectsABlankName() {
    assertRejected(
        () -> ConnectionNamePolicy.require("  "), ConnectionExceptionCodes.BLANK_CONNECTION_NAME);
    assertRejected(
        () -> ConnectionNamePolicy.require(null), ConnectionExceptionCodes.BLANK_CONNECTION_NAME);
  }

  private static void assertRejected(ThrowingCallable call, ConnectionExceptionCodes code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode()).isEqualTo(code.code());
              if (code == ConnectionExceptionCodes.CONNECTION_NAME_TOO_LONG) {
                assertThat(exception.getField()).isEqualTo("name");
                assertThat(exception.getMessage())
                    .isEqualTo("Connection name must not exceed 255 characters");
              }
            });
  }
}
