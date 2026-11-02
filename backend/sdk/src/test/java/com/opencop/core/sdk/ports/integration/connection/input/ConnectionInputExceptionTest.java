// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConnectionInputExceptionTest {

  /** A client highlights the rejected input, so the extractor may name the field it rejected. */
  @Test
  void carriesTheDeclaredFieldItRejects() {
    var exception =
        ConnectionInputException.rejecting(
            "listenPort",
            ConnectionInputFailure.INVALID_INPUT,
            "INVALID_UDP_PORT",
            "Listen port must be between 1 and 65535");

    assertThat(exception.field()).contains("listenPort");
    assertThat(exception.providerDetailCode()).contains("INVALID_UDP_PORT");
    assertThat(exception.getMessage()).isEqualTo("Listen port must be between 1 and 65535");
  }

  @Test
  void namesNoFieldWhenTheRejectionConcernsTheInputAsAWhole() {
    var exception =
        new ConnectionInputException(ConnectionInputFailure.INVALID_INPUT, null, "Invalid input");

    assertThat(exception.field()).isEmpty();
  }

  @Test
  void rejectsABlankFieldName() {
    assertThatThrownBy(
            () ->
                ConnectionInputException.rejecting(
                    " ", ConnectionInputFailure.INVALID_INPUT, null, "Invalid input"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field");
  }
}
