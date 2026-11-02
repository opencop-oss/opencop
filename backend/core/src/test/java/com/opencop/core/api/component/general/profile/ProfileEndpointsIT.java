// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.ProfileFixtures;
import com.opencop.core.api.component.support.fixture.UserFixtures;
import com.opencop.core.api.general.business.model.profile.MapperProfileFullMetaResponse;
import com.opencop.core.api.general.business.model.profile.MapperProfileResponse;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapperBuilder;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = ProfileWebTestApplication.class)
class ProfileEndpointsIT extends AbstractWebIT {

  private static final UUID MAPPER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440203");
  private static final UUID READ_ONLY_MAPPER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440207");
  private static final UUID DUPLICATE_MAPPER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440208");
  private static final UUID UNKNOWN_MAPPER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440209");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440206");
  private static final UUID CLAIMED_INTEGRATION_ID =
      UUID.fromString("00000000-0000-4000-8000-0000000000aa");
  // Fixed rather than random: these ids reach the database as created_by/updated_by, which carry
  // foreign keys to opencop_user, so each must name a row seeded in setUp().
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-4466554402a1");
  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-4466554402a2");
  private static final Authentication USER_AUTH = TestAuthentications.oidcUser(USER_ID);
  private static final Authentication ADMIN_AUTH = TestAuthentications.oidcAdmin(ADMIN_ID);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MapperProfileService mapperProfileService;

  private ProfileFixtures profileFixtures;

  @BeforeEach
  void setUp() {
    profileFixtures = new ProfileFixtures(jdbcTemplate, mapperProfileService);
    profileFixtures.reset();
    UserFixtures.seedUser(jdbcTemplate, USER_ID);
    UserFixtures.seedUser(jdbcTemplate, ADMIN_ID);
  }

  /**
   * Mappers attributed to a seeded user outlive the test that wrote them, and any later suite
   * clearing {@code opencop_user} would trip their foreign key. Clearing them here keeps that
   * cleanup local to the suite that created the reference.
   */
  @AfterEach
  void tearDown() {
    profileFixtures.reset();
  }

  @Test
  void mapperProfileEndpoints_supportPublicReadAndAdminCreateUpdate() throws Exception {
    profileFixtures.persistMapper(MAPPER_ID, "Base Mapper", true, USER_ID);

    var publicResult =
        mockMvc
            .perform(
                get("/v1/profile/mapping/{mappingId}", MAPPER_ID).with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var publicMeta = readResponse(publicResult, MapperProfileResponse.class);
    assertEquals(1, publicMeta.version());
    assertEquals("Base Mapper", publicMeta.name());
    assertEquals(MapperQualifier.INGRESS, publicMeta.qualifier());
    // Audit actors are deliberately visible to any authenticated caller, not admin-only.
    assertEquals(USER_ID, publicMeta.createdBy());
    assertNotNull(publicMeta.createdAt());

    mockMvc
        .perform(
            get("/v1/admin/profile/mapping/{mappingId}", MAPPER_ID).with(authentication(USER_AUTH)))
        .andExpect(status().isForbidden());

    var adminFullResult =
        mockMvc
            .perform(
                get("/v1/admin/profile/mapping/{mappingId}", MAPPER_ID)
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var fullMeta = readResponse(adminFullResult, MapperProfileFullMetaResponse.class);
    assertEquals("sample.xml", fullMeta.filename());
    assertNotNull(fullMeta.payload());

    var createdId = UUID.fromString("550e8400-e29b-41d4-a716-446655440205");
    var createMeta = ProfileFixtures.mapperFullMeta(createdId, "Created Mapper", true);
    mockMvc
        .perform(
            post("/v1/admin/profile/mapping")
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createMeta)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/v1/profile/mapping/" + createdId));

    var updateMeta = ProfileFixtures.mapperFullMeta(MAPPER_ID, "Base Mapper", true);
    mockMvc
        .perform(
            patch("/v1/admin/profile/mapping")
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(updateMeta)))
        .andExpect(status().isNoContent());

    assertEquals(2, profileFixtures.mapperVersionCount(MAPPER_ID));
  }

  @Test
  void pluginLifecycleStateControlsListingsWhileManualMutationsStayRejected() throws Exception {
    profileFixtures.persistPluginMapper(MAPPER_ID, "Plugin Mapper");
    var rowBefore = profileFixtures.loadMapperRow(MAPPER_ID);

    mockMvc
        .perform(
            patch("/v1/admin/profile/mapping")
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        ProfileFixtures.mapperFullMeta(MAPPER_ID, "Changed Plugin Mapper", true))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            delete("/v1/admin/profile/mapping/{id}", MAPPER_ID)
                .with(csrf())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());
    var rowAfterRejectedMutations = profileFixtures.loadMapperRow(MAPPER_ID);
    assertEquals(rowBefore.get("name"), rowAfterRejectedMutations.get("name"));
    assertEquals(rowBefore.get("description"), rowAfterRejectedMutations.get("description"));
    assertEquals(rowBefore.get("archived"), rowAfterRejectedMutations.get("archived"));
    assertEquals(1, profileFixtures.mapperVersionCount(MAPPER_ID));

    jdbcTemplate.update("UPDATE profiles.data_mapper SET archived = true WHERE id = ?", MAPPER_ID);

    var activeResult =
        mockMvc
            .perform(get("/v1/profile/mapping").with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(
        readResponse(activeResult, new TypeReference<List<MapperProfileResponse>>() {}).stream()
            .noneMatch(mapper -> mapper.id().equals(MAPPER_ID)));

    var adminResult =
        mockMvc
            .perform(get("/v1/admin/profile/mapping").with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var archived =
        readResponse(adminResult, new TypeReference<List<MapperProfileResponse>>() {}).stream()
            .filter(mapper -> mapper.id().equals(MAPPER_ID))
            .findFirst()
            .orElseThrow();
    assertTrue(archived.archived());
  }

  @Test
  void mapperCreateAndUpdateCannotClaimAnOwningIntegration() throws Exception {
    var requested =
        MapperProfile.builder()
            .withId(MAPPER_ID)
            .withName("User Mapper")
            .withDescription("User-authored mapper")
            .withEditable(true)
            .withIntegrationId(CLAIMED_INTEGRATION_ID)
            .withVersion(1)
            .withDynamicMapper(
                ProfileFixtures.mapperFullMeta(MAPPER_ID, "template", true)
                    .profile()
                    .dynamicMapper())
            .withArchived(false)
            .build();
    var createMeta =
        new MapperProfileFullMeta(
            requested, "sample.xml", "sample".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            post("/v1/admin/profile/mapping")
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createMeta)))
        .andExpect(status().isCreated());

    assertNull(owningIntegration(MAPPER_ID));

    mockMvc
        .perform(
            patch("/v1/admin/profile/mapping")
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createMeta)))
        .andExpect(status().isNoContent());

    assertEquals(2, profileFixtures.mapperVersionCount(MAPPER_ID));
    assertNull(owningIntegration(MAPPER_ID));
  }

  @Test
  void mapperVersions_carryAuditFieldsAndKeepCreatorFromTheFirstVersion() throws Exception {
    profileFixtures.persistMapper(MAPPER_ID, "Base Mapper", true, USER_ID);

    // Two updates, so "inherit from the first version" is distinguishable from "inherit from the
    // previous version" — a single bump would satisfy both readings.
    for (var name : List.of("Base Mapper v2", "Base Mapper v3")) {
      mockMvc
          .perform(
              patch("/v1/admin/profile/mapping")
                  .with(csrf())
                  .with(authentication(ADMIN_AUTH))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsBytes(
                          ProfileFixtures.mapperFullMeta(MAPPER_ID, name, true))))
          .andExpect(status().isNoContent());
    }

    var result =
        mockMvc
            .perform(
                get("/v1/admin/profile/mapping/{mappingId}/versions", MAPPER_ID)
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var versions = readResponse(result, new TypeReference<List<MapperProfileResponse>>() {});

    assertEquals(3, versions.size());
    var first = versions.stream().filter(v -> v.version() == 1).findFirst().orElseThrow();
    var latest = versions.stream().filter(v -> v.version() == 3).findFirst().orElseThrow();

    assertEquals(USER_ID, first.createdBy());
    assertEquals(USER_ID, latest.createdBy(), "creator must survive later edits by someone else");
    assertEquals(first.createdAt(), latest.createdAt());
    assertEquals(ADMIN_ID, latest.updatedBy());
    assertNotNull(latest.updatedAt());
  }

  @Test
  void mapperProfilesAdmin_listCarriesAuditFields() throws Exception {
    profileFixtures.persistMapper(MAPPER_ID, "Base Mapper", true, USER_ID);

    var result =
        mockMvc
            .perform(get("/v1/admin/profile/mapping").with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var profiles = readResponse(result, new TypeReference<List<MapperProfileResponse>>() {});

    var mapper = profiles.stream().filter(p -> MAPPER_ID.equals(p.id())).findFirst().orElseThrow();
    assertEquals(USER_ID, mapper.createdBy());
    assertNotNull(mapper.createdAt());
  }

  @Test
  void mapperProfileEndpoints_unknownMapperReads_returnNotFound() throws Exception {
    var publicResult =
        mockMvc
            .perform(
                get("/v1/profile/mapping/{mappingId}", UNKNOWN_MAPPER_ID)
                    .with(authentication(USER_AUTH)))
            .andExpect(status().isNotFound())
            .andReturn();
    assertEquals(
        "Mapper not found", readResponse(publicResult, GlobalErrorResponse.class).message());

    var adminResult =
        mockMvc
            .perform(
                get("/v1/admin/profile/mapping/{mappingId}", UNKNOWN_MAPPER_ID)
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isNotFound())
            .andReturn();
    assertEquals(
        "Mapper not found", readResponse(adminResult, GlobalErrorResponse.class).message());

    var versionsResult =
        mockMvc
            .perform(
                get("/v1/admin/profile/mapping/{mappingId}/versions", UNKNOWN_MAPPER_ID)
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isNotFound())
            .andReturn();
    assertEquals(
        "Mapper not found", readResponse(versionsResult, GlobalErrorResponse.class).message());
  }

  @Test
  void mapperProfileEndpoints_reportEveryExistingMapperAsAnIngressMapper() throws Exception {
    profileFixtures.persistPluginMapper(MAPPER_ID, "Bidirectional Mapper");
    profileFixtures.persistPluginMapper(READ_ONLY_MAPPER_ID, "Read-only Mapper");
    profileFixtures.persistPluginMapper(DUPLICATE_MAPPER_ID, "Duplicate Mapper");
    profileFixtures.persistPluginMapper(UNKNOWN_MAPPER_ID, "Unknown Mapper");

    var result =
        mockMvc
            .perform(get("/v1/profile/mapping").with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var profiles = readResponse(result, new TypeReference<List<MapperProfileResponse>>() {});
    var qualifiers =
        profiles.stream()
            .collect(Collectors.toMap(MapperProfileResponse::id, MapperProfileResponse::qualifier));

    assertEquals(MapperQualifier.INGRESS, qualifiers.get(MAPPER_ID));
    assertEquals(MapperQualifier.INGRESS, qualifiers.get(READ_ONLY_MAPPER_ID));
    assertEquals(MapperQualifier.INGRESS, qualifiers.get(DUPLICATE_MAPPER_ID));
    assertEquals(MapperQualifier.INGRESS, qualifiers.get(UNKNOWN_MAPPER_ID));
  }

  @Test
  void mapperListings_showIngressMappersUnlessEgressMappersAreAskedFor() throws Exception {
    profileFixtures.persistPluginMapper(MAPPER_ID, "Ingress Mapper");
    profileFixtures.persistPluginMapper(
        READ_ONLY_MAPPER_ID, "Egress Mapper", MapperQualifier.EGRESS);

    var defaultListing =
        readResponse(
            mockMvc
                .perform(get("/v1/profile/mapping").with(authentication(USER_AUTH)))
                .andExpect(status().isOk())
                .andReturn(),
            new TypeReference<List<MapperProfileResponse>>() {});
    var egressListing =
        readResponse(
            mockMvc
                .perform(
                    get("/v1/admin/profile/mapping")
                        .param("qualifier", "EGRESS")
                        .with(authentication(ADMIN_AUTH)))
                .andExpect(status().isOk())
                .andReturn(),
            new TypeReference<List<MapperProfileResponse>>() {});

    assertThat(defaultListing).extracting(MapperProfileResponse::id).contains(MAPPER_ID);
    assertThat(defaultListing)
        .extracting(MapperProfileResponse::id)
        .doesNotContain(READ_ONLY_MAPPER_ID);
    assertThat(egressListing)
        .extracting(MapperProfileResponse::id, MapperProfileResponse::qualifier)
        .containsExactly(tuple(READ_ONLY_MAPPER_ID, MapperQualifier.EGRESS));
  }

  @ParameterizedTest
  @ValueSource(strings = {"INGRESS,EGRESS", "EGRESS"})
  void mapperListings_returnEveryRequestedDirectionInOneRequest(String secondParameter)
      throws Exception {
    profileFixtures.persistPluginMapper(MAPPER_ID, "Ingress Mapper");
    profileFixtures.persistPluginMapper(
        READ_ONLY_MAPPER_ID, "Egress Mapper", MapperQualifier.EGRESS);

    // Both forms ask for both directions: a comma-separated value, or the parameter repeated.
    var request = get("/v1/profile/mapping").with(authentication(USER_AUTH));
    request =
        "EGRESS".equals(secondParameter)
            ? request.param("qualifier", "INGRESS").param("qualifier", secondParameter)
            : request.param("qualifier", secondParameter);
    var listing =
        readResponse(
            mockMvc.perform(request).andExpect(status().isOk()).andReturn(),
            new TypeReference<List<MapperProfileResponse>>() {});

    assertThat(listing)
        .extracting(MapperProfileResponse::id, MapperProfileResponse::qualifier)
        .contains(
            tuple(MAPPER_ID, MapperQualifier.INGRESS),
            tuple(READ_ONLY_MAPPER_ID, MapperQualifier.EGRESS));
  }

  @Test
  void deletingAnEgressMapperIsRejected() throws Exception {
    profileFixtures.persistPluginMapper(MAPPER_ID, "Egress Mapper", MapperQualifier.EGRESS);

    mockMvc
        .perform(
            delete("/v1/admin/profile/mapping/{id}", MAPPER_ID)
                .with(csrf())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());

    assertEquals(false, profileFixtures.loadMapperRow(MAPPER_ID).get("archived"));
  }

  @Test
  void previewMapperProfile_returnsMappedRecordsAndDisabledProfileErrors() throws Exception {
    var mapper = defaultPreviewMapper();
    var payload =
        "<Tracks><Track><Lat>39.9526</Lat><Lon>-75.1652</Lon></Track></Tracks>"
            .getBytes(StandardCharsets.UTF_8);

    var previewResult =
        mockMvc
            .perform(
                post("/v1/profile/mapping/preview")
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new com.opencop.core.api.general.technical.endpoint.profile.mapper
                                .PreviewMappingRequest(mapper, payload))))
            .andExpect(status().isOk())
            .andReturn();
    var preview = readResponse(previewResult, MappingResponse.class);
    assertEquals(mapper.id(), preview.profileId());
    assertEquals(1, preview.records().size());
    assertFalse(preview.hasErrors());

    var disabledMapper =
        new DynamicMapper(
            mapper.id(), false, mapper.formatConfig(), mapper.recordSelector(), mapper.rules());
    var disabledResult =
        mockMvc
            .perform(
                post("/v1/profile/mapping/preview")
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new com.opencop.core.api.general.technical.endpoint.profile.mapper
                                .PreviewMappingRequest(disabledMapper, payload))))
            .andExpect(status().isOk())
            .andReturn();
    var disabledPreview = readResponse(disabledResult, MappingResponse.class);
    assertEquals(1, disabledPreview.errors().size());
    assertEquals("PROFILE_DISABLED", disabledPreview.errors().getFirst().code());
    assertEquals("Mapper profile is disabled", disabledPreview.errors().getFirst().message());
    assertEquals(0, disabledPreview.records().size());
    assertEquals(0, disabledPreview.warnings().size());
    assertArrayEquals(payload, disabledPreview.errors().getFirst().payload());
  }

  @Test
  void mapperDelete_happyPath_returns204AndSetsArchivedAndAttributesArchiver() throws Exception {
    profileFixtures.persistMapper(MAPPER_ID, "Base Mapper", true, ADMIN_ID);

    mockMvc
        .perform(
            delete("/v1/admin/profile/mapping/{id}", MAPPER_ID)
                .with(csrf())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isNoContent());

    var row = profileFixtures.loadMapperRow(MAPPER_ID);
    assertEquals(true, row.get("archived"));
    // Asserted against the row rather than the usecase: only a real write proves the actor is
    // bound into the SQL and survives the trip to the column.
    assertEquals(ADMIN_ID, row.get("updated_by"));
    assertEquals(ADMIN_ID, row.get("created_by"));
    assertNotNull(row.get("updated_at"));
  }

  @Test
  void mapperDelete_archivesEveryVersionButStampsTheArchiverOnlyOnTheHead() throws Exception {
    // Three versions with distinguishable editors, so "every version reports the archiver" is
    // distinguishable from "only the head version does."
    var secondEditor = UUID.randomUUID();
    var thirdEditor = UUID.randomUUID();
    UserFixtures.seedUser(jdbcTemplate, secondEditor);
    UserFixtures.seedUser(jdbcTemplate, thirdEditor);

    profileFixtures.persistMapper(MAPPER_ID, "Base Mapper v1", true, USER_ID);
    mapperProfileService.createNewVersion(
        ProfileFixtures.mapperFullMeta(MAPPER_ID, "Base Mapper v2", true), secondEditor);
    mapperProfileService.createNewVersion(
        ProfileFixtures.mapperFullMeta(MAPPER_ID, "Base Mapper v3", true), thirdEditor);

    var beforeV1 = profileFixtures.loadMapperVersionRow(MAPPER_ID, 1);
    var beforeV2 = profileFixtures.loadMapperVersionRow(MAPPER_ID, 2);

    mockMvc
        .perform(
            delete("/v1/admin/profile/mapping/{id}", MAPPER_ID)
                .with(csrf())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isNoContent());

    var afterV1 = profileFixtures.loadMapperVersionRow(MAPPER_ID, 1);
    var afterV2 = profileFixtures.loadMapperVersionRow(MAPPER_ID, 2);
    var afterV3 = profileFixtures.loadMapperVersionRow(MAPPER_ID, 3);

    assertEquals(true, afterV1.get("archived"));
    assertEquals(true, afterV2.get("archived"));
    assertEquals(true, afterV3.get("archived"));

    assertEquals(
        beforeV1.get("updated_by"),
        afterV1.get("updated_by"),
        "version 1's own editor must survive archiving");
    assertEquals(beforeV1.get("updated_at"), afterV1.get("updated_at"));
    assertEquals(
        beforeV2.get("updated_by"),
        afterV2.get("updated_by"),
        "version 2's own editor must survive archiving");
    assertEquals(beforeV2.get("updated_at"), afterV2.get("updated_at"));

    assertEquals(
        ADMIN_ID, afterV3.get("updated_by"), "only the head version records who archived it");
    assertNotNull(afterV3.get("updated_at"));

    // The postcondition must also hold through the versions endpoint, not just raw SQL.
    var result =
        mockMvc
            .perform(
                get("/v1/admin/profile/mapping/{mappingId}/versions", MAPPER_ID)
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var versions = readResponse(result, new TypeReference<List<MapperProfileResponse>>() {});
    assertEquals(3, versions.size());
    assertTrue(versions.stream().allMatch(MapperProfileResponse::archived));
    var headVersion = versions.stream().filter(v -> v.version() == 3).findFirst().orElseThrow();
    assertEquals(ADMIN_ID, headVersion.updatedBy());
  }

  @Test
  void mapperDelete_whenAConnectionDecodesWithIt_returns409() throws Exception {
    profileFixtures.persistMapper(MAPPER_ID, "Base Mapper", true, USER_ID);
    profileFixtures.persistDataSource(SOURCE_ID, MAPPER_ID);

    mockMvc
        .perform(
            delete("/v1/admin/profile/mapping/{id}", MAPPER_ID)
                .with(csrf())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isConflict());

    // The dependency guard must reject the whole write, not just the response — no row changes.
    var row = profileFixtures.loadMapperRow(MAPPER_ID);
    assertEquals(false, row.get("archived"));
    assertNull(row.get("updated_at"));
    assertNull(row.get("updated_by"));
  }

  @Test
  void mapperDelete_whenAlreadyArchived_returns400() throws Exception {
    profileFixtures.persistMapper(MAPPER_ID, "Base Mapper", true);
    profileFixtures.setMapperArchived(MAPPER_ID);

    mockMvc
        .perform(
            delete("/v1/admin/profile/mapping/{id}", MAPPER_ID)
                .with(csrf())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void mapperDelete_whenNotAdmin_returns403() throws Exception {
    profileFixtures.persistMapper(MAPPER_ID, "Base Mapper", true);

    mockMvc
        .perform(
            delete("/v1/admin/profile/mapping/{id}", MAPPER_ID)
                .with(csrf())
                .with(authentication(USER_AUTH)))
        .andExpect(status().isForbidden());
  }

  private static DynamicMapper defaultPreviewMapper() {
    return DynamicMapperBuilder.xml()
        .recordXPath("//Track")
        .addRule(
            com.opencop.core.sdk.model.mapper.rules.MappingRuleBuilder.rule()
                .xpath("Lat/text()")
                .onMissing(com.opencop.core.sdk.model.mapper.OnMissing.ERROR)
                .target(com.opencop.core.sdk.model.mapper.rules.TargetField.GEOMETRY_LAT)
                .build())
        .addRule(
            com.opencop.core.sdk.model.mapper.rules.MappingRuleBuilder.rule()
                .xpath("Lon/text()")
                .onMissing(com.opencop.core.sdk.model.mapper.OnMissing.ERROR)
                .target(com.opencop.core.sdk.model.mapper.rules.TargetField.GEOMETRY_LON)
                .build())
        .build();
  }

  private UUID owningIntegration(UUID mapperId) {
    return jdbcTemplate.queryForObject(
        "SELECT integration_id FROM profiles.mapper WHERE id = ?", UUID.class, mapperId);
  }
}
