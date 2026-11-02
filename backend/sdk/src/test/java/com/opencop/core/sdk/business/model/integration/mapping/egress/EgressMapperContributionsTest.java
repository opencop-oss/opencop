// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.mapping.egress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EgressMapperContributionsTest {

  private static final UUID MAPPER_ID = UUID.fromString("7d1f3c52-0b6e-4f0e-9a51-0f4a2f6f8e10");
  private static final Integration INTEGRATION =
      new TestIntegration(
          UUID.fromString("7d1f3c52-0b6e-4f0e-9a51-0f4a2f6f8e11"),
          "Test integration",
          "Test integration");

  @Test
  void findsNoEgressMapperWhenNoIntegrationContributesOne() {
    var contributions = EgressMapperContributions.of(List.of());

    assertThat(contributions.find(MAPPER_ID)).isEmpty();
    assertThat(contributions.find(null)).isEmpty();
  }

  @Test
  void findsAContributedEgressMapperByItsIdentifier() {
    var mapper = mapper(MAPPER_ID, "COT Egress", 1);
    var contributions =
        EgressMapperContributions.of(
            List.of(new IntegrationEgressMappers(INTEGRATION, List.of(mapper))));

    assertThat(contributions.find(MAPPER_ID)).containsSame(mapper);
    assertThat(contributions.find(UUID.randomUUID())).isEmpty();
    assertThat(contributions.groups())
        .extracting(IntegrationEgressMappers::integration)
        .containsExactly(INTEGRATION);
  }

  private static PayloadEgressMapper mapper(UUID id, String name, int version) {
    var mapper = mock(PayloadEgressMapper.class);
    when(mapper.id()).thenReturn(id);
    when(mapper.name()).thenReturn(name);
    when(mapper.version()).thenReturn(version);
    return mapper;
  }
}
