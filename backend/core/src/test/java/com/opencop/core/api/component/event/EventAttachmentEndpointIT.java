// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.EventFixtures;
import com.opencop.core.api.component.support.fixture.UserFixtures;
import com.opencop.core.api.event.technical.endpoint.EventAttachmentEndpoint;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import com.opencop.core.api.general.ports.usecase.media.DeleteMedia;
import com.opencop.core.api.general.ports.usecase.media.MediaView;
import com.opencop.core.api.general.ports.usecase.media.PrepareAttachmentStream;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.ports.usecase.media.UploadMedia;
import com.opencop.core.api.general.technical.config.MediaMtxProperties;
import com.opencop.core.api.general.technical.gateway.authorization.AuthorizationService;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaContentType;
import com.opencop.core.sdk.model.media.MediaRecord;
import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.model.media.StreamKind;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import com.opencop.core.sdk.technical.audit.AuthzActorAttributionFilter;
import com.opencop.core.sdk.technical.audit.AuthzAuditRequestAttributes;
import com.opencop.core.sdk.technical.audit.AuthzDeniedBackstopFilter;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = EventWebTestApplication.class)
class EventAttachmentEndpointIT extends AbstractWebIT {

  private static final UUID SOURCE_A = UUID.fromString("550e8400-e29b-41d4-a716-446655440140");
  private static final UUID SOURCE_B = UUID.fromString("550e8400-e29b-41d4-a716-446655440141");
  private static final String OBJECT_ID = "event-123";
  private static final String OTHER_OBJECT_ID = "event-456";
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440142");
  private static final String AUTHZ_DENIED_TOTAL = "opencop_authz_denied_total";
  private static final String STREAM_AUTHZ_TOTAL = "opencop_stream_authz_total";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private AuthzDeniedBackstopFilter backstopFilter;
  @Autowired private MediaMtxProperties mediaMtxProperties;

  @Autowired
  private FilterRegistrationBean<AuthzActorAttributionFilter> attributionFilterRegistration;

  @MockitoBean private UserSpecificManager userSpecificManager;
  @MockitoBean private DataSourceRepository dataSourceRepository;
  @MockitoBean private UploadMedia uploadMedia;
  @MockitoBean private RetrieveMedia retrieveMedia;
  @MockitoBean private DeleteMedia deleteMedia;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private PrepareAttachmentStream prepareAttachmentStream;
  @MockitoBean private MediaUrlRepository mediaUrlRepository;

  private EventFixtures eventFixtures;

