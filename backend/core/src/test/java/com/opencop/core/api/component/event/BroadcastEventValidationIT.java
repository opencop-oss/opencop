// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.api.iam.ports.usecase.identity.UserManager;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.iam.source.AccessVia;
import com.opencop.core.sdk.model.iam.source.LayerInfo;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.ports.gateway.iam.SourceAccessRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;
import org.geojson.Point;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ComponentWebIT(classes = BroadcastEventValidationWebTestApplication.class)
class BroadcastEventValidationIT extends AbstractWebIT {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440181");
  private static final UUID TARGET_LAYER = UUID.fromString("550e8400-e29b-41d4-a716-446655440182");
  private static final UUID OTHER_LAYER = UUID.fromString("550e8400-e29b-41d4-a716-446655440183");
  private static final UUID OBJECT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440184");
  private static final UUID OTHER_OBJECT_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440185");
  private static final Authentication USER_AUTH = TestAuthentications.oidcUser(USER_ID);

  @MockitoBean private MessageBrokerProducer<Feature> producer;
  @MockitoBean private UserManager<InternalUser> userManager;
  @MockitoBean private UserSpecificManager userSpecificManager;
  @MockitoBean private SourceAccessRepository sourceAccessRepository;
  @MockitoBean private PostGisService postGisService;
  @MockitoBean private DataSourceRepository dataSourceRepository;

  @BeforeEach
  void setUpMocks() {
    when(userManager.get(USER_ID)).thenReturn(internalUser(USER_ID));
  }

