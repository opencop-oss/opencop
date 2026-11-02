// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.mapping.ingress;

import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import java.util.List;
import java.util.Objects;
import org.geojson.Feature;

/**
 * Immutable collection of ingress mapper contributions exposed to capability consumers, grouped by
 * the integration that owns them.
 *
 * <p>The collection retains the original {@link PayloadIngressMapper} contract instead of
 * introducing a parallel mapper DTO. Contributions can be filtered before this value is constructed
 * without changing its consumers.
 */
public final class IngressMapperContributions {

  private final List<IntegrationIngressMappers> groups;
  private final List<PayloadIngressMapper<? extends Feature>> mappers;

  private IngressMapperContributions(List<IntegrationIngressMappers> groups) {
    this.groups = List.copyOf(Objects.requireNonNull(groups, "groups"));
    this.mappers = this.groups.stream().flatMap(group -> group.mappers().stream()).toList();
  }

  public static IngressMapperContributions of(List<IntegrationIngressMappers> groups) {
    return new IngressMapperContributions(groups);
  }

  public List<IntegrationIngressMappers> groups() {
    return groups;
  }

  public List<PayloadIngressMapper<? extends Feature>> mappers() {
    return mappers;
  }
}
