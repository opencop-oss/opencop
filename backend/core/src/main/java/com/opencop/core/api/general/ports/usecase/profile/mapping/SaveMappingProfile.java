// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.profile.mapping;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;
import java.util.UUID;

public interface SaveMappingProfile {

  UUID create(MapperProfileFullMeta meta, Caller createdBy);

  /**
   * Saves a new version, unless the save would change nothing: an unchanged save writes neither a
   * version nor a history row.
   */
  void update(MapperProfileFullMeta meta, Caller updatedBy);
}
