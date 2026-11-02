// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.opencop.core.api.component.support.fixture.MapCaptureFixtures;
import com.opencop.core.api.general.business.model.map.MapCapture;
import com.opencop.core.api.general.business.model.map.MapCapturePatch;
import com.opencop.core.api.general.technical.endpoint.map.MapWebTestApplication;
import com.opencop.core.api.general.technical.gateway.db.map.MapCaptureJpaRepository;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = MapWebTestApplication.class)
class MapCaptureEndpointIT extends AbstractWebIT {

  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440201");
  private static final UUID OTHER_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440202");

  private static final Authentication OWNER_AUTH = TestAuthentications.oidcUser(OWNER_ID);
  private static final Authentication OTHER_USER_AUTH = TestAuthentications.oidcUser(OTHER_USER_ID);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MapCaptureJpaRepository mapCaptureRepository;

  private MapCaptureFixtures mapCaptureFixtures;

  @BeforeEach
  void setUp() {
    mapCaptureFixtures = new MapCaptureFixtures(jdbcTemplate, mapCaptureRepository);
    mapCaptureFixtures.reset();
    mapCaptureFixtures.seedUser(OWNER_ID);
    mapCaptureFixtures.seedUser(OTHER_USER_ID);
  }

  // Auth
  @Test
  void listMapCaptures_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(get("/v1/me/map/captures")).andExpect(status().isUnauthorized());
  }

  @Test
  void createMapCapture_withoutCsrf_returnsForbiddenAndDoesNotPersist() throws Exception {
    mockMvc
        .perform(
            post("/v1/me/map/captures")
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(MapCaptureFixtures.defaultCreate("Operations"))))
        .andExpect(status().isForbidden());

    assertEquals(0L, mapCaptureFixtures.count());
  }

  // Happy path
  @Test
  void ownerCanCreateAndReadBackMapCapture() throws Exception {
    var create = MapCaptureFixtures.defaultCreate("Operations");

    var createResult =
        mockMvc
            .perform(
                post("/v1/me/map/captures")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();

    var created = readResponse(createResult, MapCapture.class);
    assertNotNull(created.id());
    assertEquals("Operations", created.name());
    assertEquals(MapCaptureFixtures.defaultMapView(), created.mapView());
    assertNull(created.createdAt());
    assertNull(created.updatedAt());

    var readResult =
        mockMvc
            .perform(
                get("/v1/me/map/captures/{captureId}", created.id())
                    .with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var readBack = readResponse(readResult, MapCapture.class);
    assertEquals(created.id(), readBack.id());
    assertEquals(created.name(), readBack.name());
    assertEquals(created.mapView(), readBack.mapView());
    assertNotNull(readBack.createdAt());
    assertNotNull(readBack.updatedAt());
  }

  @Test
  void ownerCanPatchMapCapture() throws Exception {
    var captureId =
        mapCaptureFixtures.persistMapCapture(
            OWNER_ID, "Before", MapCaptureFixtures.defaultMapView());
    var patch =
        new MapCapturePatch(
            Optional.of("After"), Optional.of(MapCaptureFixtures.alternateMapView()));

    mockMvc
        .perform(
            patch("/v1/me/map/captures/{captureId}", captureId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(patch)))
        .andExpect(status().isOk());

    var result =
        mockMvc
            .perform(
                get("/v1/me/map/captures/{captureId}", captureId).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var capture = readResponse(result, MapCapture.class);
    assertEquals(captureId, capture.id());
    assertEquals("After", capture.name());
    assertEquals(MapCaptureFixtures.alternateMapView(), capture.mapView());
  }

  @Test
  void ownerCanDeleteMapCapture() throws Exception {
    var captureId =
        mapCaptureFixtures.persistMapCapture(
            OWNER_ID, "Delete me", MapCaptureFixtures.defaultMapView());

    mockMvc
        .perform(
            delete("/v1/me/map/captures/{captureId}", captureId)
                .with(csrf())
                .with(authentication(OWNER_AUTH)))
        .andExpect(status().isNoContent());

    var listResult =
        mockMvc
            .perform(get("/v1/me/map/captures").with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    assertEquals(0, readResponse(listResult, new TypeReference<List<MapCapture>>() {}).size());
  }

  // Access edge cases
  @Test
  void listOnlyReturnsCapturesOwnedByAuthenticatedUser() throws Exception {
    mapCaptureFixtures.persistMapCapture(
        OWNER_ID, "Owner capture", MapCaptureFixtures.defaultMapView());
    mapCaptureFixtures.persistMapCapture(
        OTHER_USER_ID, "Other capture", MapCaptureFixtures.alternateMapView());

    var result =
        mockMvc
            .perform(get("/v1/me/map/captures").with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var captures = readResponse(result, new TypeReference<List<MapCapture>>() {});
    assertEquals(1, captures.size());
    assertEquals("Owner capture", captures.getFirst().name());
    assertEquals(MapCaptureFixtures.defaultMapView(), captures.getFirst().mapView());
  }

  @Test
  void otherUserCanReadCaptureByIdThroughMeEndpoint() throws Exception {
    var captureId =
        mapCaptureFixtures.persistMapCapture(
            OWNER_ID, "Shared readback", MapCaptureFixtures.defaultMapView());

    var result =
        mockMvc
            .perform(
                get("/v1/me/map/captures/{captureId}", captureId)
                    .with(authentication(OTHER_USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var capture = readResponse(result, MapCapture.class);
    assertEquals(captureId, capture.id());
    assertEquals("Shared readback", capture.name());
  }

  @Test
  void otherUserCannotPatchOwnedCapture_returnsBadRequest() throws Exception {
    var captureId =
        mapCaptureFixtures.persistMapCapture(
            OWNER_ID, "Before", MapCaptureFixtures.defaultMapView());
    var patch = new MapCapturePatch(Optional.of("After"), Optional.empty());

    var result =
        mockMvc
            .perform(
                patch("/v1/me/map/captures/{captureId}", captureId)
                    .with(csrf())
                    .with(authentication(OTHER_USER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(patch)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("Tried patching a capture that the user does not own", error.message());

    var readResult =
        mockMvc
            .perform(
                get("/v1/me/map/captures/{captureId}", captureId).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals("Before", readResponse(readResult, MapCapture.class).name());
  }

  @Test
  void otherUserCannotDeleteOwnedCapture_returnsNotFound() throws Exception {
    var captureId =
        mapCaptureFixtures.persistMapCapture(
            OWNER_ID, "Keep me", MapCaptureFixtures.defaultMapView());

    var result =
        mockMvc
            .perform(
                delete("/v1/me/map/captures/{captureId}", captureId)
                    .with(csrf())
                    .with(authentication(OTHER_USER_AUTH)))
            .andExpect(status().isNotFound())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("Tried deleting a capture that the user does not own", error.message());
    assertEquals(1L, mapCaptureFixtures.count());
  }

  // Validation failures
  @Test
  void createMapCapture_withBlankName_returnsBadRequest() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/v1/me/map/captures")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(MapCaptureFixtures.defaultCreate(" "))))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("Map capture name and mapView are required", error.message());
    assertEquals(0L, mapCaptureFixtures.count());
  }

  @Test
  void createMapCapture_withoutMapView_returnsBadRequest() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/v1/me/map/captures")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new MapCapture(null, "Operations", null, null, null))))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("Map capture name and mapView are required", error.message());
    assertEquals(0L, mapCaptureFixtures.count());
  }

  // Missing resource
  @Test
  void getMapCapture_whenMissing_returnsNotFound() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/v1/map/captures/{captureId}", UUID.randomUUID())
                    .with(authentication(OWNER_AUTH)))
            .andExpect(status().isNotFound())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("Not Found", error.message());
  }

  @Test
  void patchMapCapture_whenMissing_returnsNotFound() throws Exception {
    var result =
        mockMvc
            .perform(
                patch("/v1/me/map/captures/{captureId}", UUID.randomUUID())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new MapCapturePatch(Optional.of("After"), Optional.empty()))))
            .andExpect(status().isNotFound())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertTrue(error.message().startsWith("Could not find map capture: "));
  }

  @Test
  void deleteMapCapture_whenMissing_returnsNotFound() throws Exception {
    var result =
        mockMvc
            .perform(
                delete("/v1/me/map/captures/{captureId}", UUID.randomUUID())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH)))
            .andExpect(status().isNotFound())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertTrue(error.message().startsWith("Could not find map capture: "));
  }

  // Persistence/readback
  @Test
  void patchUpdatesPersistedCaptureReadback() throws Exception {
    var captureId =
        mapCaptureFixtures.persistMapCapture(
            OWNER_ID, "Original", MapCaptureFixtures.defaultMapView());

    mockMvc
        .perform(
            patch("/v1/me/map/captures/{captureId}", captureId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new MapCapturePatch(Optional.of("Persisted"), Optional.empty()))))
        .andExpect(status().isOk());

    var directResult =
        mockMvc
            .perform(
                get("/v1/map/captures/{captureId}", captureId).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var capture = readResponse(directResult, MapCapture.class);
    assertEquals("Persisted", capture.name());
    assertEquals(MapCaptureFixtures.defaultMapView(), capture.mapView());
  }
}
