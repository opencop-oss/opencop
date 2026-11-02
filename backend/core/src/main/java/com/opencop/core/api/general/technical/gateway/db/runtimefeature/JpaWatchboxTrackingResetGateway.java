// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.runtimefeature;

import com.opencop.core.api.general.ports.gateway.runtimefeature.WatchboxTrackingResetGateway;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxJpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWatchboxTrackingResetGateway implements WatchboxTrackingResetGateway {

  private final WatchboxJpaRepository watchboxRepository;

  public JpaWatchboxTrackingResetGateway(WatchboxJpaRepository watchboxRepository) {
    this.watchboxRepository = watchboxRepository;
  }

  @Override
  public int bumpAllActiveConfigEpochs() {
    return watchboxRepository.bumpAllActiveConfigEpochs();
  }
}
