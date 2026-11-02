// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.banner;

import com.opencop.core.api.general.technical.model.banner.BannerKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannerRepository extends JpaRepository<BannerEntity, UUID> {

  List<BannerEntity> findAllByKind(BannerKind kind);

  Optional<BannerEntity> findFirstByKindAndEnabledOrderByPriorityDescCreatedAtDesc(
      BannerKind kind, boolean enabled);
}
