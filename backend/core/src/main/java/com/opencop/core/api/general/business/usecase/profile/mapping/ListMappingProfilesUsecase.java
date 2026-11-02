// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.profile.mapping;

import com.opencop.core.api.general.ports.usecase.profile.mapping.ListMappingProfiles;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import java.util.List;
import java.util.Set;

public class ListMappingProfilesUsecase implements ListMappingProfiles {

  private final MapperProfileService service;

  public ListMappingProfilesUsecase(MapperProfileService service) {
    this.service = service;
  }

  @Override
  public List<MapperProfile> list(Set<MapperQualifier> qualifiers) {
    return service.getAllMapperProfiles(qualifiers);
  }

  @Override
  public List<MapperProfile> listIncludingArchived(Set<MapperQualifier> qualifiers) {
    return service.getAllMapperProfilesAdmin(qualifiers);
  }
}
