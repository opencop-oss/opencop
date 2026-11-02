// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.usecase.integration.mapping.egress;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperAdmission;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributionDecision;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.egress.IntegrationEgressMappers;
import com.opencop.core.sdk.business.policy.integration.mapping.egress.EgressMapperContributionPolicy;
import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import com.opencop.core.sdk.ports.integration.mapping.EgressMappingContribution;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates egress mapper admission decisions and assembles the admitted-only contribution view,
 * as {@link IngressMapperAdmissionUsecase} does for ingress mappers.
 */
public final class EgressMapperAdmissionUsecase {

  public EgressMapperAdmission admit(List<? extends EgressMappingContribution> groups) {
    var owners = new IdentityHashMap<PayloadEgressMapper, UUID>();
    groups.forEach(
        group -> group.mappers().forEach(mapper -> owners.put(mapper, group.integration().id())));

    var decisions = EgressMapperContributionPolicy.assess(List.copyOf(owners.keySet()));
    // Groups are keyed by integration id: an Integration implementation need not define equality.
    var integrations = new HashMap<UUID, Integration>();
    groups.forEach(
        group -> integrations.putIfAbsent(group.integration().id(), group.integration()));
    var admittedByIntegration = new LinkedHashMap<UUID, List<PayloadEgressMapper>>();
    integrations.keySet().stream()
        .sorted()
        .forEach(id -> admittedByIntegration.put(id, new ArrayList<>()));
    // Decisions arrive in the policy's deterministic order, which each group keeps.
    decisions.stream()
        .filter(EgressMapperContributionDecision::admitted)
        .map(EgressMapperContributionDecision::contribution)
        .forEach(mapper -> admittedByIntegration.get(owners.get(mapper)).add(mapper));
    var rejections = decisions.stream().filter(decision -> !decision.admitted()).toList();

    var admittedGroups =
        admittedByIntegration.entrySet().stream()
            .map(
                entry ->
                    new IntegrationEgressMappers(
                        integrations.get(entry.getKey()), entry.getValue()))
            .toList();
    return new EgressMapperAdmission(EgressMapperContributions.of(admittedGroups), rejections);
  }
}
