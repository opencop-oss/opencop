// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SettingsDocumentWireNumberTest {

  @Test
  void writesAWholeNumberAsALong() {
    assertThat(SettingsDocument.wireNumber(9_999_999.0)).isEqualTo(9_999_999L);
    assertThat(SettingsDocument.wireNumber(-11_000.0)).isEqualTo(-11_000L);
  }

  @Test
  void keepsAFractionalNumberADouble() {
    assertThat(SettingsDocument.wireNumber(45.5)).isEqualTo(45.5);
  }

  @Test
  void keepsAWholeNumberBeyondTheLongRangeADoubleRatherThanSaturatingIt() {
    assertThat(SettingsDocument.wireNumber(1e20)).isEqualTo(1e20);
  }
}
