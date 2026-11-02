// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConnectionExtensionExceptionTest {

  private static final String MESSAGE = "Mission alpha does not exist";

  @Test
  void keepsTheExtensionsOwnCode() {
    var failure =
        new ConnectionExtensionException(ExtensionFailure.NOT_FOUND, "MISSION_NOT_FOUND", MESSAGE);

    assertThat(failure.code()).contains("MISSION_NOT_FOUND");
  }

  /** A code under the host's prefixes would read to clients as the host's own answer. */
  @ParameterizedTest
  @ValueSource(strings = {"CONNECTION_NOT_FOUND", "EXTENSION_FAILED", "INTEGRATION_UNAVAILABLE"})
  void refusesACodeUnderTheHostsPrefixes(String code) {
    assertThatThrownBy(
            () -> new ConnectionExtensionException(ExtensionFailure.CONFLICT, code, MESSAGE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(code);
  }

  /** The host raises these from the record store, and an extension may pass the failure on. */
  @ParameterizedTest
  @ValueSource(
      strings = {ConnectionRecordStore.REVISION_CONFLICT, ConnectionRecordStore.LIMIT_EXCEEDED})
  void acceptsTheRecordStoresCodesItPassesOn(String code) {
    assertThat(new ConnectionExtensionException(ExtensionFailure.CONFLICT, code, MESSAGE).code())
        .contains(code);
  }
}
