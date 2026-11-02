// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.model.datasource.AccessMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccessModeFormatPolicyTest {

  @Test
  void parsesEachAccessMode() {
    assertThat(AccessModeFormatPolicy.parse("R")).isEqualTo(AccessMode.READ);
    assertThat(AccessModeFormatPolicy.parse("W")).isEqualTo(AccessMode.WRITE);
    assertThat(AccessModeFormatPolicy.parse("RW")).isEqualTo(AccessMode.READ_WRITE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"X", "", "read"})
  void rejectsAValueThatNamesNoAccessMode(String rawAccess) {
    assertThatThrownBy(() -> AccessModeFormatPolicy.parse(rawAccess))
        .isInstanceOfSatisfying(
            ConnectionManagementException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("CORE-CONNECTION-1005"));
  }
}
