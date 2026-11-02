// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.mapping;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapper/framing choices permitted by one integration schema and protocol, as a set of {@link
 * FramingRule rules}: a selection is permitted when any rule permits it.
 *
 * <p>Rules never combine: two exact pairs do not permit their crossed combinations. The caller
 * still checks mapper availability, archival state, and access-mode eligibility.
 *
 * <p>The framing rules concern what the connection reads. What it writes is governed by the egress
 * rule alone, and an integration without one cannot write.
 *
 * @param rules how a reading connection may pair its ingress mapper with a framing
 * @param egress the egress mappers a writing connection may select, empty when it cannot write
 */
public record MapperFramingDeclaration(Set<FramingRule> rules, Optional<EgressMapperRule> egress)
    implements ConnectionMappingDeclaration {

  public MapperFramingDeclaration {
    rules = Set.copyOf(Objects.requireNonNull(rules, "rules"));
    Objects.requireNonNull(egress, "egress");
    // An empty declaration would leave the plugin with no valid connection setup.
    if (rules.isEmpty()) {
      throw new IllegalArgumentException("At least one mapper/framing rule is required");
    }
  }

  /** A declaration whose connections can only read. */
  public MapperFramingDeclaration(Set<FramingRule> rules) {
    this(rules, Optional.empty());
  }

  /** A declaration whose connections can only read. */
  public static MapperFramingDeclaration of(FramingRule... rules) {
    return new MapperFramingDeclaration(Set.of(rules));
  }

  /**
   * This declaration, letting its connections also write with the egress mappers the rule permits.
   */
  public MapperFramingDeclaration withEgress(EgressMapperRule rule) {
    return new MapperFramingDeclaration(rules, Optional.of(rule));
  }

  /**
   * Checks the plugin's rules for a mapper reading a stream split by a framing the integration
   * provides. Catalog availability and access-mode eligibility are separate checks performed by the
   * Core caller.
   */
  public boolean allows(UUID mapperId, UUID framingId) {
    if (mapperId == null || framingId == null) {
      return false;
    }
    return rules.stream()
        .anyMatch(
            rule ->
                switch (rule) {
                  case MapperFramingPair pair ->
                      pair.mapperId().equals(mapperId) && pair.framingId().equals(framingId);
                  case FramingRule.AnyMapperWith open -> open.framingId().equals(framingId);
                  case FramingRule.AnyMapperCustomFraming _ -> false;
                });
  }

  /**
   * Checks the plugin's rules for a mapper reading a stream split by the connection's own
   * delimiter.
   */
  public boolean allowsCustomFraming(UUID mapperId) {
    return mapperId != null && rules.contains(new FramingRule.AnyMapperCustomFraming());
  }

  /** A pair can be selected automatically only when it is the plugin's entire choice set. */
  public Optional<MapperFramingPair> solePair() {
    if (rules.size() != 1 || !(rules.iterator().next() instanceof MapperFramingPair pair)) {
      return Optional.empty();
    }
    return Optional.of(pair);
  }

  /** The exact pairs among the rules. */
  public Set<MapperFramingPair> allowedPairs() {
    return rules.stream()
        .filter(MapperFramingPair.class::isInstance)
        .map(MapperFramingPair.class::cast)
        .collect(Collectors.toUnmodifiableSet());
  }

  /** The framings among the rules with which any mapper may be selected. */
  public Set<UUID> openMapperFramings() {
    return rules.stream()
        .filter(FramingRule.AnyMapperWith.class::isInstance)
        .map(rule -> ((FramingRule.AnyMapperWith) rule).framingId())
        .collect(Collectors.toUnmodifiableSet());
  }

  /** Every framing the rules reference, each of which the integration must provide in code. */
  public Set<UUID> framingIds() {
    return rules.stream()
        .<UUID>mapMulti(
            (rule, framings) -> {
              switch (rule) {
                case MapperFramingPair pair -> framings.accept(pair.framingId());
                case FramingRule.AnyMapperWith open -> framings.accept(open.framingId());
                case FramingRule.AnyMapperCustomFraming _ -> {
                  // A custom framing is the connection's own, so the integration provides none.
                }
              }
            })
        .collect(Collectors.toUnmodifiableSet());
  }
}
