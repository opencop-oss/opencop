// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExtensionBodyTest {

  /** The host checks the value against the type, and a boxed value is never a primitive. */
  @Test
  void aPrimitiveTypeStandsForItsWrapper() {
    assertThat(type(ExtensionBody.json(int.class))).isEqualTo(Integer.class);
    assertThat(type(ExtensionBody.json(boolean.class))).isEqualTo(Boolean.class);
    assertThat(type(ExtensionBody.json(double.class))).isEqualTo(Double.class);
  }

  @Test
  void anyOtherTypeIsKept() {
    assertThat(type(ExtensionBody.json(Integer.class))).isEqualTo(Integer.class);
    assertThat(type(ExtensionBody.json(String.class))).isEqualTo(String.class);
  }

  @Test
  void voidIsDeclaredAsNone() {
    assertThatThrownBy(() -> ExtensionBody.json(void.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("none()");
  }

  private static Class<?> type(ExtensionBody<?> body) {
    return ((ExtensionBody.Json<?>) body).type();
  }
}
