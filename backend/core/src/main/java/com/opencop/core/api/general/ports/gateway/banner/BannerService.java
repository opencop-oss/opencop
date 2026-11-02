// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.banner;

import com.opencop.core.api.general.technical.model.banner.Banner;
import com.opencop.core.api.general.technical.model.banner.BannerCreate;
import com.opencop.core.api.general.technical.model.banner.BannerPatch;
import com.opencop.core.api.general.technical.model.banner.BannerTranslation;
import com.opencop.core.api.general.technical.model.banner.BannerTranslationCreate;
import com.opencop.core.api.general.technical.model.banner.BannerTranslationPatch;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.List;
import java.util.UUID;

public interface BannerService {

  // Banner
  Banner getBanner(UUID bannerId) throws PersistenceException;

  List<Banner> getAllBanners();

  Banner createBanner(BannerCreate create) throws PersistenceException;

  Banner patchBanner(UUID bannerId, BannerPatch patch) throws PersistenceException;

  void deleteBanner(UUID bannerId) throws PersistenceException;

  // Translation
  BannerTranslation getTranslation(UUID bannerId, String locale) throws PersistenceException;

  List<BannerTranslation> getTranslations(UUID bannerId) throws PersistenceException;

  BannerTranslation addTranslation(UUID bannerId, BannerTranslationCreate create)
      throws PersistenceException;

  BannerTranslation patchTranslation(
      UUID bannerId, UUID translationId, BannerTranslationPatch patch) throws PersistenceException;

  void deleteTranslation(UUID bannerId, UUID translationId) throws PersistenceException;
}
