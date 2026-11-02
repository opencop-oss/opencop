// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AccessModeTest {

  @ParameterizedTest
  @MethodSource("validWireValues")
  void parsesAnExactWireValue(String wireValue, AccessMode expected) {
    assertThat(AccessMode.fromString(wireValue)).isEqualTo(expected);
  }

  @Test
  void rejectsAnUnknownWireValue() {
    assertThatThrownBy(() -> AccessMode.fromString("UNKNOWN"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNull() {
    assertThatThrownBy(() -> AccessMode.fromString(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "r", "w", "rw", "Rw", " R", "R ", "UNKNOWN"})
  void rejectsAMalformedWireValue(String wireValue) {
    assertThatThrownBy(() -> AccessMode.fromString(wireValue))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @MethodSource("directionCapabilities")
  void exposesDirectionCapabilities(AccessMode accessMode, boolean readable, boolean writable) {
    assertThat(accessMode.canRead()).isEqualTo(readable);
    assertThat(accessMode.canWrite()).isEqualTo(writable);
  }

  private static Arguments[] validWireValues() {
    return new Arguments[] {
      Arguments.of("R", AccessMode.READ),
      Arguments.of("W", AccessMode.WRITE),
      Arguments.of("RW", AccessMode.READ_WRITE)
    };
  }

  private static Arguments[] directionCapabilities() {
    return new Arguments[] {
      Arguments.of(AccessMode.READ, true, false),
      Arguments.of(AccessMode.WRITE, false, true),
      Arguments.of(AccessMode.READ_WRITE, true, true)
    };
  }
}
