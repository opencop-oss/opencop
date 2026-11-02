// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.profile;

import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import java.util.Objects;
import java.util.UUID;

/**
 * The catalog identity of a mapper an integration contributes from code, whatever its direction.
 *
 * @param integrationId the integration contributing the mapper, which owns its catalog entry
 */
public record PluginMapperContribution(
    UUID id,
    String name,
    String description,
    int version,
    MapperQualifier qualifier,
    UUID integrationId) {

  public PluginMapperContribution {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(qualifier, "qualifier");
    Objects.requireNonNull(integrationId, "integrationId");
  }
}
