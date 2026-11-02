// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.profile;

import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted mapper facts required to reconcile contribution ownership and availability.
 *
 * @param integrationId the integration contributing the mapper; {@code null} when none does
 */
public record MapperCatalogProfile(
    UUID id,
    int version,
    String name,
    String description,
    boolean editable,
    boolean mappingDefinitionPresent,
    boolean archived,
    MapperQualifier qualifier,
    UUID integrationId) {

  public MapperCatalogProfile {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(qualifier, "qualifier");
  }
}
