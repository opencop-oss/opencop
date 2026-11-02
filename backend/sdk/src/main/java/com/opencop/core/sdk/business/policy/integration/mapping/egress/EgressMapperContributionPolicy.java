// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy.integration.mapping.egress;

import static com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributionDecision.RejectionReason.BLANK_NAME;
import static com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributionDecision.RejectionReason.DUPLICATE_UUID;
import static com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributionDecision.RejectionReason.MISSING_UUID;
import static com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributionDecision.RejectionReason.NON_POSITIVE_VERSION;

import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributionDecision;
import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Decides which egress mapper contributions have valid and unambiguous business identity, with the
 * same rules as ingress mapper contributions.
 */
public final class EgressMapperContributionPolicy {

  private static final Comparator<PayloadEgressMapper> DECISION_ORDER =
      Comparator.comparing(
              (PayloadEgressMapper contribution) -> Objects.toString(contribution.id(), ""))
          .thenComparing(contribution -> Objects.toString(contribution.name(), ""))
          .thenComparingInt(PayloadEgressMapper::version)
          .thenComparing(contribution -> contribution.getClass().getName());

  private EgressMapperContributionPolicy() {}

  public static List<EgressMapperContributionDecision> assess(
      List<PayloadEgressMapper> contributions) {
    var candidates = List.copyOf(Objects.requireNonNull(contributions, "contributions"));
    var duplicateIds = duplicateIds(candidates);

    return candidates.stream()
        .sorted(DECISION_ORDER)
        .map(contribution -> assess(contribution, duplicateIds))
        .toList();
  }

  private static EgressMapperContributionDecision assess(
      PayloadEgressMapper contribution, Set<UUID> duplicateIds) {
    if (contribution.id() == null) {
      return EgressMapperContributionDecision.rejected(contribution, MISSING_UUID);
    }
    if (duplicateIds.contains(contribution.id())) {
      return EgressMapperContributionDecision.rejected(contribution, DUPLICATE_UUID);
    }
    if (contribution.name() == null || contribution.name().isBlank()) {
      return EgressMapperContributionDecision.rejected(contribution, BLANK_NAME);
    }
    if (contribution.version() <= 0) {
      return EgressMapperContributionDecision.rejected(contribution, NON_POSITIVE_VERSION);
    }
    return EgressMapperContributionDecision.admitted(contribution);
  }

  private static Set<UUID> duplicateIds(List<PayloadEgressMapper> contributions) {
    return contributions.stream()
        .map(PayloadEgressMapper::id)
        .filter(Objects::nonNull)
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
        .entrySet()
        .stream()
        .filter(entry -> entry.getValue() > 1)
        .map(Map.Entry::getKey)
        .collect(Collectors.toUnmodifiableSet());
  }
}
