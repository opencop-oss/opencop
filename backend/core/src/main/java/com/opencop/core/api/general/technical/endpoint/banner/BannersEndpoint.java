// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.banner;

import com.opencop.core.api.general.ports.gateway.banner.BannerService;
import com.opencop.core.api.general.technical.model.banner.Banner;
import com.opencop.core.api.general.technical.model.banner.BannerCreate;
import com.opencop.core.api.general.technical.model.banner.BannerPatch;
import com.opencop.core.api.general.technical.model.banner.BannerTranslation;
import com.opencop.core.api.general.technical.model.banner.BannerTranslationCreate;
import com.opencop.core.api.general.technical.model.banner.BannerTranslationPatch;
import com.opencop.core.sdk.helper.LocaleSupport;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class BannersEndpoint {

  private final BannerService bannerService;

  BannersEndpoint(final BannerService bannerService) {
    this.bannerService = bannerService;
  }

  // =========================================================
  // ===================== PUBLIC GET ========================
  // =========================================================

  // Get banner metadata by ID
  @GetMapping("/banners/{bannerId}")
  public Banner getBanner(@PathVariable UUID bannerId) {
    try {
      return bannerService.getBanner(bannerId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  @GetMapping("/banners")
  public List<Banner> getAllBanners() {
    return bannerService.getAllBanners();
  }

  @GetMapping("/banners/{bannerId}/translations/{locale}")
  public BannerTranslation getTranslation(
      @PathVariable UUID bannerId, @PathVariable String locale) {

    try {
      return bannerService.getTranslation(bannerId, locale);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  @GetMapping("/banners/{bannerId}/translations")
  public List<BannerTranslation> getAllBannerTranslations(@PathVariable UUID bannerId) {
    try {
      return bannerService.getTranslations(bannerId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  // =========================================================
  // ===================== ADMIN ONLY ========================
  // =========================================================

  @PreAuthorize("hasRole('admin')")
  @PostMapping("/admin/banners")
  public ResponseEntity<Void> createBanner(@RequestBody BannerCreate create) {
    try {
      if (create.translations() == null || create.translations().isEmpty()) {
        throw new GlobalOpenCopException(
            "Provide at least the english locale", HttpStatus.BAD_REQUEST);
      }
      boolean hasEnglish = false;
      for (var translation : create.translations()) {
        if (!LocaleSupport.isSupported(translation.locale())) {
          throw new GlobalOpenCopException(
              "Locale not supported: " + translation.locale(), HttpStatus.BAD_REQUEST);
        }
        if ("en".equals(translation.locale())) {
          hasEnglish = true;
        }
      }
      if (!hasEnglish) {
        throw new GlobalOpenCopException(
            "Provide at least the english locale", HttpStatus.BAD_REQUEST);
      }
      var created = bannerService.createBanner(create);
      return ResponseEntity.created(URI.create("/v1/banners/" + created.id())).build();
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @PreAuthorize("hasRole('admin')")
  @PatchMapping("/admin/banners/{bannerId}")
  public ResponseEntity<Void> patchBanner(
      @PathVariable UUID bannerId, @RequestBody BannerPatch patch) {

    try {
      bannerService.patchBanner(bannerId, patch);
      return ResponseEntity.ok().build();
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @PreAuthorize("hasRole('admin')")
  @DeleteMapping("/admin/banners/{bannerId}")
  public ResponseEntity<Void> deleteBanner(@PathVariable UUID bannerId) {
    try {
      bannerService.deleteBanner(bannerId);
      return ResponseEntity.noContent().build();
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @PreAuthorize("hasRole('admin')")
  @PostMapping("/admin/banners/{bannerId}/translations")
  public ResponseEntity<Void> addTranslation(
      @PathVariable UUID bannerId, @RequestBody BannerTranslationCreate create) {

    if (!LocaleSupport.isSupported(create.locale())) {
      throw new GlobalOpenCopException(
          "Locale '%s' is not supported".formatted(create.locale()), HttpStatus.BAD_REQUEST);
    }

    try {
      var created = bannerService.addTranslation(bannerId, create);

      return ResponseEntity.created(
              URI.create("/v1/banners/" + bannerId + "/translations/" + created.locale()))
          .build();

    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @PreAuthorize("hasRole('admin')")
  @PatchMapping("/admin/banners/{bannerId}/translations/{translationId}")
  public ResponseEntity<Void> patchTranslation(
      @PathVariable UUID bannerId,
      @PathVariable UUID translationId,
      @RequestBody BannerTranslationPatch patch) {
    try {
      bannerService.patchTranslation(bannerId, translationId, patch);
      return ResponseEntity.ok().build();

    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @PreAuthorize("hasRole('admin')")
  @DeleteMapping("/admin/banners/{bannerId}/translations/{translationId}")
  public ResponseEntity<Void> deleteTranslation(
      @PathVariable UUID bannerId, @PathVariable UUID translationId) {
    try {
      bannerService.deleteTranslation(bannerId, translationId);
      return ResponseEntity.noContent().build();
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }
}