  @Test
  void broadcastEvent_whenTargetLayerDoesNotMatchFeatureSource_returnsBadRequest()
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/v1/events/broadcast/layer/{targetLayer}", TARGET_LAYER)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .contentType("application/json")
                    .content(
                        objectMapper.writeValueAsBytes(
                            requestFeature(OTHER_LAYER, OBJECT_ID.toString(), new Point(1, 2)))))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(
        "Specified target layer does not match the event source specified", error.message());
    verifyNoInteractions(producer, userSpecificManager);
  }

  @Test
  void patchEvent_whenObjectIdDoesNotMatchFeatureSource_returnsBadRequest() throws Exception {
    var result =
        mockMvc
            .perform(
                patch(
                        "/v1/events/broadcast/layer/{targetLayer}/{objectId}",
                        TARGET_LAYER,
                        OBJECT_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .contentType("application/json")
                    .content(
                        objectMapper.writeValueAsBytes(
                            requestFeature(
                                TARGET_LAYER, OTHER_OBJECT_ID.toString(), new Point(1, 2)))))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("Specified objectId does not match the event objectId specified", error.message());
    verifyNoInteractions(producer, userSpecificManager);
  }

  @Test
  void patchEvent_whenGeometryMissing_returnsBadRequest() throws Exception {
    var result =
        mockMvc
            .perform(
                patch(
                        "/v1/events/broadcast/layer/{targetLayer}/{objectId}",
                        TARGET_LAYER,
                        OBJECT_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .contentType("application/json")
                    .content(
                        objectMapper.writeValueAsBytes(
                            requestFeature(TARGET_LAYER, OBJECT_ID.toString()))))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("Specified event geometry is missing", error.message());
    verifyNoInteractions(producer, userSpecificManager);
  }

  @Test
  void patchEvent_whenLayerEditor_sendsPatchedFeature() throws Exception {
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    TARGET_LAYER,
                    "Ops",
                    LayerRole.EDITOR,
                    LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));
    when(postGisService.getLatestFeature(TARGET_LAYER, OBJECT_ID.toString()))
        .thenReturn(requestFeature(TARGET_LAYER, OBJECT_ID.toString(), new Point(4, 5)));

    mockMvc
        .perform(
            patch("/v1/events/broadcast/layer/{targetLayer}/{objectId}", TARGET_LAYER, OBJECT_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsBytes(
                        requestFeature(TARGET_LAYER, OBJECT_ID.toString(), new Point(1, 2)))))
        .andExpect(status().isOk());
  }

  @Test
  void patchEvent_whenDirectSourceAdmin_isUnauthorized() throws Exception {
    when(userSpecificManager.getUserDirectServers(USER_ID))
        .thenReturn(List.of(new SourceRole(TARGET_LAYER, ServerRole.ADMIN, AccessVia.DIRECT)));
    when(postGisService.getLatestFeature(TARGET_LAYER, OBJECT_ID.toString()))
        .thenReturn(requestFeature(TARGET_LAYER, OBJECT_ID.toString(), new Point(4, 5)));

    mockMvc
        .perform(
            patch("/v1/events/broadcast/layer/{targetLayer}/{objectId}", TARGET_LAYER, OBJECT_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsBytes(
                        requestFeature(TARGET_LAYER, OBJECT_ID.toString(), new Point(1, 2)))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(producer);
  }

  @Test
  void deleteEvent_whenLayerEditor_returnsAccepted() throws Exception {
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    TARGET_LAYER,
                    "Ops",
                    LayerRole.EDITOR,
                    LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));
    when(postGisService.getLatestFeature(TARGET_LAYER, OBJECT_ID.toString()))
        .thenReturn(requestFeature(TARGET_LAYER, OBJECT_ID.toString(), new Point(4, 5)));

    mockMvc
        .perform(
            delete("/v1/events/broadcast/layer/{targetLayer}/{objectId}", TARGET_LAYER, OBJECT_ID)
                .with(csrf())
                .with(authentication(USER_AUTH)))
        .andExpect(status().isAccepted());
  }

  @Test
  void deleteEvent_whenDirectSourceOwner_isUnauthorized() throws Exception {
    when(userSpecificManager.getUserDirectServers(USER_ID))
        .thenReturn(List.of(new SourceRole(TARGET_LAYER, ServerRole.OWNER, AccessVia.DIRECT)));
    when(postGisService.getLatestFeature(TARGET_LAYER, OBJECT_ID.toString()))
        .thenReturn(requestFeature(TARGET_LAYER, OBJECT_ID.toString(), new Point(4, 5)));

    mockMvc
        .perform(
            delete("/v1/events/broadcast/layer/{targetLayer}/{objectId}", TARGET_LAYER, OBJECT_ID)
                .with(csrf())
                .with(authentication(USER_AUTH)))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(producer);
  }

  @Test
  void sourcePatchEvent_whenSourceAdminAndNonUuidObjectId_sendsPatchedFeature() throws Exception {
    var nonUuid = "track-AC-7842";
    when(sourceAccessRepository.userRole(USER_ID, TARGET_LAYER)).thenReturn(ServerRole.ADMIN);
    when(postGisService.getLatestFeature(TARGET_LAYER, nonUuid))
        .thenReturn(requestFeature(TARGET_LAYER, nonUuid, new Point(4, 5)));

    mockMvc
        .perform(
            patch("/v1/events/broadcast/source/{sourceId}/{objectId}", TARGET_LAYER, nonUuid)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsBytes(
                        requestFeature(TARGET_LAYER, nonUuid, new Point(1, 2)))))
        .andExpect(status().isOk());
  }

  @Test
  void sourceDeleteEvent_whenSourceOwnerAndNonUuidObjectId_returnsAccepted() throws Exception {
    var nonUuid = "track-AC-7842";
    when(sourceAccessRepository.userRole(USER_ID, TARGET_LAYER)).thenReturn(ServerRole.OWNER);
    when(postGisService.getLatestFeature(TARGET_LAYER, nonUuid))
        .thenReturn(requestFeature(TARGET_LAYER, nonUuid, new Point(4, 5)));

    mockMvc
        .perform(
            delete("/v1/events/broadcast/source/{sourceId}/{objectId}", TARGET_LAYER, nonUuid)
                .with(csrf())
                .with(authentication(USER_AUTH)))
        .andExpect(status().isAccepted());
  }

  @Test
  void sourcePatchEvent_whenLayerEditorOnly_isUnauthorized() throws Exception {
    var nonUuid = "track-AC-7842";
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    TARGET_LAYER,
                    "Ops",
                    LayerRole.EDITOR,
                    LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));
    when(postGisService.getLatestFeature(TARGET_LAYER, nonUuid))
        .thenReturn(requestFeature(TARGET_LAYER, nonUuid, new Point(4, 5)));

    mockMvc
        .perform(
            patch("/v1/events/broadcast/source/{sourceId}/{objectId}", TARGET_LAYER, nonUuid)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsBytes(
                        requestFeature(TARGET_LAYER, nonUuid, new Point(1, 2)))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(producer);
  }

  @Test
  void deleteEvent_whenPersistedTargetLayerDoesNotMatchPath_returnsBadRequest() throws Exception {
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    TARGET_LAYER,
                    "Ops",
                    LayerRole.EDITOR,
                    LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));
    var persisted = requestFeature(OTHER_LAYER, OBJECT_ID.toString(), new Point(1, 2));
    when(postGisService.getLatestFeature(TARGET_LAYER, OBJECT_ID.toString())).thenReturn(persisted);

    var result =
        mockMvc
            .perform(
                delete(
                        "/v1/events/broadcast/layer/{targetLayer}/{objectId}",
                        TARGET_LAYER,
                        OBJECT_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(
        "Specified target layer does not match the event source specified", error.message());
    verifyNoInteractions(producer);
  }

  private static Feature requestFeature(UUID sourceId, String objectId) {
    return requestFeature(sourceId, objectId, null);
  }

  private static Feature requestFeature(UUID sourceId, String objectId, Point geometry) {
    var now = Instant.now().toEpochMilli();
    var properties = new OpenCopProperties();
    properties.setSource(sourceId);
    properties.setObjectId(objectId);
    properties.setTimestampz(
        new Timestampz(now, now, Instant.now().plusSeconds(60).toEpochMilli()));
    return OpenCopPropertiesParser.writeToFeature(properties, geometry);
  }

  private static InternalUser internalUser(UUID userId) {
    return new InternalUser(
        userId,
        "idp-user",
        "user",
        "issuer",
        "user",
        "user@example.com",
        "Jane",
        "Doe",
        "Jane Doe",
        true,
        false,
        0,
        0,
        Instant.now(),
        Instant.now(),
        null);
  }
}
