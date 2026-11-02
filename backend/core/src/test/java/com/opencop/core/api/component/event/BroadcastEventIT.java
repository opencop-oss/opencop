// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.event.business.usecase.event.LayerBroadcastEventHandler;
import com.opencop.core.api.event.ports.business.usecase.EventExceptionCodes;
import com.opencop.core.api.event.ports.business.usecase.OpencopEventException;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import java.time.Instant;
import java.util.UUID;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ComponentWebIT(classes = EventWebTestApplication.class)
class BroadcastEventIT extends AbstractWebIT {

  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440999");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
  private static final String OBJECT_ID = "event-123";
  private static final String JSON_CONTENT_TYPE = "application/json";
  private static final Authentication ADMIN_AUTH = TestAuthentications.oidcAdmin(ADMIN_ID);

  @MockitoBean private LayerBroadcastEventHandler eventBroadcaster;

  // Happy path
  @Test
  void nonAdminCanBroadcastEvent() throws Exception {
    var userId = UUID.randomUUID();
    var auth = TestAuthentications.oidcUser(userId);
    var requestFeature = requestFeature(SOURCE_ID, OBJECT_ID);
    when(eventBroadcaster.create(eq(userId), eq(SOURCE_ID), any(Feature.class)))
        .thenAnswer(invocation -> invocation.getArgument(2));

    mockMvc
        .perform(
            post("/v1/events/broadcast/layer/{targetLayer}", SOURCE_ID)
                .with(csrf())
                .with(authentication(auth))
                .contentType(JSON_CONTENT_TYPE)
                .content(objectMapper.writeValueAsBytes(requestFeature)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.properties.opencop.source").value(SOURCE_ID.toString()))
        .andExpect(jsonPath("$.properties.opencop.objectId").value(OBJECT_ID));

    var featureCaptor = ArgumentCaptor.forClass(Feature.class);
    verify(eventBroadcaster).create(eq(userId), eq(SOURCE_ID), featureCaptor.capture());
    var properties = OpenCopPropertiesParser.readFromFeature(featureCaptor.getValue());
    assertEquals(OBJECT_ID, properties.getObjectId());
    assertEquals(SOURCE_ID, properties.getSource());
  }

  @Test
  void adminCanBroadcastEvent() throws Exception {
    var requestFeature = requestFeature(SOURCE_ID, OBJECT_ID);
    when(eventBroadcaster.create(eq(ADMIN_ID), eq(SOURCE_ID), any(Feature.class)))
        .thenAnswer(invocation -> invocation.getArgument(2));

    mockMvc
        .perform(
            post("/v1/events/broadcast/layer/{targetLayer}", SOURCE_ID)
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(JSON_CONTENT_TYPE)
                .content(objectMapper.writeValueAsBytes(requestFeature)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.properties.opencop.objectId").value(OBJECT_ID));

    var featureCaptor = ArgumentCaptor.forClass(Feature.class);
    verify(eventBroadcaster).create(eq(ADMIN_ID), eq(SOURCE_ID), featureCaptor.capture());
    var properties = OpenCopPropertiesParser.readFromFeature(featureCaptor.getValue());
    assertEquals(OBJECT_ID, properties.getObjectId());
  }

  @Test
  void patchEvent_delegatesToPatch() throws Exception {
    var userId = UUID.randomUUID();
    var auth = TestAuthentications.oidcUser(userId);
    var requestFeature = requestFeature(SOURCE_ID, OBJECT_ID);
    var objectId = UUID.fromString("550e8400-e29b-41d4-a716-446655440777");
    when(eventBroadcaster.patch(eq(userId), eq(SOURCE_ID), eq(objectId), any(Feature.class)))
        .thenAnswer(invocation -> invocation.getArgument(3));

    mockMvc
        .perform(
            patch("/v1/events/broadcast/layer/{targetLayer}/{objectId}", SOURCE_ID, objectId)
                .with(csrf())
                .with(authentication(auth))
                .contentType(JSON_CONTENT_TYPE)
                .content(objectMapper.writeValueAsBytes(requestFeature)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.properties.opencop.source").value(SOURCE_ID.toString()))
        .andExpect(jsonPath("$.properties.opencop.objectId").value(OBJECT_ID));

    verify(eventBroadcaster).patch(eq(userId), eq(SOURCE_ID), eq(objectId), any(Feature.class));
  }

  @Test
  void deleteEvent_delegatesToDelete() throws Exception {
    var userId = UUID.randomUUID();
    var auth = TestAuthentications.oidcUser(userId);
    var objectId = UUID.fromString("550e8400-e29b-41d4-a716-446655440778");

    mockMvc
        .perform(
            delete("/v1/events/broadcast/layer/{targetLayer}/{objectId}", SOURCE_ID, objectId)
                .with(csrf())
                .with(authentication(auth)))
        .andExpect(status().isAccepted());

    verify(eventBroadcaster).delete(eq(userId), eq(SOURCE_ID), eq(objectId));
  }

  // Validation failures
  @Test
  void broadcastEvent_whenBroadcasterRejectsIncompleteRequest_returnsBadRequest() throws Exception {
    var userId = UUID.randomUUID();
    var auth = TestAuthentications.oidcUser(userId);
    var requestFeature = requestFeature(null, OBJECT_ID);
    when(eventBroadcaster.create(eq(userId), eq(SOURCE_ID), any(Feature.class)))
        .thenThrow(new OpencopEventException(EventExceptionCodes.MISSING_INFORMATION, BAD_REQUEST));

    var result =
        mockMvc
            .perform(
                post("/v1/events/broadcast/layer/{targetLayer}", SOURCE_ID)
                    .with(csrf())
                    .with(authentication(auth))
                    .contentType(JSON_CONTENT_TYPE)
                    .content(objectMapper.writeValueAsBytes(requestFeature)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("CORE-1000", error.code());
    assertEquals("Request is not complete", error.message());
    verify(eventBroadcaster).create(eq(userId), eq(SOURCE_ID), any(Feature.class));
  }

  // Auth
  @Test
  void broadcastEvent_whenUnauthenticated_returnsUnauthorized() throws Exception {
    var requestFeature = requestFeature(SOURCE_ID, null);

    mockMvc
        .perform(
            post("/v1/events/broadcast/layer/{targetLayer}", SOURCE_ID)
                .with(csrf())
                .contentType(JSON_CONTENT_TYPE)
                .content(objectMapper.writeValueAsBytes(requestFeature)))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(eventBroadcaster);
  }

  private static Feature requestFeature(UUID source, String objectId) {
    var now = Instant.now().toEpochMilli();
    var properties = new OpenCopProperties();
    properties.setSource(source);
    properties.setObjectId(objectId);
    properties.setTimestampz(
        new Timestampz(now, now, Instant.now().plusSeconds(60).toEpochMilli()));
    return OpenCopPropertiesParser.writeToFeature(properties, null);
  }
}
