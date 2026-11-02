// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.banner;

import static com.opencop.core.api.component.support.assertion.BannerAssertions.assertBannerMetadata;
import static com.opencop.core.api.component.support.assertion.BannerAssertions.assertBannerSummary;
import static com.opencop.core.api.component.support.assertion.BannerAssertions.assertTranslation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.BannerFixtures;
import com.opencop.core.api.general.technical.gateway.db.banner.BannerRepository;
import com.opencop.core.api.general.technical.gateway.db.banner.BannerTranslationRepository;
import com.opencop.core.api.general.technical.model.banner.Banner;
import com.opencop.core.api.general.technical.model.banner.BannerCreate;
import com.opencop.core.api.general.technical.model.banner.BannerFormat;
import com.opencop.core.api.general.technical.model.banner.BannerKind;
import com.opencop.core.api.general.technical.model.banner.BannerPatch;
import com.opencop.core.api.general.technical.model.banner.BannerTranslation;
import com.opencop.core.api.general.technical.model.banner.BannerTranslationCreate;
import com.opencop.core.api.general.technical.model.banner.BannerTranslationPatch;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = BannerWebTestApplication.class)
class BannersEndpointIT extends AbstractWebIT {

  private static final Authentication ADMIN_AUTH = TestAuthentications.oidcAdmin();
  private static final Authentication USER_AUTH = TestAuthentications.oidcUser();

  @Autowired private BannerRepository bannerRepository;
  @Autowired private BannerTranslationRepository bannerTranslationRepository;

  private BannerFixtures bannerFixtures;

  @BeforeEach
  void setUpData() {
    bannerFixtures = new BannerFixtures(bannerRepository, bannerTranslationRepository);
    bannerFixtures.reset();
  }

