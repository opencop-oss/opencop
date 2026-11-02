// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.banner;

import com.opencop.core.api.general.technical.model.banner.Banner;
import com.opencop.core.api.general.technical.model.banner.BannerTranslation;
import java.util.List;

public final class BannerMapper {

  public Banner toModel(BannerEntity e) {
    return new Banner(
        e.getId(),
        e.getName(),
        e.getKind(),
        e.isEnabled(),
        e.getStartsAt(),
        e.getEndsAt(),
        e.getColor(),
        e.getTextColor(),
        e.getFormat(),
        e.isDismissible(),
        e.getLinkUrl(),
        e.getPriority(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        null);
  }

  public Banner toModel(BannerEntity e, List<BannerTranslation> translations) {
    return new Banner(
        e.getId(),
        e.getName(),
        e.getKind(),
        e.isEnabled(),
        e.getStartsAt(),
        e.getEndsAt(),
        e.getColor(),
        e.getTextColor(),
        e.getFormat(),
        e.isDismissible(),
        e.getLinkUrl(),
        e.getPriority(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        translations);
  }

  public BannerTranslation toModel(BannerTranslationEntity e) {
    return new BannerTranslation(
        e.getId(),
        e.getBanner().getId(),
        e.getLocale(),
        e.getContent(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }
}
