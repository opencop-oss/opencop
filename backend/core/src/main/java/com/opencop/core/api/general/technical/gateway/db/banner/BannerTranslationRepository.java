// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.banner;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannerTranslationRepository extends JpaRepository<BannerTranslationEntity, UUID> {

  boolean existsByBannerIdAndLocale(UUID bannerId, String locale);

  Optional<BannerTranslationEntity> findByBannerIdAndLocale(UUID bannerId, String locale);

  List<BannerTranslationEntity> findAllByBannerId(UUID bannerId);
}
