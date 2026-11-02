// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.factory.profile;

import com.opencop.core.api.general.business.model.profile.MapperProfileFullMetaResponse;
import com.opencop.core.api.general.business.model.profile.MapperProfileResponse;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;

public final class MapperProfileResponseFactory {

  public MapperProfileResponse create(MapperProfile profile) {
    return new MapperProfileResponse(
        profile.id(),
        profile.name(),
        profile.description(),
        profile.editable(),
        profile.version(),
        profile.dynamicMapper(),
        profile.archived(),
        profile.qualifier(),
        profile.createdAt(),
        profile.createdBy(),
        profile.updatedAt(),
        profile.updatedBy(),
        profile.lastEventAt());
  }

  public MapperProfileFullMetaResponse create(MapperProfileFullMeta profile) {
    return new MapperProfileFullMetaResponse(
        create(profile.profile()), profile.filename(), profile.payload());
  }
}