  // Happy path
  @Test
  void adminCanCreateBanner() throws Exception {
    var create = BannerFixtures.defaultBannerCreate();

    var result =
        mockMvc
            .perform(
                post("/v1/admin/banners")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();

    var bannerId = uuidFromLocation(result);

    var bannerResult =
        mockMvc
            .perform(get("/v1/banners/{bannerId}", bannerId).with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var banner = readResponse(bannerResult, Banner.class);
    assertBannerSummary(banner, bannerId, "System maintenance", "HEADER", 2);
    assertEquals(true, banner.enabled());
    assertEquals("MARKDOWN", banner.format().name());
  }

  @Test
  void adminCanPatchBannerMetadata() throws Exception {
    var bannerId = createBannerAsAdmin(BannerFixtures.defaultBannerCreate());
    var patch =
        new BannerPatch(
            "System maintenance update",
            false,
            Instant.parse("2026-03-13T09:00:00Z"),
            Instant.parse("2026-03-14T10:00:00Z"),
            "#000000",
            "#FFFFFF",
            BannerFormat.HTML,
            false,
            "https://example.com/incidents",
            3);

    mockMvc
        .perform(
            patch("/v1/admin/banners/{bannerId}", bannerId)
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(patch)))
        .andExpect(status().isOk());

    var bannerResult =
        mockMvc
            .perform(get("/v1/banners/{bannerId}", bannerId).with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var banner = readResponse(bannerResult, Banner.class);
    assertBannerMetadata(
        banner,
        "System maintenance update",
        false,
        "2026-03-13T09:00:00Z",
        "2026-03-14T10:00:00Z",
        "#000000",
        "#FFFFFF",
        "HTML",
        false,
        "https://example.com/incidents",
        3);
  }

  @Test
  void adminCanManageTranslations() throws Exception {
    var bannerId = createBannerAsAdmin(BannerFixtures.defaultBannerCreate());
    var frenchTranslationId = bannerFixtures.translationIdForLocale(bannerId, "fr-CA");

    mockMvc
        .perform(
            post("/v1/admin/banners/{bannerId}/translations", bannerId)
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new BannerTranslationCreate(
                            "es-mx", "El mantenimiento comienza a las 08:00 UTC"))))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/v1/banners/" + bannerId + "/translations/es-MX"));

    mockMvc
        .perform(
            patch(
                    "/v1/admin/banners/{bannerId}/translations/{translationId}",
                    bannerId,
                    frenchTranslationId)
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new BannerTranslationPatch("La maintenance est repoussee a 09:00 UTC"))))
        .andExpect(status().isOk());

    var spanishResult =
        mockMvc
            .perform(
                get("/v1/banners/{bannerId}/translations/{locale}", bannerId, "es-mx")
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var spanish = readResponse(spanishResult, BannerTranslation.class);
    assertTranslation(spanish, "es-MX", "El mantenimiento comienza a las 08:00 UTC");

    var frenchResult =
        mockMvc
            .perform(
                get("/v1/banners/{bannerId}/translations/{locale}", bannerId, "fr-ca")
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var french = readResponse(frenchResult, BannerTranslation.class);
    assertTranslation(
        french, frenchTranslationId, "fr-CA", "La maintenance est repoussee a 09:00 UTC");

    var spanishTranslationId = bannerFixtures.translationIdForLocale(bannerId, "es-MX");

    mockMvc
        .perform(
            delete(
                    "/v1/admin/banners/{bannerId}/translations/{translationId}",
                    bannerId,
                    spanishTranslationId)
                .with(csrf())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isNoContent());

    var translationsResult =
        mockMvc
            .perform(
                get("/v1/banners/{bannerId}/translations", bannerId)
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(
        2,
        readResponse(translationsResult, new TypeReference<List<BannerTranslation>>() {}).size());
  }

  @Test
  void adminCanDeleteBanner() throws Exception {
    var bannerId = createBannerAsAdmin(BannerFixtures.defaultBannerCreate());

    mockMvc
        .perform(
            delete("/v1/admin/banners/{bannerId}", bannerId)
                .with(csrf())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(get("/v1/banners").with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(0, readResponse(result, new TypeReference<List<Banner>>() {}).size());
  }

  @Test
  void nonAdminCanReadBannersAndTranslations() throws Exception {
    var bannerId =
        bannerFixtures.persistBannerWithTranslations(
            "System maintenance",
            List.of(
                new BannerTranslationCreate("en", "Maintenance starts at 08:00 UTC"),
                new BannerTranslationCreate("fr-CA", "La maintenance commence a 08:00 UTC")));
    var frenchTranslationId = bannerFixtures.translationIdForLocale(bannerId, "fr-CA");

    var allBannersResult =
        mockMvc
            .perform(get("/v1/banners").with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var banners = readResponse(allBannersResult, new TypeReference<List<Banner>>() {});
    assertBannerSummary(banners.getFirst(), bannerId, "System maintenance", "HEADER", 0);

    var bannerResult =
        mockMvc
            .perform(get("/v1/banners/{bannerId}", bannerId).with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var banner = readResponse(bannerResult, Banner.class);
    assertBannerSummary(banner, bannerId, "System maintenance", "HEADER", 2);

    var translationResult =
        mockMvc
            .perform(
                get("/v1/banners/{bannerId}/translations/{locale}", bannerId, "fr-ca")
                    .with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var translation = readResponse(translationResult, BannerTranslation.class);
    assertTranslation(
        translation, frenchTranslationId, "fr-CA", "La maintenance commence a 08:00 UTC");

    var translationsResult =
        mockMvc
            .perform(
                get("/v1/banners/{bannerId}/translations", bannerId)
                    .with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(
        2,
        readResponse(translationsResult, new TypeReference<List<BannerTranslation>>() {}).size());
  }

  // Forbidden mutation
  @Test
  void nonAdminCannotUseAdminEndpoints() throws Exception {
    var bannerId =
        bannerFixtures.persistBannerWithTranslations(
            "System maintenance",
            List.of(new BannerTranslationCreate("en", "Maintenance starts at 08:00 UTC")));
    var translationId = bannerFixtures.translationIdForLocale(bannerId, "en");

    mockMvc
        .perform(
            post("/v1/admin/banners")
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(BannerFixtures.defaultBannerCreate())))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            patch("/v1/admin/banners/{bannerId}", bannerId)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new BannerPatch(
                            "updated", null, null, null, null, null, null, null, null, null))))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/v1/admin/banners/{bannerId}/translations", bannerId)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new BannerTranslationCreate("es", "Hola"))))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            patch(
                    "/v1/admin/banners/{bannerId}/translations/{translationId}",
                    bannerId,
                    translationId)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new BannerTranslationPatch("Updated"))))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            delete(
                    "/v1/admin/banners/{bannerId}/translations/{translationId}",
                    bannerId,
                    translationId)
                .with(csrf())
                .with(authentication(USER_AUTH)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            delete("/v1/admin/banners/{bannerId}", bannerId)
                .with(csrf())
                .with(authentication(USER_AUTH)))
        .andExpect(status().isForbidden());
  }

  // Validation failures
  @Test
  void adminCreateRequiresEnglishTranslation() throws Exception {
    var create =
        new BannerCreate(
            "French only",
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
            List.of(new BannerTranslationCreate("fr-CA", "La maintenance commence a 08:00 UTC")));

    var result =
        mockMvc
            .perform(
                post("/v1/admin/banners")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertEquals(
        "Provide at least the english locale",
        readResponse(result, GlobalErrorResponse.class).message());
  }

  @Test
  void adminCreateRejectsUnsupportedLocale() throws Exception {
    var create =
        new BannerCreate(
            "Invalid locale",
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
                new BannerTranslationCreate("not_a_locale", "Invalid translation")));

    var result =
        mockMvc
            .perform(
                post("/v1/admin/banners")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertEquals(
        "Locale not supported: not_a_locale",
        readResponse(result, GlobalErrorResponse.class).message());
  }

  @Test
  void adminCannotCreateDuplicateTranslationForSameLocale() throws Exception {
    var bannerId = createBannerAsAdmin(BannerFixtures.defaultBannerCreate());

    var result =
        mockMvc
            .perform(
                post("/v1/admin/banners/{bannerId}/translations", bannerId)
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new BannerTranslationCreate("fr-ca", "Duplicate French translation"))))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertEquals(
        "Translation already exists for banner " + bannerId + " and locale fr-CA",
        readResponse(result, GlobalErrorResponse.class).message());
  }

  @Test
  void adminTranslationValidationRejectsUnsupportedLocale() throws Exception {
    var bannerId = createBannerAsAdmin(BannerFixtures.defaultBannerCreate());

    var result =
        mockMvc
            .perform(
                post("/v1/admin/banners/{bannerId}/translations", bannerId)
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new BannerTranslationCreate("bad_locale", "Should fail"))))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertEquals(
        "Locale 'bad_locale' is not supported",
        readResponse(result, GlobalErrorResponse.class).message());
  }

  // Missing resource
  @Test
  void adminGetsBadRequestForMissingResources() throws Exception {
    var missingBannerId = UUID.randomUUID();
    var missingTranslationId = UUID.randomUUID();

    mockMvc
        .perform(
            patch("/v1/admin/banners/{bannerId}", missingBannerId)
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new BannerPatch(
                            "updated", null, null, null, null, null, null, null, null, null))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            delete(
                    "/v1/admin/banners/{bannerId}/translations/{translationId}",
                    missingBannerId,
                    missingTranslationId)
                .with(csrf())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void nonAdminGetsNotFoundForMissingBannerAndTranslations() throws Exception {
    var missingBannerId = UUID.randomUUID();

    mockMvc
        .perform(get("/v1/banners/{bannerId}", missingBannerId).with(authentication(USER_AUTH)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            get("/v1/banners/{bannerId}/translations", missingBannerId)
                .with(authentication(USER_AUTH)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            get("/v1/banners/{bannerId}/translations/{locale}", missingBannerId, "en")
                .with(authentication(USER_AUTH)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            get("/v1/banners/{bannerId}/translations/{locale}", missingBannerId, "fr-CA")
                .with(authentication(USER_AUTH)))
        .andExpect(status().isNotFound());
  }

  // Persistence/readback
  @Test
  void localizationNormalizesLocaleForCreationAndLookup() throws Exception {
    var bannerId = createBannerAsAdmin(BannerFixtures.defaultBannerCreate());

    mockMvc
        .perform(
            post("/v1/admin/banners/{bannerId}/translations", bannerId)
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new BannerTranslationCreate("pt-br", "Manutencao as 08:00 UTC"))))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/v1/banners/" + bannerId + "/translations/pt-BR"));

    var result =
        mockMvc
            .perform(
                get("/v1/banners/{bannerId}/translations/{locale}", bannerId, "pt-br")
                    .with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var translation = readResponse(result, BannerTranslation.class);
    assertTranslation(translation, "pt-BR", "Manutencao as 08:00 UTC");
  }

  private UUID createBannerAsAdmin(BannerCreate create) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/v1/admin/banners")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isCreated())
            .andReturn();

    return uuidFromLocation(result);
  }
}
