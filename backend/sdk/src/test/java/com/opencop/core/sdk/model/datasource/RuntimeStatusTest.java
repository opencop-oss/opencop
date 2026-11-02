// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorStateReason;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RuntimeStatusTest {

  private static final UUID SOURCE_ID = UUID.fromString("3f6a1c2e-8b4d-4e7a-9f15-2c8d0b7e6a91");
  private static final String INSTANCE_ID = "connect-a";

  @Test
  void failedCarriesItsReason() {
    var status =
        RuntimeStatus.failed(SOURCE_ID, INSTANCE_ID, ConnectorStateReason.INTEGRATION_UNAVAILABLE);

    assertThat(status.state()).isEqualTo(ConnectorState.FAILED);
    assertThat(status.stateReason()).isEqualTo(ConnectorStateReason.INTEGRATION_UNAVAILABLE);
    assertThat(status.instanceId()).isEqualTo(INSTANCE_ID);
  }

  @ParameterizedTest
  @EnumSource(value = ConnectorState.class, names = "FAILED", mode = EnumSource.Mode.EXCLUDE)
  void rejectsAReasonOnAnyStateButFailed(ConnectorState state) {
    assertThatThrownBy(
            () ->
                new RuntimeStatus(
                    SOURCE_ID,
                    state,
                    null,
                    INSTANCE_ID,
                    null,
                    null,
                    ConnectorStateReason.INTEGRATION_UNAVAILABLE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(state.name());
  }

  @ParameterizedTest
  @EnumSource(ConnectorState.class)
  void acceptsEveryStateWithoutAReason(ConnectorState state) {
    assertThat(new RuntimeStatus(SOURCE_ID, state, null, INSTANCE_ID).stateReason()).isNull();
  }
}
