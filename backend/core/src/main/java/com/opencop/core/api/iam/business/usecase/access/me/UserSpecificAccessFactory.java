// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.business.usecase.access.me;

import com.opencop.core.api.iam.business.usecase.access.layer.EffectiveLayerAccessService;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerUserRepository;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.ports.gateway.iam.SourceAccessRepository;

public class UserSpecificAccessFactory {

  private UserSpecificAccessFactory() {}

  public static UserSpecificManager createUserSpecificManager(
      final ServerLayerRepository serverLayerRepository,
      final ServerUserRepository serverUserRepository,
      final EffectiveLayerAccessService effectiveLayerAccessService,
      final SourceAccessRepository sourceAccessRepository) {
    return new UserSpecificAccessManager(
        serverLayerRepository,
        serverUserRepository,
        effectiveLayerAccessService,
        sourceAccessRepository);
  }
}
