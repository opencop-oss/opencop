// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.banner;

import com.opencop.core.api.general.ports.gateway.banner.BannerService;
import com.opencop.core.api.general.technical.model.banner.Banner;
import com.opencop.core.api.general.technical.model.banner.BannerCreate;
import com.opencop.core.api.general.technical.model.banner.BannerPatch;
import com.opencop.core.api.general.technical.model.banner.BannerTranslation;
import com.opencop.core.api.general.technical.model.banner.BannerTranslationCreate;
import com.opencop.core.api.general.technical.model.banner.BannerTranslationPatch;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppBannerService implements BannerService {

  private final BannerRepository bannerRepo;
  private final BannerTranslationRepository translationRepo;
  private final BannerMapper mapper;

  public AppBannerService(
      BannerRepository bannerRepo, BannerTranslationRepository translationRepo) {
    this.bannerRepo = bannerRepo;
    this.translationRepo = translationRepo;
    this.mapper = new BannerMapper();
  }

  // --------------------
  // Banner
  // --------------------

  @Override
  @Transactional(readOnly = true)
  public Banner getBanner(UUID bannerId) throws PersistenceException {
    var entity =
        bannerRepo
            .findById(bannerId)
            .orElseThrow(() -> new PersistenceException("Could not find banner: " + bannerId));
    var translations = this.getAllTranslations(entity.getId());
    return mapper.toModel(entity, translations);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Banner> getAllBanners() {
    return bannerRepo.findAll().stream().map(mapper::toModel).toList();
  }

  @Override
  @Transactional
  public Banner createBanner(BannerCreate create) throws PersistenceException {
    try {
      var entity = new BannerEntity();
      entity.setName(create.name());
      entity.setKind(create.kind());
      entity.setEnabled(create.enabled());
      entity.setStartsAt(create.startsAt());
      entity.setEndsAt(create.endsAt());
      entity.setColor(create.color());
      entity.setTextColor(create.textColor());
      entity.setFormat(create.format());
      entity.setDismissible(create.dismissible());
      entity.setLinkUrl(create.linkUrl());
      entity.setPriority(create.priority());

      var saved = bannerRepo.save(entity);

      for (BannerTranslationCreate translation : create.translations()) {
        this.createTranslation(saved.getId(), translation);
      }

      return mapper.toModel(saved);

    } catch (Exception e) {
      throw new PersistenceException(e.getMessage());
    }
  }

  @Override
  @Transactional
  public Banner patchBanner(UUID bannerId, BannerPatch patch) throws PersistenceException {
    var entity =
        bannerRepo
            .findById(bannerId)
            .orElseThrow(() -> new PersistenceException("Could not find banner: " + bannerId));

    if (patch.name() != null && !patch.name().isEmpty()) entity.setName(patch.name());
    if (patch.enabled() != null) entity.setEnabled(patch.enabled());
    if (patch.startsAt() != null) entity.setStartsAt(patch.startsAt());
    if (patch.endsAt() != null) entity.setEndsAt(patch.endsAt());
    if (patch.color() != null) entity.setColor(patch.color());
    if (patch.textColor() != null) entity.setTextColor(patch.textColor());
    if (patch.format() != null) entity.setFormat(patch.format());
    if (patch.dismissible() != null) entity.setDismissible(patch.dismissible());
    if (patch.linkUrl() != null) entity.setLinkUrl(patch.linkUrl());
    if (patch.priority() != null) entity.setPriority(patch.priority());

    var saved = bannerRepo.save(entity);
    return mapper.toModel(saved);
  }

  @Override
  @Transactional
  public void deleteBanner(UUID bannerId) throws PersistenceException {
    if (!bannerRepo.existsById(bannerId)) {
      throw new PersistenceException("Could not find banner: " + bannerId);
    }
    bannerRepo.deleteById(bannerId);
  }

  // --------------------
  // Translation
  // --------------------

  @Override
  @Transactional(readOnly = true)
  public BannerTranslation getTranslation(UUID bannerId, String locale)
      throws PersistenceException {
    String normalized = normalizeLocaleOrThrow(locale);

    var entity =
        translationRepo
            .findByBannerIdAndLocale(bannerId, normalized)
            .orElseThrow(
                () ->
                    new PersistenceException(
                        "Could not find translation for banner %s and locale %s"
                            .formatted(bannerId, normalized)));

    return mapper.toModel(entity);
  }

  @Override
  @Transactional(readOnly = true)
  public List<BannerTranslation> getTranslations(UUID bannerId) throws PersistenceException {
    // Ensure banner exists (optional, but gives nicer error)
    if (!bannerRepo.existsById(bannerId)) {
      throw new PersistenceException("Could not find banner");
    }
    return getAllTranslations(bannerId);
  }

  private List<BannerTranslation> getAllTranslations(UUID bannerId) {
    return translationRepo.findAllByBannerId(bannerId).stream().map(mapper::toModel).toList();
  }

  @Override
  @Transactional
  public BannerTranslation addTranslation(UUID bannerId, BannerTranslationCreate create)
      throws PersistenceException {
    return this.createTranslation(bannerId, create);
  }

  private BannerTranslation createTranslation(UUID bannerId, BannerTranslationCreate create)
      throws PersistenceException {
    String normalized = normalizeLocaleOrThrow(create.locale());

    var banner =
        bannerRepo
            .findById(bannerId)
            .orElseThrow(() -> new PersistenceException("Could not find banner: " + bannerId));

    // "If locale already exists, do not override"
    if (translationRepo.existsByBannerIdAndLocale(bannerId, normalized)) {
      throw new PersistenceException(
          "Translation already exists for banner %s and locale %s".formatted(bannerId, normalized));
    }

    try {
      var t = new BannerTranslationEntity();
      t.setBanner(banner);
      t.setLocale(normalized);
      t.setContent(create.content());

      var saved = translationRepo.save(t);
      return mapper.toModel(saved);

    } catch (DataIntegrityViolationException _) {
      // In case of race condition, DB unique constraint wins
      throw new PersistenceException(
          "Translation already exists for banner %s and locale %s".formatted(bannerId, normalized));
    } catch (Exception e) {
      throw new PersistenceException(e.getMessage());
    }
  }

  @Override
  @Transactional
  public BannerTranslation patchTranslation(
      UUID bannerId, UUID translationId, BannerTranslationPatch patch) throws PersistenceException {

    var entity =
        translationRepo
            .findById(translationId)
            .orElseThrow(
                () ->
                    new PersistenceException(
                        "Could not find translation for banner %s and translation id %s"
                            .formatted(bannerId, translationId)));

    if (patch.content() != null) {
      entity.setContent(patch.content());
    }

    var saved = translationRepo.save(entity);
    return mapper.toModel(saved);
  }

  @Transactional
  public void deleteTranslation(UUID bannerId, UUID translationId) throws PersistenceException {

    var translation =
        translationRepo
            .findById(translationId)
            .orElseThrow(
                () ->
                    new PersistenceException(
                        "Could not find translation for banner %s and translation %s"
                            .formatted(bannerId, translationId)));

    translationRepo.delete(translation);
  }

  // --------------------
  // Locale validation / normalization
  // --------------------

  private static String normalizeLocaleOrThrow(String raw) throws PersistenceException {
    if (raw == null || raw.isBlank()) {
      throw new PersistenceException("Locale is required");
    }

    var locale = java.util.Locale.forLanguageTag(raw.trim());
    if (locale.getLanguage().isBlank()) {
      throw new PersistenceException("Invalid locale: " + raw);
    }
    return locale.toLanguageTag();
  }
}
