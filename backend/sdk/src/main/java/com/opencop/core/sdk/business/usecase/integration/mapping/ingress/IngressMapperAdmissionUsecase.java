// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.usecase.integration.mapping.ingress;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperAdmission;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributionDecision;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.business.policy.integration.mapping.ingress.IngressMapperContributionPolicy;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.ports.integration.mapping.IngressMappingContribution;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;

/** Coordinates mapper-admission decisions and assembles the admitted-only contribution view. */
public final class IngressMapperAdmissionUsecase {

  public IngressMapperAdmission admit(List<? extends IngressMappingContribution> groups) {
    var owners = new IdentityHashMap<PayloadIngressMapper<? extends Feature>, UUID>();
    groups.forEach(
        group -> group.mappers().forEach(mapper -> owners.put(mapper, group.integration().id())));

    var decisions = IngressMapperContributionPolicy.assess(List.copyOf(owners.keySet()));
    // Groups are keyed by integration id: an Integration implementation need not define equality.
    var integrations = new HashMap<UUID, Integration>();
    groups.forEach(
        group -> integrations.putIfAbsent(group.integration().id(), group.integration()));
    var admittedByIntegration =
        new LinkedHashMap<UUID, List<PayloadIngressMapper<? extends Feature>>>();
    integrations.keySet().stream()
        .sorted()
        .forEach(id -> admittedByIntegration.put(id, new ArrayList<>()));
    // Decisions arrive in the policy's deterministic order, which each group keeps.
    decisions.stream()
        .filter(IngressMapperContributionDecision::admitted)
        .map(IngressMapperContributionDecision::contribution)
        .forEach(mapper -> admittedByIntegration.get(owners.get(mapper)).add(mapper));
    var rejections = decisions.stream().filter(decision -> !decision.admitted()).toList();

    var admittedGroups =
        admittedByIntegration.entrySet().stream()
            .map(
                entry ->
                    new IntegrationIngressMappers(
                        integrations.get(entry.getKey()), entry.getValue()))
            .toList();
    return new IngressMapperAdmission(IngressMapperContributions.of(admittedGroups), rejections);
  }
}
