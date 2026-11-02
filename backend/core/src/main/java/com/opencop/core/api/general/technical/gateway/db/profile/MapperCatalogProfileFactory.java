// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.profile;

import com.opencop.core.api.general.business.model.profile.MapperCatalogProfile;

/** Maps persisted catalog state into the mapper domain model used by reconciliation. */
final class MapperCatalogProfileFactory {

  private MapperCatalogProfileFactory() {}

  static MapperCatalogProfile create(
      MapperCatalogEntryEntity mapper, MapperIdentityEntity identity) {
    return new MapperCatalogProfile(
        mapper.mapperId(),
        mapper.version(),
        mapper.name(),
        mapper.description(),
        mapper.editable(),
        mapper.mappingDefinitionPresent(),
        mapper.archived(),
        identity.qualifier(),
        identity.integrationId());
  }
}