  @BeforeEach
  void setUp() {
    // Filters run in registration order, and springSecurity() registers its
    // delegate at apply() time — so this mirrors production: backstop FIRST
    // (order -110, outside the -100 security chain), then the chain, then the
    // attribution filter (order -90, inside, sees the populated context on
    // the way in). The backstop reads only stashed attributes on the unwind,
    // exactly as in production.
    this.mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .addFilters(backstopFilter)
            .apply(springSecurity())
            .addFilters(attributionFilterRegistration.getFilter())
            .build();
    jdbcTemplate.update("DELETE FROM event_attachment");
    jdbcTemplate.update("DELETE FROM media_file");
    jdbcTemplate.update("DELETE FROM media_attachment");
    jdbcTemplate.update("DELETE FROM geo.remarks");
    jdbcTemplate.update("DELETE FROM geo.features");
    UserFixtures.releaseUserReferences(jdbcTemplate);
    jdbcTemplate.update("DELETE FROM opencop_user");
    meterRegistry.find(AUTHZ_DENIED_TOTAL).meters().stream()
        .toList()
        .forEach(meterRegistry::remove);
    meterRegistry.find(STREAM_AUTHZ_TOTAL).meters().stream()
        .toList()
        .forEach(meterRegistry::remove);

    seedUser(USER_ID);
    mediaMtxProperties.setEnabled(true);
    mediaMtxProperties.getHls().setPublicBaseUrl("/streams");
    eventFixtures = new EventFixtures(jdbcTemplate, objectMapper);
    eventFixtures.insertFeature(
        UUID.randomUUID(), OBJECT_ID, SOURCE_A, Instant.parse("2026-03-12T10:00:00Z"));
    eventFixtures.insertFeature(
        UUID.randomUUID(), OBJECT_ID, SOURCE_B, Instant.parse("2026-03-12T10:05:00Z"));
    eventFixtures.insertFeature(
        UUID.randomUUID(), OTHER_OBJECT_ID, SOURCE_A, Instant.parse("2026-03-12T10:10:00Z"));

    when(userSpecificManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_A, ServerRole.ADMIN, null)));
    when(userSpecificManager.getUserLayers(USER_ID)).thenReturn(List.of());
    when(dataSourceRepository.findById(eq(SOURCE_A)))
        .thenReturn(Optional.of(dataSourceWithArchivedAt(SOURCE_A, null)));
    when(dataSourceRepository.findById(eq(SOURCE_B)))
        .thenReturn(Optional.of(dataSourceWithArchivedAt(SOURCE_B, null)));
  }

  @Test
  void upload_persistsSourceScopedAssociation() throws Exception {
    var mediaId = UUID.randomUUID();
    var record = mediaRecord(mediaId);
    var file = new MockMultipartFile("original", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3});

    when(uploadMedia.upload(
            eq(USER_ID),
            eq("photo.jpg"),
            eq("image/jpeg"),
            eq(3L),
            eq(MediaAccess.SCOPED),
            any(),
            eq(null),
            eq(MediaUsage.EVENT_DETAIL)))
        .thenAnswer(
            _ -> {
              seedMedia(mediaId);
              return record;
            });
    when(retrieveMedia.getWithoutAccessCheck(mediaId))
        .thenReturn(new MediaView(record, "https://blob/photo.jpg", List.of()));

    mockMvc
        .perform(
            multipart("/v1/events/{sourceId}/{objectId}/attachments", SOURCE_A, OBJECT_ID)
                .file(file)
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isCreated());

    var row =
        jdbcTemplate.queryForMap(
            "SELECT source_id, object_id, attachment_id FROM event_attachment WHERE source_id = ? AND object_id = ? AND attachment_id = ?",
            SOURCE_A,
            OBJECT_ID,
            mediaId);

    assertEquals(SOURCE_A, row.get("source_id"));
    assertEquals(OBJECT_ID, row.get("object_id"));
    assertEquals(mediaId, row.get("attachment_id"));
  }

  @Test
  void list_filtersAttachmentsBySourceId() throws Exception {
    var mediaA = UUID.randomUUID();
    var mediaB = UUID.randomUUID();
    seedMedia(mediaA);
    seedMedia(mediaB);
    jdbcTemplate.update(
        "INSERT INTO event_attachment (source_id, object_id, attachment_id) VALUES (?, ?, ?)",
        SOURCE_A,
        OBJECT_ID,
        mediaA);
    jdbcTemplate.update(
        "INSERT INTO event_attachment (source_id, object_id, attachment_id) VALUES (?, ?, ?)",
        SOURCE_B,
        OBJECT_ID,
        mediaB);

    when(retrieveMedia.getWithoutAccessCheck(mediaA))
        .thenReturn(new MediaView(mediaRecord(mediaA), "url-a", List.of()));
    when(retrieveMedia.getWithoutAccessCheck(mediaB))
        .thenReturn(new MediaView(mediaRecord(mediaB), "url-b", List.of()));

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/{objectId}/attachments", SOURCE_A, OBJECT_ID)
                    .with(csrf())
                    .with(authentication(TestAuthentications.oidcUser(USER_ID))))
            .andExpect(status().isOk())
            .andReturn();

    var views = readResponse(result, new TypeReference<List<AttachmentView>>() {});
    assertEquals(1, views.size());
    assertEquals(mediaA, ((AttachmentView.File) views.getFirst()).record().id());
  }

  @Test
  void list_filtersAttachmentsByObjectIdWithinSameSource() throws Exception {
    var mediaForObject = UUID.randomUUID();
    var mediaForOtherObject = UUID.randomUUID();
    seedMedia(mediaForObject);
    seedMedia(mediaForOtherObject);
    jdbcTemplate.update(
        "INSERT INTO event_attachment (source_id, object_id, attachment_id) VALUES (?, ?, ?)",
        SOURCE_A,
        OBJECT_ID,
        mediaForObject);
    jdbcTemplate.update(
        "INSERT INTO event_attachment (source_id, object_id, attachment_id) VALUES (?, ?, ?)",
        SOURCE_A,
        OTHER_OBJECT_ID,
        mediaForOtherObject);

    when(retrieveMedia.getWithoutAccessCheck(mediaForObject))
        .thenReturn(new MediaView(mediaRecord(mediaForObject), "url-a", List.of()));
    when(retrieveMedia.getWithoutAccessCheck(mediaForOtherObject))
        .thenReturn(new MediaView(mediaRecord(mediaForOtherObject), "url-b", List.of()));

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/{objectId}/attachments", SOURCE_A, OBJECT_ID)
                    .with(csrf())
                    .with(authentication(TestAuthentications.oidcUser(USER_ID))))
            .andExpect(status().isOk())
            .andReturn();

    var views = readResponse(result, new TypeReference<List<AttachmentView>>() {});
    assertEquals(1, views.size());
    assertEquals(mediaForObject, ((AttachmentView.File) views.getFirst()).record().id());
  }

  @Test
  void list_whenMemberDenied_recordsAuthzDeniedMetricExactlyOnce() throws Exception {
    when(userSpecificManager.getUserServers(USER_ID)).thenReturn(List.of());
    when(userSpecificManager.getUserLayers(USER_ID)).thenReturn(List.of());

    mockMvc
        .perform(
            get("/v1/events/{sourceId}/{objectId}/attachments", SOURCE_A, OBJECT_ID)
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isForbidden());

    var counter =
        meterRegistry
            .find(AUTHZ_DENIED_TOTAL)
            .tags(
                "action",
                "GET /v1/events/{sourceId}/{objectId}/attachments",
                "resource_type",
                "EVENT",
                "reason_code",
                "MISSING_SOURCE_OR_LAYER_ROLE",
                "principal_type",
                "AUTHENTICATED")
            .counter();

    assertNotNull(counter);
    assertEquals(1.0d, counter.count());
    assertEquals(1.0d, totalAuthzDeniedCount());
  }

  @Test
  void upload_whenAttachmentCapReached_returns400() throws Exception {
    for (int i = 0; i < 20; i++) {
      var mediaId = UUID.randomUUID();
      seedMedia(mediaId);
      jdbcTemplate.update(
          "INSERT INTO event_attachment (source_id, object_id, attachment_id) VALUES (?, ?, ?)",
          SOURCE_A,
          OBJECT_ID,
          mediaId);
    }

    var file = new MockMultipartFile("original", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3});

    mockMvc
        .perform(
            multipart("/v1/events/{sourceId}/{objectId}/attachments", SOURCE_A, OBJECT_ID)
                .file(file)
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void get_returns404WhenMediaBelongsToDifferentSource() throws Exception {
    var mediaId = UUID.randomUUID();
    seedMedia(mediaId);
    jdbcTemplate.update(
        "INSERT INTO event_attachment (source_id, object_id, attachment_id) VALUES (?, ?, ?)",
        SOURCE_A,
        OBJECT_ID,
        mediaId);
    when(userSpecificManager.getUserServers(USER_ID))
        .thenReturn(
            List.of(
                new SourceRole(SOURCE_A, ServerRole.ADMIN, null),
                new SourceRole(SOURCE_B, ServerRole.ADMIN, null)));

    mockMvc
        .perform(
            get(
                    "/v1/events/{sourceId}/{objectId}/attachments/{mediaId}",
                    SOURCE_B,
                    OBJECT_ID,
                    mediaId)
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isNotFound());

    assertEquals(0.0d, totalAuthzDeniedCount());
  }

  @Test
  void upload_whenSourceArchived_returnsConflictWithoutPersistingAssociation() throws Exception {
    when(dataSourceRepository.findById(eq(SOURCE_A)))
        .thenReturn(
            Optional.of(
                dataSourceWithArchivedAt(
                    SOURCE_A, Instant.parse("2026-03-12T10:00:00Z").plus(Duration.ofHours(1)))));

    var mediaId = UUID.randomUUID();
    var record = mediaRecord(mediaId);
    var file = new MockMultipartFile("original", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3});
    when(uploadMedia.upload(
            eq(USER_ID),
            eq("photo.jpg"),
            eq("image/jpeg"),
            eq(3L),
            eq(MediaAccess.SCOPED),
            any(),
            eq(null),
            eq(MediaUsage.EVENT_DETAIL)))
        .thenAnswer(
            _ -> {
              seedMedia(mediaId);
              return record;
            });

    mockMvc
        .perform(
            multipart("/v1/events/{sourceId}/{objectId}/attachments", SOURCE_A, OBJECT_ID)
                .file(file)
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isConflict());

    var count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM event_attachment WHERE source_id = ? AND object_id = ?",
            Integer.class,
            SOURCE_A,
            OBJECT_ID);
    assertEquals(0, count);
  }

  @Test
  void list_whenSourceArchived_returnsOkWithSingleMedia() throws Exception {
    when(dataSourceRepository.findById(eq(SOURCE_A)))
        .thenReturn(
            Optional.of(
                dataSourceWithArchivedAt(
                    SOURCE_A, Instant.parse("2026-03-12T10:00:00Z").plus(Duration.ofHours(1)))));

    var mediaId = UUID.randomUUID();
    seedMedia(mediaId);
    jdbcTemplate.update(
        "INSERT INTO event_attachment (source_id, object_id, attachment_id) VALUES (?, ?, ?)",
        SOURCE_A,
        OBJECT_ID,
        mediaId);
    when(retrieveMedia.getWithoutAccessCheck(mediaId))
        .thenReturn(new MediaView(mediaRecord(mediaId), "url", List.of()));

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/{objectId}/attachments", SOURCE_A, OBJECT_ID)
                    .with(csrf())
                    .with(authentication(TestAuthentications.oidcUser(USER_ID))))
            .andExpect(status().isOk())
            .andReturn();

    var views = readResponse(result, new TypeReference<List<AttachmentView>>() {});
    assertEquals(1, views.size());
    assertEquals(mediaId, ((AttachmentView.File) views.getFirst()).record().id());
  }

  @Test
  void get_returns404WhenMediaBelongsToDifferentObject() throws Exception {
    var mediaId = UUID.randomUUID();
    seedMedia(mediaId);
    jdbcTemplate.update(
        "INSERT INTO event_attachment (source_id, object_id, attachment_id) VALUES (?, ?, ?)",
        SOURCE_A,
        OTHER_OBJECT_ID,
        mediaId);

    mockMvc
        .perform(
            get(
                    "/v1/events/{sourceId}/{objectId}/attachments/{mediaId}",
                    SOURCE_A,
                    OBJECT_ID,
                    mediaId)
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isNotFound());

    assertEquals(0.0d, totalAuthzDeniedCount());
  }

  @Test
  void delete_returns404WhenMediaBelongsToDifferentObject() throws Exception {
    var mediaId = UUID.randomUUID();
    seedMedia(mediaId);
    jdbcTemplate.update(
        "INSERT INTO event_attachment (source_id, object_id, attachment_id) VALUES (?, ?, ?)",
        SOURCE_A,
        OTHER_OBJECT_ID,
        mediaId);

    mockMvc
        .perform(
            delete(
                    "/v1/events/{sourceId}/{objectId}/attachments/{mediaId}",
                    SOURCE_A,
                    OBJECT_ID,
                    mediaId)
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isNotFound());

    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM event_attachment WHERE source_id = ? AND object_id = ? AND attachment_id = ?",
            Integer.class,
            SOURCE_A,
            OTHER_OBJECT_ID,
            mediaId));
    assertEquals(0.0d, totalAuthzDeniedCount());
  }

  @Test
  void delete_ownerMismatch_recordsGenericUnattributedDenial() throws Exception {
    var mediaId = UUID.randomUUID();
    var otherOwner = UUID.fromString("550e8400-e29b-41d4-a716-446655440199");
    seedMedia(mediaId);
    jdbcTemplate.update(
        "INSERT INTO event_attachment (source_id, object_id, attachment_id) VALUES (?, ?, ?)",
        SOURCE_A,
        OBJECT_ID,
        mediaId);
    var record =
        new MediaRecord(
            mediaId,
            "blob/" + mediaId,
            "photo.jpg",
            MediaContentType.IMAGE,
            3L,
            MediaAccess.SCOPED,
            otherOwner,
            otherOwner,
            Instant.parse("2026-03-12T10:00:00Z"),
            "image/jpeg",
            null,
            null,
            MediaUsage.EVENT_DETAIL);
    when(retrieveMedia.getWithoutAccessCheck(mediaId))
        .thenReturn(new MediaView(record, "url", List.of()));
    when(authorizationService.isAdmin(any(OpenCopPrincipal.class))).thenReturn(false);

    var result =
        mockMvc
            .perform(
                delete(
                        "/v1/events/{sourceId}/{objectId}/attachments/{mediaId}",
                        SOURCE_A,
                        OBJECT_ID,
                        mediaId)
                    .with(csrf())
                    .with(authentication(TestAuthentications.oidcUser(USER_ID))))
            .andExpect(status().isForbidden())
            .andReturn();

    // The attribution filter stashed the real actor for the log-only fields.
    assertEquals(
        USER_ID, result.getRequest().getAttribute(AuthzAuditRequestAttributes.ACTOR_USER_ID));

    // principal_type=AUTHENTICATED comes from the in-chain attribution
    // filter's stashed attribute — the backstop itself unwinds outside the
    // chain with a cleared SecurityContext and reads only what was stashed.
    var counter =
        meterRegistry
            .find(AUTHZ_DENIED_TOTAL)
            .tags(
                "action",
                "DELETE /v1/events/{sourceId}/{objectId}/attachments/{attachmentId}",
                "resource_type",
                "UNKNOWN",
                "reason_code",
                "UNATTRIBUTED",
                "principal_type",
                "AUTHENTICATED")
            .counter();

    assertNotNull(counter);
    assertEquals(1.0d, counter.count());
    assertEquals(1.0d, totalAuthzDeniedCount());
  }

  @Test
  void prepareStream_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post(
                    "/v1/events/{sourceId}/{objectId}/attachments/{attachmentId}/stream/prepare",
                    SOURCE_A,
                    OBJECT_ID,
                    UUID.randomUUID())
                .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void prepareStream_whenGatewaySucceeds_returnsHlsUrl() throws Exception {
    var attachmentId = UUID.randomUUID();
    var hlsUrl = "/streams/att_" + attachmentId + "/index.m3u8";
    when(prepareAttachmentStream.prepare(any(), eq(SOURCE_A), eq(OBJECT_ID), eq(attachmentId)))
        .thenReturn(Optional.of(hlsUrl));

    var result =
        mockMvc
            .perform(
                post(
                        "/v1/events/{sourceId}/{objectId}/attachments/{attachmentId}/stream/prepare",
                        SOURCE_A,
                        OBJECT_ID,
                        attachmentId)
                    .with(csrf())
                    .with(authentication(TestAuthentications.oidcUser(USER_ID))))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, EventAttachmentEndpoint.StreamPrepareResponse.class);
    assertEquals(hlsUrl, response.hlsUrl());
  }

  @Test
  void prepareStream_whenGatewayUnavailable_returnsServiceUnavailable() throws Exception {
    var attachmentId = UUID.randomUUID();
    when(prepareAttachmentStream.prepare(any(), any(), any(), any())).thenReturn(Optional.empty());

    var result =
        mockMvc
            .perform(
                post(
                        "/v1/events/{sourceId}/{objectId}/attachments/{attachmentId}/stream/prepare",
                        SOURCE_A,
                        OBJECT_ID,
                        attachmentId)
                    .with(csrf())
                    .with(authentication(TestAuthentications.oidcUser(USER_ID))))
            .andExpect(status().isServiceUnavailable())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("STREAM_PREPARATION_UNAVAILABLE", error.code());
  }

  @Test
  void prepareStream_whenAccessDenied_returnsForbidden() throws Exception {
    var attachmentId = UUID.randomUUID();
    when(prepareAttachmentStream.prepare(any(), eq(SOURCE_A), eq(OBJECT_ID), eq(attachmentId)))
        .thenThrow(new GlobalOpenCopException("Forbidden", HttpStatus.FORBIDDEN));

    mockMvc
        .perform(
            post(
                    "/v1/events/{sourceId}/{objectId}/attachments/{attachmentId}/stream/prepare",
                    SOURCE_A,
                    OBJECT_ID,
                    attachmentId)
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isForbidden());
  }

  @Test
  void authorizeStream_whenSharedAttachmentHasAccessibleAssociation_returnsNoContent()
      throws Exception {
    var attachmentId = UUID.randomUUID();
    seedMedia(attachmentId);
    jdbcTemplate.update(
        "INSERT INTO event_attachment (source_id, object_id, attachment_id) VALUES (?, ?, ?)",
        SOURCE_B,
        OBJECT_ID,
        attachmentId);
    jdbcTemplate.update(
        "INSERT INTO event_attachment (source_id, object_id, attachment_id) VALUES (?, ?, ?)",
        SOURCE_A,
        OTHER_OBJECT_ID,
        attachmentId);
    when(mediaUrlRepository.findById(attachmentId))
        .thenReturn(Optional.of(urlRecord(attachmentId, "rtsp://camera.example.com/stream")));

    mockMvc
        .perform(
            get("/streams/authorize")
                .header("X-Forwarded-Uri", "/streams/att_" + attachmentId + "/index.m3u8")
                .header("X-Forwarded-Method", "GET")
                .header("X-Forwarded-Host", "untrusted.example.test")
                .header("X-Forwarded-Proto", "http")
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isNoContent());

    verify(prepareAttachmentStream, never()).prepare(any(), any(), any(), any());
    assertStreamAuthzCounter("allowed", "allowed");
  }

  @Test
  void authorizeStream_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(get("/streams/authorize")).andExpect(status().isUnauthorized());

    assertStreamAuthzCounter("denied", "unauthenticated");
  }

  @Test
  void authorizeStream_whenForwardedUriMissing_returnsForbidden() throws Exception {
    mockMvc
        .perform(
            get("/streams/authorize")
                .header("X-Forwarded-Method", "GET")
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isForbidden());

    assertStreamAuthzCounter("denied", "missing_forwarded_uri");
  }

  @Test
  void authorizeStream_whenForwardedPathMalformed_returnsForbidden() throws Exception {
    mockMvc
        .perform(
            get("/streams/authorize")
                .header("X-Forwarded-Uri", "/streams/ds_abc/index.m3u8")
                .header("X-Forwarded-Method", "GET")
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isForbidden());

    assertStreamAuthzCounter("denied", "malformed_stream_path");
  }

  @Test
  void authorizeStream_whenForwardedPathContainsTraversal_returnsForbidden() throws Exception {
    var attachmentId = UUID.randomUUID();
    var otherAttachmentId = UUID.randomUUID();

    mockMvc
        .perform(
            get("/streams/authorize")
                .header(
                    "X-Forwarded-Uri",
                    "/streams/att_" + attachmentId + "/../att_" + otherAttachmentId + "/index.m3u8")
                .header("X-Forwarded-Method", "GET")
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isForbidden());

    assertStreamAuthzCounter("denied", "malformed_stream_path");
  }

  @Test
  void authorizeStream_whenForwardedMethodUnsupported_returnsForbidden() throws Exception {
    var attachmentId = UUID.randomUUID();

    mockMvc
        .perform(
            get("/streams/authorize")
                .header("X-Forwarded-Uri", "/streams/att_" + attachmentId + "/index.m3u8")
                .header("X-Forwarded-Method", "POST")
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isForbidden());

    assertStreamAuthzCounter("denied", "unsupported_method");
  }

  @Test
  void authorizeStream_whenForwardedMethodMissing_returnsForbidden() throws Exception {
    var attachmentId = UUID.randomUUID();

    mockMvc
        .perform(
            get("/streams/authorize")
                .header("X-Forwarded-Uri", "/streams/att_" + attachmentId + "/index.m3u8")
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isForbidden());

    assertStreamAuthzCounter("denied", "unsupported_method");
  }

  @Test
  void authorizeStream_whenMediaMtxDisabled_returnsForbidden() throws Exception {
    mediaMtxProperties.setEnabled(false);
    var attachmentId = UUID.randomUUID();

    mockMvc
        .perform(
            get("/streams/authorize")
                .header("X-Forwarded-Uri", "/streams/att_" + attachmentId + "/index.m3u8")
                .header("X-Forwarded-Method", "GET")
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isForbidden());

    assertStreamAuthzCounter("denied", "mediamtx_disabled");
  }

  @Test
  void authorizeStream_whenMediaMtxPublicHlsBaseUrlMissing_returnsForbidden() throws Exception {
    mediaMtxProperties.getHls().setPublicBaseUrl(" ");
    var attachmentId = UUID.randomUUID();

    mockMvc
        .perform(
            get("/streams/authorize")
                .header("X-Forwarded-Uri", "/streams/att_" + attachmentId + "/index.m3u8")
                .header("X-Forwarded-Method", "GET")
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isForbidden());

    assertStreamAuthzCounter("denied", "mediamtx_disabled");
  }

  @Test
  void authorizeStream_whenAttachmentUnknown_returnsForbidden() throws Exception {
    var attachmentId = UUID.randomUUID();
    when(mediaUrlRepository.findById(attachmentId)).thenReturn(Optional.empty());

    mockMvc
        .perform(
            get("/streams/authorize")
                .header("X-Forwarded-Uri", "/streams/att_" + attachmentId + "/index.m3u8")
                .header("X-Forwarded-Method", "GET")
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isForbidden());

    assertStreamAuthzCounter("denied", "attachment_not_found");
  }

  @Test
  void authorizeStream_whenAttachmentIsNotStreamable_returnsForbidden() throws Exception {
    var attachmentId = UUID.randomUUID();
    seedMedia(attachmentId);
    jdbcTemplate.update(
        "INSERT INTO event_attachment (source_id, object_id, attachment_id) VALUES (?, ?, ?)",
        SOURCE_A,
        OBJECT_ID,
        attachmentId);
    when(mediaUrlRepository.findById(attachmentId))
        .thenReturn(Optional.of(urlRecord(attachmentId, "https://example.test/not-streamable")));

    mockMvc
        .perform(
            get("/streams/authorize")
                .header("X-Forwarded-Uri", "/streams/att_" + attachmentId + "/index.m3u8")
                .header("X-Forwarded-Method", "GET")
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isForbidden());

    assertStreamAuthzCounter("denied", "attachment_not_streamable");
  }

  @Test
  void authorizeStream_whenNoAssociationIsAccessible_returnsForbidden() throws Exception {
    var attachmentId = UUID.randomUUID();
    seedMedia(attachmentId);
    jdbcTemplate.update(
        "INSERT INTO event_attachment (source_id, object_id, attachment_id) VALUES (?, ?, ?)",
        SOURCE_B,
        OBJECT_ID,
        attachmentId);
    when(mediaUrlRepository.findById(attachmentId))
        .thenReturn(Optional.of(urlRecord(attachmentId, "rtsp://camera.example.com/stream")));

    mockMvc
        .perform(
            get("/streams/authorize")
                .header("X-Forwarded-Uri", "/streams/att_" + attachmentId + "/index.m3u8")
                .header("X-Forwarded-Method", "GET")
                .with(authentication(TestAuthentications.oidcUser(USER_ID))))
        .andExpect(status().isForbidden());

    assertStreamAuthzCounter("denied", "no_accessible_association");
  }

  private MediaRecord mediaRecord(UUID mediaId) {
    return new MediaRecord(
        mediaId,
        "blob/" + mediaId,
        "photo.jpg",
        MediaContentType.IMAGE,
        3L,
        MediaAccess.SCOPED,
        USER_ID,
        USER_ID,
        Instant.parse("2026-03-12T10:00:00Z"),
        "image/jpeg",
        null,
        null,
        MediaUsage.EVENT_DETAIL);
  }

  private MediaUrlRecord urlRecord(UUID mediaId, String url) {
    return new MediaUrlRecord(
        mediaId,
        url,
        StreamKind.RTSP,
        MediaAccess.SCOPED,
        USER_ID,
        USER_ID,
        Instant.parse("2026-03-12T10:00:00Z"),
        MediaUsage.EVENT_DETAIL);
  }

  private void seedUser(UUID userId) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        userId,
        "idp-" + userId,
        "user-" + userId,
        "https://issuer.test",
        "user-" + userId,
        "user@example.com",
        "Test",
        "User",
        "Test User");
  }

  private void seedMedia(UUID mediaId) {
    jdbcTemplate.update(
        """
        INSERT INTO media_attachment (
          id, creator_id, owner_id, access, created_at, usage
        ) VALUES (?, ?, ?, CAST(? AS media_access), ?, CAST(? AS media_usage))
        """,
        mediaId,
        USER_ID,
        USER_ID,
        "SCOPED",
        Timestamp.from(Instant.now()),
        "EVENT_DETAIL");
    jdbcTemplate.update(
        """
        INSERT INTO media_file (
          id, blob_key, original_filename, content_type, size_bytes, mime_type
        ) VALUES (?, ?, ?, CAST(? AS media_content_type), ?, ?)
        """,
        mediaId,
        "blob/" + mediaId,
        "file-" + mediaId + ".jpg",
        "IMAGE",
        123L,
        "image/jpeg");
  }

  private double totalAuthzDeniedCount() {
    return meterRegistry.find(AUTHZ_DENIED_TOTAL).meters().stream()
        .map(Counter.class::cast)
        .mapToDouble(Counter::count)
        .sum();
  }

  private void assertStreamAuthzCounter(String outcome, String reason) {
    var counter =
        meterRegistry.find(STREAM_AUTHZ_TOTAL).tags("outcome", outcome, "reason", reason).counter();

    assertNotNull(counter);
    assertEquals(1.0d, counter.count());
    assertEquals(1.0d, totalStreamAuthzCount());
  }

  private double totalStreamAuthzCount() {
    return meterRegistry.find(STREAM_AUTHZ_TOTAL).meters().stream()
        .map(Counter.class::cast)
        .mapToDouble(Counter::count)
        .sum();
  }

  private DataSource dataSourceWithArchivedAt(UUID sourceId, Instant archivedAt) {
    return new SocketDataSource(
        sourceId,
        "source-" + sourceId,
        "tenant-a",
        new ConnectionSpec(
            new TcpConfig("127.0.0.1", 8087, null, null, null, null, new NoSecurity())),
        null,
        null,
        null,
        AccessMode.READ,
        true,
        null,
        archivedAt,
        USER_ID,
        Instant.parse("2026-03-12T10:00:00Z"),
        null,
        Instant.parse("2026-03-12T10:00:00Z"),
        null,
        null,
        true);
  }
}
