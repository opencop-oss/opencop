// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support.fixture;

import com.opencop.core.api.general.technical.gateway.db.banner.BannerEntity;
import com.opencop.core.api.general.technical.gateway.db.banner.BannerRepository;
import com.opencop.core.api.general.technical.gateway.db.banner.BannerTranslationEntity;
import com.opencop.core.api.general.technical.gateway.db.banner.BannerTranslationRepository;
import com.opencop.core.api.general.technical.model.banner.BannerCreate;
import com.opencop.core.api.general.technical.model.banner.BannerFormat;
import com.opencop.core.api.general.technical.model.banner.BannerKind;
import com.opencop.core.api.general.technical.model.banner.BannerTranslationCreate;
import com.opencop.core.sdk.component.TestFixture;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class BannerFixtures implements TestFixture {

  private final BannerRepository bannerRepository;
  private final BannerTranslationRepository bannerTranslationRepository;

  public BannerFixtures(
      BannerRepository bannerRepository, BannerTranslationRepository bannerTranslationRepository) {
    this.bannerRepository = bannerRepository;
    this.bannerTranslationRepository = bannerTranslationRepository;
  }

  @Override
  public void reset() {
    bannerTranslationRepository.deleteAll();
    bannerRepository.deleteAll();
  }

  public UUID persistBannerWithTranslations(
      String name, List<BannerTranslationCreate> translations) {
    var banner = new BannerEntity();
    banner.setName(name);
    banner.setKind(BannerKind.HEADER);
    banner.setEnabled(true);
    banner.setStartsAt(Instant.parse("2026-03-13T08:00:00Z"));
    banner.setEndsAt(Instant.parse("2026-03-14T08:00:00Z"));
    banner.setColor("#ffcc00");
    banner.setTextColor("#FFFFFF");
    banner.setFormat(BannerFormat.MARKDOWN);
    banner.setDismissible(true);
    banner.setLinkUrl("https://example.com/status");
    banner.setPriority(7);

    var savedBanner = bannerRepository.save(banner);

    for (var translation : translations) {
      var entity = new BannerTranslationEntity();
      entity.setBanner(savedBanner);
      entity.setLocale(Locale.forLanguageTag(translation.locale()).toLanguageTag());
      entity.setContent(translation.content());
      bannerTranslationRepository.save(entity);
    }

    return savedBanner.getId();
  }

  public UUID translationIdForLocale(UUID bannerId, String locale) {
    var normalizedLocale = Locale.forLanguageTag(locale).toLanguageTag();
    return bannerTranslationRepository
        .findByBannerIdAndLocale(bannerId, normalizedLocale)
        .orElseThrow(
            () -> new IllegalStateException("Missing translation for locale " + normalizedLocale))
        .getId();
  }

  public static BannerCreate defaultBannerCreate() {
    return new BannerCreate(
        "System maintenance",
        BannerKind.HEADER,
        true,
        Instant.parse("2026-03-13T08:00:00Z"),
        Instant.parse("2026-03-14T08:00:00Z"),
        "#ffcc00",
        "#FFFFFF",
        BannerFormat.MARKDOWN,
        true,
        "https://example.com/status",
        7,
        List.of(
            new BannerTranslationCreate("en", "Maintenance starts at 08:00 UTC"),
            new BannerTranslationCreate("fr-CA", "La maintenance commence a 08:00 UTC")));
  }
}
