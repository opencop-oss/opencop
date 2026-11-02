// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.banner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.banner.BannerService;
import com.opencop.core.api.general.technical.model.banner.Banner;
import com.opencop.core.api.general.technical.model.banner.BannerCreate;
import com.opencop.core.api.general.technical.model.banner.BannerFormat;
import com.opencop.core.api.general.technical.model.banner.BannerKind;
import com.opencop.core.api.general.technical.model.banner.BannerPatch;
import com.opencop.core.api.general.technical.model.banner.BannerTranslation;
import com.opencop.core.api.general.technical.model.banner.BannerTranslationCreate;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BannersEndpointTest {

  @Mock private BannerService bannerService;

  private BannersEndpoint endpoint;

  @BeforeEach
  void setUp() {
    endpoint = new BannersEndpoint(bannerService);
  }

  @Test
  void getBanner_returnsBanner() throws PersistenceException {
    var bannerId = UUID.randomUUID();
    var banner = banner(bannerId);
    when(bannerService.getBanner(bannerId)).thenReturn(banner);

    var result = endpoint.getBanner(bannerId);

    assertEquals(banner, result);
    verify(bannerService).getBanner(bannerId);
  }

  @Test
  void getBanner_whenMissing_throwsNotFound() throws PersistenceException {
    var bannerId = UUID.randomUUID();
    when(bannerService.getBanner(bannerId)).thenThrow(new PersistenceException("Missing banner"));

    var error = assertThrows(GlobalOpenCopException.class, () -> endpoint.getBanner(bannerId));

    assertEquals(HttpStatus.NOT_FOUND, error.getHttpStatus());
    assertEquals("Missing banner", error.getMessage());
  }

  @Test
  void getAllBanners_returnsList() {
    var banners = List.of(banner(UUID.randomUUID()));
    when(bannerService.getAllBanners()).thenReturn(banners);

    var result = endpoint.getAllBanners();

    assertEquals(banners, result);
    verify(bannerService).getAllBanners();
  }

  @Test
  void getTranslation_whenMissing_throwsNotFound() throws PersistenceException {
    var bannerId = UUID.randomUUID();
    when(bannerService.getTranslation(bannerId, "en"))
        .thenThrow(new PersistenceException("Missing translation"));

    var error =
        assertThrows(GlobalOpenCopException.class, () -> endpoint.getTranslation(bannerId, "en"));

    assertEquals(HttpStatus.NOT_FOUND, error.getHttpStatus());
    assertEquals("Missing translation", error.getMessage());
  }

  @Test
  void createBanner_whenMissingTranslations_throwsBadRequest() throws PersistenceException {
    var create =
        new BannerCreate(
            "name",
            BannerKind.HEADER,
            true,
            null,
            null,
            "#fff",
            "#FFFFFF",
            BannerFormat.PLAIN,
            false,
            null,
            1,
            List.of());

    var error = assertThrows(GlobalOpenCopException.class, () -> endpoint.createBanner(create));

    assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
    assertEquals("Provide at least the english locale", error.getMessage());
    verify(bannerService, never()).createBanner(create);
  }

  @Test
  void createBanner_whenLocaleUnsupported_throwsBadRequest() throws PersistenceException {
    var create =
        new BannerCreate(
            "name",
            BannerKind.HEADER,
            true,
            null,
            null,
            "#fff",
            "#FFFFFF",
            BannerFormat.PLAIN,
            false,
            null,
            1,
            List.of(new BannerTranslationCreate("INVALID", "content")));

    var error = assertThrows(GlobalOpenCopException.class, () -> endpoint.createBanner(create));

    assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
    assertEquals("Provide at least the english locale", error.getMessage());
    verify(bannerService, never()).createBanner(create);
  }

  @Test
  void createBanner_whenEnglishMissing_throwsBadRequest() throws PersistenceException {
    var create =
        new BannerCreate(
            "name",
            BannerKind.HEADER,
            true,
            null,
            null,
            "#fff",
            "#FFFFFF",
            BannerFormat.PLAIN,
            false,
            null,
            1,
            List.of(new BannerTranslationCreate("fr", "content")));

    var error = assertThrows(GlobalOpenCopException.class, () -> endpoint.createBanner(create));

    assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
    assertEquals("Provide at least the english locale", error.getMessage());
    verify(bannerService, never()).createBanner(create);
  }

  @Test
  void createBanner_returnsCreatedLocation() throws PersistenceException {
    var bannerId = UUID.randomUUID();
    var create =
        new BannerCreate(
            "name",
            BannerKind.HEADER,
            true,
            null,
            null,
            "#fff",
            "#FFFFFF",
            BannerFormat.PLAIN,
            false,
            null,
            1,
            List.of(new BannerTranslationCreate("en", "content")));
    when(bannerService.createBanner(create)).thenReturn(banner(bannerId));

    var response = endpoint.createBanner(create);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals(URI.create("/v1/banners/" + bannerId), response.getHeaders().getLocation());
    verify(bannerService).createBanner(create);
  }

  @Test
  void patchBanner_whenPersistenceFails_throwsBadRequest() throws PersistenceException {
    var bannerId = UUID.randomUUID();
    var patch =
        new BannerPatch(
            "new", true, null, null, "#fff", "#FFFFFF", BannerFormat.PLAIN, true, null, 2);
    when(bannerService.patchBanner(bannerId, patch))
        .thenThrow(new PersistenceException("Invalid patch"));

    var error =
        assertThrows(GlobalOpenCopException.class, () -> endpoint.patchBanner(bannerId, patch));

    assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
    assertEquals("Invalid patch", error.getMessage());
  }

  @Test
  void addTranslation_returnsCreatedLocation() throws PersistenceException {
    var bannerId = UUID.randomUUID();
    var create = new BannerTranslationCreate("en", "content");
    var created =
        new BannerTranslation(
            UUID.randomUUID(), bannerId, "en", "content", Instant.now(), Instant.now());
    when(bannerService.addTranslation(bannerId, create)).thenReturn(created);

    var response = endpoint.addTranslation(bannerId, create);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals(
        URI.create("/v1/banners/" + bannerId + "/translations/en"),
        response.getHeaders().getLocation());
    verify(bannerService).addTranslation(bannerId, create);
  }

  @Test
  void deleteTranslation_whenPersistenceFails_throwsBadRequest() throws PersistenceException {
    var bannerId = UUID.randomUUID();
    var translationId = UUID.randomUUID();
    doThrow(new PersistenceException("Missing translation"))
        .when(bannerService)
        .deleteTranslation(bannerId, translationId);

    var error =
        assertThrows(
            GlobalOpenCopException.class,
            () -> endpoint.deleteTranslation(bannerId, translationId));

    assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
    assertEquals("Missing translation", error.getMessage());
  }

  private static Banner banner(UUID id) {
    return new Banner(
        id,
        "name",
        BannerKind.HEADER,
        true,
        null,
        null,
        "#fff",
        "#FFFFFF",
        BannerFormat.PLAIN,
        false,
        null,
        1,
        Instant.parse("2024-01-01T00:00:00Z"),
        Instant.parse("2024-01-02T00:00:00Z"),
        List.of());
  }
}
