// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.factory.profile;

import com.opencop.core.api.general.business.model.profile.PluginMapperContribution;
import com.opencop.core.sdk.business.model.profile.MapperProfile;

/** Builds the ordinary catalog profile for a newly accepted plugin mapper contribution. */
public final class PluginMapperProfileFactory {

  private PluginMapperProfileFactory() {}

  public static MapperProfile create(PluginMapperContribution contribution) {
    return MapperProfile.builder()
        .withId(contribution.id())
        .withName(contribution.name())
        .withDescription(contribution.description())
        .withEditable(false)
        .withQualifier(contribution.qualifier())
        .withVersion(contribution.version())
        .withArchived(false)
        .build();
  }
}
