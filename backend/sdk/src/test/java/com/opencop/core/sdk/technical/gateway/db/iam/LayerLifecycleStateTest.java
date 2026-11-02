// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LayerLifecycleStateTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String ACTIVE = "ACTIVE";
  private static final String MERGE_SOURCE_LOCKED = "MERGE_SOURCE_LOCKED";
  private static final String PENDING_MERGE = "PENDING_MERGE";
  private static final String MERGE_FAILED = "MERGE_FAILED";
  private static final String ARCHIVED = "ARCHIVED";

  @Test
  void enumNamesMatchDatabaseValues() {
    assertThat(Arrays.stream(LayerLifecycleState.values()).map(Enum::name))
        .containsExactly(ACTIVE, MERGE_SOURCE_LOCKED, PENDING_MERGE, MERGE_FAILED, ARCHIVED);
  }

  @Test
  void jsonSerializationUsesDatabaseNames() throws Exception {
    assertThat(OBJECT_MAPPER.writeValueAsString(LayerLifecycleState.ACTIVE))
        .isEqualTo("\"" + ACTIVE + "\"");
    assertThat(OBJECT_MAPPER.writeValueAsString(LayerLifecycleState.MERGE_SOURCE_LOCKED))
        .isEqualTo("\"" + MERGE_SOURCE_LOCKED + "\"");
    assertThat(OBJECT_MAPPER.writeValueAsString(LayerLifecycleState.PENDING_MERGE))
        .isEqualTo("\"" + PENDING_MERGE + "\"");
    assertThat(OBJECT_MAPPER.writeValueAsString(LayerLifecycleState.MERGE_FAILED))
        .isEqualTo("\"" + MERGE_FAILED + "\"");
    assertThat(OBJECT_MAPPER.writeValueAsString(LayerLifecycleState.ARCHIVED))
        .isEqualTo("\"" + ARCHIVED + "\"");
  }
}
