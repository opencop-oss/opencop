// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.profile.mapping;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import java.util.List;
import java.util.Set;

public interface ListMappingProfiles {

  /** The latest revision of every unarchived mapper working in one of the directions. */
  List<MapperProfile> list(Set<MapperQualifier> qualifiers);

  /** The latest revision of every mapper working in one of the directions, archived included. */
  List<MapperProfile> listIncludingArchived(Set<MapperQualifier> qualifiers);
}
