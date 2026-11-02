// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MapperFramingDeclarationTest {

  private static final UUID MAPPER = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final UUID OTHER_MAPPER = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
  private static final UUID FRAMING = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
  private static final UUID OTHER_FRAMING = UUID.fromString("00000000-0000-4000-8000-0000000000b2");

  @Test
  void anExactPairPermitsOnlyThatMapperWithThatFraming() {
    var declaration = MapperFramingDeclaration.of(new MapperFramingPair(MAPPER, FRAMING));

    assertThat(declaration.allows(MAPPER, FRAMING)).isTrue();
    assertThat(declaration.allows(OTHER_MAPPER, FRAMING)).isFalse();
    assertThat(declaration.allows(MAPPER, OTHER_FRAMING)).isFalse();
    assertThat(declaration.allowsCustomFraming(MAPPER)).isFalse();
    assertThat(declaration.solePair()).contains(new MapperFramingPair(MAPPER, FRAMING));
  }

  /** Fixed pairs do not combine into their crossed combinations. */
  @Test
  void exactPairsNeverCombine() {
    var declaration =
        MapperFramingDeclaration.of(
            new MapperFramingPair(MAPPER, FRAMING),
            new MapperFramingPair(OTHER_MAPPER, OTHER_FRAMING));

    assertThat(declaration.allows(MAPPER, OTHER_FRAMING)).isFalse();
    assertThat(declaration.solePair()).isEmpty();
  }

  @Test
  void anOpenFramingPermitsAnyMapperWithIt() {
    var declaration =
        MapperFramingDeclaration.of(
            new MapperFramingPair(MAPPER, FRAMING), new FramingRule.AnyMapperWith(OTHER_FRAMING));

    assertThat(declaration.allows(OTHER_MAPPER, OTHER_FRAMING)).isTrue();
    assertThat(declaration.allows(OTHER_MAPPER, FRAMING)).isFalse();
    assertThat(declaration.solePair()).isEmpty();
    assertThat(declaration.allowedPairs()).containsExactly(new MapperFramingPair(MAPPER, FRAMING));
    assertThat(declaration.openMapperFramings()).containsExactly(OTHER_FRAMING);
    assertThat(declaration.framingIds()).containsExactlyInAnyOrder(FRAMING, OTHER_FRAMING);
  }

  @Test
  void aCustomFramingRulePermitsAnyMapperWithAHandTypedDelimiterOnly() {
    var declaration = MapperFramingDeclaration.of(new FramingRule.AnyMapperCustomFraming());

    assertThat(declaration.allowsCustomFraming(MAPPER)).isTrue();
    assertThat(declaration.allowsCustomFraming(null)).isFalse();
    assertThat(declaration.allows(MAPPER, FRAMING)).isFalse();
    assertThat(declaration.solePair()).isEmpty();
    assertThat(declaration.framingIds()).isEmpty();
  }

  /** Framing rules govern what a connection reads; only an egress rule lets it write. */
  @Test
  void writesOnlyWithTheEgressItsRulePermits() {
    var readOnly = MapperFramingDeclaration.of(new MapperFramingPair(MAPPER, FRAMING));
    var closed = readOnly.withEgress(EgressMapperRule.closedTo(Set.of(OTHER_MAPPER)));
    var open = readOnly.withEgress(EgressMapperRule.openMapper());

    assertThat(readOnly.egress()).isEmpty();
    assertThat(closed.egress().orElseThrow().allows(OTHER_MAPPER)).isTrue();
    assertThat(closed.egress().orElseThrow().allows(MAPPER)).isFalse();
    assertThat(open.egress().orElseThrow().allows(MAPPER)).isTrue();
    assertThat(open.egress().orElseThrow().allows(null)).isFalse();
    assertThat(closed.rules()).isEqualTo(readOnly.rules());
  }

  @Test
  void anEgressRulePermitsSomething() {
    assertThatThrownBy(() -> EgressMapperRule.closedTo(Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requiresAtLeastOneRule() {
    assertThatThrownBy(() -> new MapperFramingDeclaration(Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
