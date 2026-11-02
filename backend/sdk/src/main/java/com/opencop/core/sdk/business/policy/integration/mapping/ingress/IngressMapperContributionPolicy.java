// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy.integration.mapping.ingress;

import static com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributionDecision.RejectionReason.BLANK_NAME;
import static com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributionDecision.RejectionReason.DUPLICATE_UUID;
import static com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributionDecision.RejectionReason.MISSING_UUID;
import static com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributionDecision.RejectionReason.NON_POSITIVE_VERSION;

import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributionDecision;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.geojson.Feature;

/** Decides which ingress mapper contributions have valid and unambiguous business identity. */
public final class IngressMapperContributionPolicy {

  private static final Comparator<PayloadIngressMapper<? extends Feature>> DECISION_ORDER =
      Comparator.comparing(
              (PayloadIngressMapper<? extends Feature> contribution) ->
                  Objects.toString(contribution.id(), ""))
          .thenComparing(contribution -> Objects.toString(contribution.name(), ""))
          .thenComparingInt(PayloadIngressMapper::version)
          .thenComparing(contribution -> contribution.getClass().getName());

  private IngressMapperContributionPolicy() {}

  public static List<IngressMapperContributionDecision> assess(
      List<PayloadIngressMapper<? extends Feature>> contributions) {
    var candidates = List.copyOf(Objects.requireNonNull(contributions, "contributions"));
    var duplicateIds = duplicateIds(candidates);

    return candidates.stream()
        .sorted(DECISION_ORDER)
        .map(contribution -> assess(contribution, duplicateIds))
        .toList();
  }

  private static IngressMapperContributionDecision assess(
      PayloadIngressMapper<? extends Feature> contribution, Set<UUID> duplicateIds) {
    if (contribution.id() == null) {
      return IngressMapperContributionDecision.rejected(contribution, MISSING_UUID);
    }
    if (duplicateIds.contains(contribution.id())) {
      return IngressMapperContributionDecision.rejected(contribution, DUPLICATE_UUID);
    }
    if (contribution.name() == null || contribution.name().isBlank()) {
      return IngressMapperContributionDecision.rejected(contribution, BLANK_NAME);
    }
    if (contribution.version() <= 0) {
      return IngressMapperContributionDecision.rejected(contribution, NON_POSITIVE_VERSION);
    }
    return IngressMapperContributionDecision.admitted(contribution);
  }

  private static Set<UUID> duplicateIds(
      List<PayloadIngressMapper<? extends Feature>> contributions) {
    return contributions.stream()
        .map(PayloadIngressMapper::id)
        .filter(Objects::nonNull)
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
        .entrySet()
        .stream()
        .filter(entry -> entry.getValue() > 1)
        .map(java.util.Map.Entry::getKey)
        .collect(Collectors.toUnmodifiableSet());
  }
}
