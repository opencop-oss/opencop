// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MappingRuleJsonTest {

  private static final UUID MAPPER_ID = UUID.fromString("c724aa29-d0e4-46c7-b9f0-b07eaac5e495");
  private static final UUID RULE_ID = UUID.fromString("841d36ef-98ae-4b2c-bc72-bb3b044b4336");

  private final ObjectMapper hibernateObjectMapper = new ObjectMapper();
  private final tools.jackson.databind.ObjectMapper applicationObjectMapper =
      new tools.jackson.databind.ObjectMapper();

  @Test
  void deserializesPersistedMapperContainingDerivedAttributeEntryFlag() throws Exception {
    var json =
        """
        {
          "id": "c724aa29-d0e4-46c7-b9f0-b07eaac5e495",
          "enabled": true,
          "rules": [
            {
              "id": "841d36ef-98ae-4b2c-bc72-bb3b044b4336",
              "enabled": true,
              "attributeEntry": false
            }
          ]
        }
        """;

    var mapper = hibernateObjectMapper.readValue(json, DynamicMapper.class);

    assertThat(mapper.id()).isEqualTo(MAPPER_ID);
    assertThat(mapper.rules())
        .singleElement()
        .satisfies(rule -> assertThat(rule.id()).isEqualTo(RULE_ID));
    assertThat(applicationObjectMapper.readValue(json, DynamicMapper.class)).isEqualTo(mapper);
  }

  @Test
  void doesNotPersistDerivedAttributeEntryFlag() throws Exception {
    var rule =
        MappingRuleBuilder.rule().target(TargetField.DISPLAY_NAME).xpath("name/text()").build();
    var mapper = new DynamicMapper(MAPPER_ID, true, null, null, List.of(rule));

    var hibernateJson = hibernateObjectMapper.writeValueAsString(mapper);
    var applicationJson = applicationObjectMapper.writeValueAsString(mapper);

    assertThat(hibernateJson).doesNotContain("\"attributeEntry\"");
    assertThat(applicationJson).doesNotContain("\"attributeEntry\"");
    assertThat(hibernateObjectMapper.readValue(hibernateJson, DynamicMapper.class))
        .isEqualTo(mapper);
    assertThat(applicationObjectMapper.readValue(applicationJson, DynamicMapper.class))
        .isEqualTo(mapper);
  }
}
