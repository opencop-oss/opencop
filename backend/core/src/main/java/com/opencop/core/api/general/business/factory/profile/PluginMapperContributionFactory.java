// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.factory.profile;

import com.opencop.core.api.general.business.model.profile.PluginMapperContribution;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import java.util.List;

/** Reads the catalog identity of every mapper contribution, with its owning integration. */
public final class PluginMapperContributionFactory {

  private PluginMapperContributionFactory() {}

  public static List<PluginMapperContribution> ingress(IngressMapperContributions contributions) {
    return contributions.groups().stream()
        .flatMap(
            group ->
                group.mappers().stream()
                    .map(
                        mapper ->
                            new PluginMapperContribution(
                                mapper.id(),
                                mapper.name(),
                                mapper.description(),
                                mapper.version(),
                                MapperQualifier.INGRESS,
                                group.integration().id())))
        .toList();
  }

  public static List<PluginMapperContribution> egress(EgressMapperContributions contributions) {
    return contributions.groups().stream()
        .flatMap(
            group ->
                group.mappers().stream()
                    .map(
                        mapper ->
                            new PluginMapperContribution(
                                mapper.id(),
                                mapper.name(),
                                mapper.description(),
                                mapper.version(),
                                MapperQualifier.EGRESS,
                                group.integration().id())))
        .toList();
  }
}
