// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.watchbox;

import static com.opencop.core.api.component.watchbox.WatchboxTestMapperIdentity.MAPPER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.FullyQualifiedConfigurationContextInitializer;
import com.opencop.core.api.component.support.fixture.ConnectionFixtures;
import com.opencop.core.api.component.support.fixture.LayerFixtures;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.watchbox.business.service.WatchboxResponseAssembler;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.watchbox.WatchboxAlertingStatus;
import com.opencop.core.sdk.model.watchbox.WatchboxCapabilities;
import com.opencop.core.sdk.model.watchbox.WatchboxConfigurationLimits;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterMatch;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusReason;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

@ComponentWebIT(classes = WatchboxWebTestApplication.class)
@ContextConfiguration(initializers = FullyQualifiedConfigurationContextInitializer.class)
class WatchboxesEndpointIT extends AbstractWebIT {

  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441001");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441002");
  private static final UUID VIEWER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441005");
  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441006");
  private static final Authentication OWNER_AUTH = TestAuthentications.oidcUser(OWNER_ID);
  private static final Authentication VIEWER_AUTH = TestAuthentications.oidcUser(VIEWER_ID);
  private static final Authentication GLOBAL_ADMIN_AUTH =
      TestAuthentications.oidcGlobalAdmin(ADMIN_ID);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private MapperProfileService mapperProfileService;
  @Autowired private IamLayerRepository layerRepository;
  @Autowired private IamLayerMembersRepository layerMembersRepository;

  @Autowired
  private com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository
      serverLayerRepository;

  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoSpyBean private WatchboxResponseAssembler responseAssembler;

  private ConnectionFixtures connectionFixtures;
  private LayerFixtures layerFixtures;
  private UUID layerId;

  @BeforeEach
  void setUp() {
    connectionFixtures = new ConnectionFixtures(jdbcTemplate, dataSourceRepository, objectMapper);
    layerFixtures =
        new LayerFixtures(
            jdbcTemplate, layerRepository, layerMembersRepository, serverLayerRepository);

    resetWatchboxes();
    seedFreshDetectionHeartbeat();
    layerFixtures.reset();
    connectionFixtures.reset();
    insertUser(OWNER_ID);
    insertUser(VIEWER_ID);
    connectionFixtures.seedDefaultMapper(MAPPER_ID);
    connectionFixtures.persistTcpSource(SOURCE_ID, "watchbox-source", MAPPER_ID, true, null);
    layerId =
        layerFixtures.persistLayerWithMembers("Watchbox Layer", Map.of(OWNER_ID, LayerRole.OWNER));
    layerFixtures.addServerAccess(SOURCE_ID, layerId, ServerRole.VIEWER);
    when(mapperProfileService.getLatestMapperProfileFull(MAPPER_ID))
        .thenReturn(
            new MapperProfileFullMeta(
                MapperProfile.builder()
                    .withId(MAPPER_ID)
                    .withName("test-mapper")
                    .withEditable(false)
                    .withIntegrationId(WatchboxTestMapperIdentity.INTEGRATION_ID)
                    .withVersion(1)
                    .withArchived(false)
                    .build(),
                null,
                null));
  }

  @Test
  void validationErrorsReturnEmptyReasonsAndSpecificNameCode() throws Exception {
    var missingName =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequestWithoutName()))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(missingName, WatchboxErrorCode.WATCHBOX_INVALID_NAME, "name");

    var invalidPolygon =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequestWithInvalidPolygon()))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(invalidPolygon, WatchboxErrorCode.WATCHBOX_INVALID_POLYGON, "polygon");

    var invalidFilter =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequestWithInvalidFilter()))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(
        invalidFilter, WatchboxErrorCode.WATCHBOX_INVALID_FILTER, "filter.predicates[0].path");
  }

  @Test
  void archivedPinnedMapperRejectsEnabledCreateWithAggregateConflict() throws Exception {
    when(mapperProfileService.getLatestMapperProfileFull(MAPPER_ID))
        .thenReturn(
            new MapperProfileFullMeta(
                MapperProfile.builder()
                    .withId(MAPPER_ID)
                    .withName("archived-test-mapper")
                    .withEditable(false)
                    .withIntegrationId(WatchboxTestMapperIdentity.INTEGRATION_ID)
                    .withVersion(1)
                    .withArchived(true)
                    .build(),
                null,
                null));

    var result =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest("archived-mapper-proof")))
            .andExpect(status().isConflict())
            .andReturn();

    var error = readResponse(result, WatchboxErrorResponse.class);
    assertThat(error.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_SOURCE_ELIGIBILITY_FAILED);
    assertThat(error.message()).isEqualTo("Selected sources require object ID mapping correction");
    assertThat(error.fields()).containsExactly("layerIds");
    assertThat(error.reasons()).isEmpty();
    assertThat(error.context()).containsEntry("sourceNames", List.of("watchbox-source"));
    assertThat(countAllAuditRows()).isZero();
  }

  @Test
  void archivedLayerSourceIsSkippedByCreateAndReEnableAdmission() throws Exception {
    // Archiving is a soft delete that keeps the layer association, so the layer still expands to
    // two sources. Kafka avoids contesting the TCP source's active listen port.
    UUID archivedSourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655441007");
    connectionFixtures.persistKafkaSource(archivedSourceId, "archived-source", MAPPER_ID, true);
    layerFixtures.addServerAccess(archivedSourceId, layerId, ServerRole.VIEWER);
    dataSourceRepository.archive(archivedSourceId, OWNER_ID);
    assertThat(connectionFixtures.countArchivedSources(archivedSourceId)).isEqualTo(1);

    var created = createWatchbox("archived-source-admission-proof");
    assertThat(created.enabled()).isTrue();

    var disableResult =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":false}"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(readResponse(disableResult, WatchboxResponse.class).enabled()).isFalse();

    // Re-enabling resets tracking, so the confirmation travels with the enable.
    var reEnableResult =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":true,\"confirmTrackingReset\":true}"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(readResponse(reEnableResult, WatchboxResponse.class).enabled()).isTrue();
  }

  @Test
  void patchBlankNameReturnsInvalidNameCode() throws Exception {
    var created = createWatchbox("blank-name-proof");

    var result =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"   \"}"))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertWatchboxError(result, WatchboxErrorCode.WATCHBOX_INVALID_NAME, "name");
  }

  @Test
  void configurationBounds_whenExceeded_returnSpecificErrorsWithoutRejectedAuditRows()
      throws Exception {
    String maximumName = "n".repeat(255);
    String maximumDescription = "d".repeat(4_000);
    String boundedRequest =
        validCreateRequest(maximumName)
            .replace(
                "\"description\":\"REST/config proof\"",
                "\"description\":\"" + maximumDescription + "\"");
    var boundedCreate =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(boundedRequest))
            .andExpect(status().isCreated())
            .andReturn();
    var created = readResponse(boundedCreate, WatchboxResponse.class);
    assertThat(created.name()).isEqualTo(maximumName);
    assertThat(created.description()).isEqualTo(maximumDescription);
    assertThat(countAuditRows(created.id())).isEqualTo(1);

    var longNameCreate =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest("n".repeat(256))))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(longNameCreate, WatchboxErrorCode.WATCHBOX_INVALID_NAME, "name");
    assertThat(countAllAuditRows()).isEqualTo(1);

    var longDescriptionPatch =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\":\"" + "d".repeat(4_001) + "\"}"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(
        longDescriptionPatch, WatchboxErrorCode.WATCHBOX_INVALID_DESCRIPTION, "description");
    assertThat(countAuditRows(created.id())).isEqualTo(1);

    String tooManyLayerIds =
        IntStream.rangeClosed(1, 101)
            .mapToObj(index -> "\"00000000-0000-0000-0003-%012d\"".formatted(index))
            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    var tooManyLayersPatch =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"layerIds\":" + tooManyLayerIds + "}"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(
        tooManyLayersPatch, WatchboxErrorCode.WATCHBOX_LAYER_LIMIT_EXCEEDED, "layerIds");
    assertThat(countAuditRows(created.id())).isEqualTo(1);

    String tooManyPredicates =
        IntStream.rangeClosed(1, WatchboxConfigurationLimits.MAX_FILTER_PREDICATES + 1)
            .mapToObj(
                index ->
                    "{\"path\":\"properties.opencop.attributes.attr%d\",\"operation\":\"EQUALS\",\"values\":[\"v\"]}"
                        .formatted(index))
            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    var tooManyPredicatesPatch =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"filter\":{\"combine\":\"ALL\",\"predicates\":"
                            + tooManyPredicates
                            + "}}"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(
        tooManyPredicatesPatch,
        WatchboxErrorCode.WATCHBOX_FILTER_LIMIT_EXCEEDED,
        "filter.predicates");
    assertThat(countAuditRows(created.id())).isEqualTo(1);
  }

  @Test
  void nullCreateAndPatchBodiesReturnWatchboxErrorEnvelope() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("null"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(createResult, WatchboxErrorCode.WATCHBOX_INVALID_REQUEST, "body");

    var created = createWatchbox("null-patch-proof");
    var patchResult =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("null"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(patchResult, WatchboxErrorCode.WATCHBOX_INVALID_REQUEST, "body");
  }

  @Test
  void unreadableRequestBodiesReturnWatchboxErrorEnvelope() throws Exception {
    var malformedCreate =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"x\""))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertUnreadableBodyError(malformedCreate);

    var invalidCreateEnum =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest("bad-direction-proof").replace("BOTH", "NORTH")))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertUnreadableBodyError(invalidCreateEnum);

    var created = createWatchbox("unreadable-patch-proof");
    var malformedPatch =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\": \"x\""))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertUnreadableBodyError(malformedPatch);

    var invalidPatchEnum =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"filter":{"combine":"ALL","predicates":[{"path":"properties.opencop.kind","operation":"CONTAINS","values":["unit"]}]}}
                        """))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertUnreadableBodyError(invalidPatchEnum);

    var malformedFilterTest =
        mockMvc
            .perform(
                post("/v1/watchboxes/filter/test")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"filter\": {"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertUnreadableBodyError(malformedFilterTest);
  }

  @Test
  void numericAndQuotedNumericEnumOrdinalsReturnWatchboxErrorEnvelope() throws Exception {
    var numericDirection =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        validCreateRequest("numeric-direction-proof").replace("\"BOTH\"", "0")))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertUnreadableBodyError(numericDirection);

    var quotedNumericDirection =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        validCreateRequest("quoted-numeric-direction-proof")
                            .replace("\"BOTH\"", "\"0\"")))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertUnreadableBodyError(quotedNumericDirection);

    var numericFilterOperation =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest("numeric-operation-proof").replace("\"IN\"", "0")))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertUnreadableBodyError(numericFilterOperation);
  }

  @Test
  void unreadableBodyDebugStaysBoundedEvenWithBroadSpringDebugEnabled() throws Exception {
    var marker = "unreadable-body-debug-marker-8f3c1a";
    var advice =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(
                com.opencop.core.api.watchbox.technical.endpoint.WatchboxExceptionAdvice.class);
    var resolver =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(
                org.springframework.web.servlet.mvc.method.annotation
                    .ExceptionHandlerExceptionResolver.class);
    var root =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    var springframework =
        (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.springframework");

    var adviceAppender =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    var resolverAppender =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    adviceAppender.start();
    resolverAppender.start();
    advice.addAppender(adviceAppender);
    resolver.addAppender(resolverAppender);
    var originalRootLevel = root.getLevel();
    var originalSpringLevel = springframework.getLevel();
    // Leave the resolver's own explicit level (set to INFO in application.yml) untouched; only
    // broaden root/org.springframework to prove that broad DEBUG does not implicitly enable it.
    root.setLevel(ch.qos.logback.classic.Level.DEBUG);
    springframework.setLevel(ch.qos.logback.classic.Level.DEBUG);

    try {
      var result =
          mockMvc
              .perform(
                  post("/v1/watchboxes")
                      .with(csrf())
                      .with(authentication(OWNER_AUTH))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"description\": \"" + marker + "\""))
              .andExpect(status().isBadRequest())
              .andReturn();
      assertUnreadableBodyError(result);

      assertThat(adviceAppender.list).hasSize(1);
      var adviceEvent = adviceAppender.list.getFirst();
      assertThat(adviceEvent.getFormattedMessage()).isEqualTo("Watchbox JSON parse error");
      assertThat(adviceEvent.getThrowableProxy()).isNull();

      // The resolver's exact category stays at its configured INFO level despite broad
      // root/org.springframework DEBUG, so it never emits its handled-exception DEBUG record.
      assertThat(resolverAppender.list).isEmpty();
    } finally {
      advice.detachAppender(adviceAppender);
      resolver.detachAppender(resolverAppender);
      adviceAppender.stop();
      resolverAppender.stop();
      root.setLevel(originalRootLevel);
      springframework.setLevel(originalSpringLevel);
    }
  }

  @Test
  void nonEmptyFilterMissingCombineReturnsInvalidFilter() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequestWithFilterMissingCombine()))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertWatchboxError(result, WatchboxErrorCode.WATCHBOX_INVALID_FILTER, "filter.combine");
  }

  @Test
  void filterTestEndpointMatchesHostileAndRejectsFriendlyFeature() throws Exception {
    var hostileResult =
        mockMvc
            .perform(
                post("/v1/watchboxes/filter/test")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(filterTestRequest("shgp-----------")))
            .andExpect(status().isOk())
            .andReturn();
    var hostile = readResponse(hostileResult, WatchboxFilterMatch.class);
    assertThat(hostile.matches()).isTrue();
    assertThat(hostile.predicateResults()).singleElement().extracting("matched").isEqualTo(true);

    var friendlyResult =
        mockMvc
            .perform(
                post("/v1/watchboxes/filter/test")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(filterTestRequest("sfgp-----------")))
            .andExpect(status().isOk())
            .andReturn();
    var friendly = readResponse(friendlyResult, WatchboxFilterMatch.class);
    assertThat(friendly.matches()).isFalse();
    assertThat(friendly.predicateResults()).singleElement().extracting("matched").isEqualTo(false);
  }

  @Test
  void filterTestEndpointAcceptsObjectFeatureRootWithoutGeoJsonValidation() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/v1/watchboxes/filter/test")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(filterTestRequestWithPartialFeatureObject("shgp-----------")))
            .andExpect(status().isOk())
            .andReturn();

    var match = readResponse(result, WatchboxFilterMatch.class);
    assertThat(match.matches()).isTrue();
    assertThat(match.predicateResults()).singleElement().extracting("matched").isEqualTo(true);
  }

  @Test
  void filterTestEndpointReturnsWatchboxErrorsForInvalidAndUnknownFields() throws Exception {
    var invalidFilter =
        mockMvc
            .perform(
                post("/v1/watchboxes/filter/test")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(filterTestRequestMissingOffset()))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(
        invalidFilter, WatchboxErrorCode.WATCHBOX_INVALID_FILTER, "filter.predicates[0].offset");

    var unknownOperation =
        mockMvc
            .perform(
                post("/v1/watchboxes/filter/test")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(filterTestRequestUnknownOperation()))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(
        unknownOperation,
        WatchboxErrorCode.WATCHBOX_INVALID_FILTER,
        "filter.predicates[0].operation");

    var unknownRootField =
        mockMvc
            .perform(
                post("/v1/watchboxes/filter/test")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(filterTestRequestUnknownRootField()))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(
        unknownRootField, WatchboxErrorCode.WATCHBOX_INVALID_REQUEST, "body.unexpected");

    var unknownFilterField =
        mockMvc
            .perform(
                post("/v1/watchboxes/filter/test")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(filterTestRequestUnknownFilterField()))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(
        unknownFilterField, WatchboxErrorCode.WATCHBOX_INVALID_FILTER, "filter.unexpected");

    var unknownCombine =
        mockMvc
            .perform(
                post("/v1/watchboxes/filter/test")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(filterTestRequestUnknownCombine()))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(
        unknownCombine, WatchboxErrorCode.WATCHBOX_INVALID_FILTER, "filter.combine");

    var unknownPredicateField =
        mockMvc
            .perform(
                post("/v1/watchboxes/filter/test")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(filterTestRequestUnknownPredicateField()))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(
        unknownPredicateField,
        WatchboxErrorCode.WATCHBOX_INVALID_FILTER,
        "filter.predicates[0].unexpected");

    var missingFeature =
        mockMvc
            .perform(
                post("/v1/watchboxes/filter/test")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"filter":{"combine":"ALL","predicates":[]}}
                        """))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(missingFeature, WatchboxErrorCode.WATCHBOX_INVALID_REQUEST, "feature");

    var malformedFeature =
        mockMvc
            .perform(
                post("/v1/watchboxes/filter/test")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(filterTestRequestMalformedFeature()))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertWatchboxError(malformedFeature, WatchboxErrorCode.WATCHBOX_INVALID_REQUEST, "feature");
  }

  @Test
  void nullLayerIdElementReturnsWatchboxErrorEnvelope() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name":"null-layer-proof",
                          "description":"REST/config proof",
                          "direction":"BOTH",
                          "layerIds":[null],
                          "polygon":{"type":"Polygon","coordinates":[[[-100.2,39.8],[-99.8,39.8],[-99.8,40.2],[-100.2,40.2],[-100.2,39.8]]]},
                          "filter":{"predicates":[]},
                          "enabled":true
                        }
                        """))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertWatchboxError(result, WatchboxErrorCode.WATCHBOX_LAYER_REQUIRED, "layerIds");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void createAndPatch_globalLayerLists_allowEffectiveManagers(boolean allGlobal) throws Exception {
    var globalLayerId =
        layerFixtures.persistLayerWithMembers(
            "Global Watchbox Layer",
            LayerAccessScope.GLOBAL,
            Map.of(OWNER_ID, LayerRole.OWNER, VIEWER_ID, LayerRole.ADMIN));
    layerFixtures.addMember(layerId, VIEWER_ID, LayerRole.ADMIN);
    layerFixtures.addServerAccess(SOURCE_ID, globalLayerId, ServerRole.VIEWER);
    if (allGlobal) {
      makeLayerGlobal(layerId);
    }

    var created = createTwoLayerWatchbox("global-managers", globalLayerId);
    assertThat(created.layerIds()).containsExactlyInAnyOrder(layerId, globalLayerId);
    assertThat(created.capabilities()).isEqualTo(new WatchboxCapabilities(true, true));

    var patched =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(VIEWER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"description":"managed through layer roles","layerIds":["%s","%s"]}
                        """
                            .formatted(layerId, globalLayerId)))
            .andExpect(status().isOk())
            .andReturn();
    var response = readResponse(patched, WatchboxResponse.class);
    assertThat(response.description()).isEqualTo("managed through layer roles");
    assertThat(response.capabilities()).isEqualTo(new WatchboxCapabilities(true, true));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void patch_whenAddingGlobalLayerAsImplicitMember_rejectsWithoutMutation(boolean enabled)
      throws Exception {
    var created = createWatchbox("global-add-management-denial");
    var auditRowsBefore = countAuditRows(created.id());
    var globalLayerId =
        layerFixtures.persistLayer("Global Watchbox Layer", LayerAccessScope.GLOBAL);
    layerFixtures.addServerAccess(SOURCE_ID, globalLayerId, ServerRole.VIEWER);
    var request =
        """
        {"layerIds":["%s","%s"],"enabled":%s}
        """
            .formatted(layerId, globalLayerId, enabled);

    var denied =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isForbidden())
            .andReturn();
    assertWatchboxError(denied, WatchboxErrorCode.WATCHBOX_MANAGEMENT_ROLE_REQUIRED, "layerIds");
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT layer_id FROM watchbox.watchbox_layer WHERE watchbox_id = ?",
                UUID.class,
                created.id()))
        .containsExactly(layerId);
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT enabled, config_epoch FROM watchbox.watchbox WHERE id = ?", created.id()))
        .isEqualTo(Map.of("enabled", created.enabled(), "config_epoch", created.configEpoch()));
    assertThat(countAuditRows(created.id())).isEqualTo(auditRowsBefore);

    layerFixtures.addMember(globalLayerId, OWNER_ID, LayerRole.ADMIN);
    mockMvc
        .perform(
            patch("/v1/watchboxes/{watchboxId}", created.id())
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void globalLayerLists_implicitMember_canReadButCannotManage(boolean allGlobal) throws Exception {
    var globalLayerId =
        layerFixtures.persistLayer("Global Watchbox Layer", LayerAccessScope.GLOBAL);
    layerFixtures.addServerAccess(SOURCE_ID, globalLayerId, ServerRole.VIEWER);
    if (allGlobal) {
      makeLayerGlobal(layerId);
    }
    var request = twoLayerCreateRequest("global-member-boundary", globalLayerId);
    var deniedCreate =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isForbidden())
            .andReturn();
    assertWatchboxError(
        deniedCreate, WatchboxErrorCode.WATCHBOX_MANAGEMENT_ROLE_REQUIRED, "layerIds");

    insertUser(ADMIN_ID);
    var adminCreate =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(GLOBAL_ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isCreated())
            .andReturn();
    var created = readResponse(adminCreate, WatchboxResponse.class);
    assertThat(created.capabilities()).isEqualTo(new WatchboxCapabilities(true, true));
    assertReadOnlyCapabilities(OWNER_AUTH, created.id());

    assertManagementMutationsDenied(OWNER_AUTH, created.id());

    if (allGlobal) {
      // No explicit membership on either layer: visibility comes only from GLOBAL.
      assertReadOnlyCapabilities(VIEWER_AUTH, created.id());
    } else {
      mockMvc
          .perform(
              get("/v1/watchboxes/{watchboxId}", created.id()).with(authentication(VIEWER_AUTH)))
          .andExpect(status().isNotFound());
      var listed =
          mockMvc
              .perform(get("/v1/watchboxes").with(authentication(VIEWER_AUTH)))
              .andExpect(status().isOk())
              .andReturn();
      assertThat(readResponse(listed, WatchboxResponse[].class)).isEmpty();
    }
  }

  @Test
  void create_globalLayerWithOnlyArchivedSource_rejectsWhenNoEligibleSourcesRemain()
      throws Exception {
    makeLayerGlobal(layerId);
    dataSourceRepository.archive(SOURCE_ID, OWNER_ID);
    assertThat(connectionFixtures.countArchivedSources(SOURCE_ID)).isEqualTo(1);

    var result =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest("global-ineligible-source")))
            .andExpect(status().isConflict())
            .andReturn();

    assertWatchboxError(result, WatchboxErrorCode.WATCHBOX_NO_ELIGIBLE_SOURCES, "layerIds");
  }

  @Test
  void patchAndCleanup_whenStoredLayerDriftsToGlobal_succeedThroughRealWiring() throws Exception {
    var created = createWatchbox("global-drift-cleanup-proof");
    makeLayerGlobal(layerId);

    var editResult =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\":\"edited after scope change\"}"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(readResponse(editResult, WatchboxResponse.class).description())
        .isEqualTo("edited after scope change");

    var disableResult =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":false}"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(readResponse(disableResult, WatchboxResponse.class).enabled()).isFalse();

    mockMvc
        .perform(
            delete("/v1/watchboxes/{watchboxId}", created.id())
                .with(csrf())
                .with(authentication(OWNER_AUTH)))
        .andExpect(status().isNoContent());
  }

  @Test
  void watchboxSchemaRejectsArchivePairDrift() throws Exception {
    var created = createWatchbox("archive-pair-proof");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE watchbox.watchbox SET archived_at = now() WHERE id = ?", created.id()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE watchbox.watchbox SET archived_by = ? WHERE id = ?",
                    OWNER_ID,
                    created.id()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void sourceStatusSchemaRejectsInvalidEnumsAndNegativeCounts() throws Exception {
    var created = createWatchbox("source-status-constraint-proof");

    assertThatThrownBy(
            () -> insertSourceStatus(created.id(), "UNKNOWN", "WATCHBOX_DISABLED", null, null))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () ->
                insertSourceStatus(
                    created.id(), "ACTIVE", "WATCHBOX_REASON_FROM_THE_FUTURE", null, null))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () -> insertSourceStatus(created.id(), "ACTIVE", "WATCHBOX_DISABLED", -1L, null))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () -> insertSourceStatus(created.id(), "ACTIVE", "WATCHBOX_DISABLED", null, -1L))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);

    insertSourceStatus(created.id(), "ACTIVE", "WATCHBOX_DISABLED", 0L, 1000L);
    assertThat(countSourceStatuses(created.id())).isEqualTo(1);
  }

  @Test
  void missingDetectionHeartbeatDegradesStatusAsNotRunning() throws Exception {
    var created = createWatchbox("liveness-honesty-proof");
    assertThat(created.alertingStatus()).isEqualTo(WatchboxAlertingStatus.ACTIVE);

    // The Processor writes only problem rows, so with no live detector the rows cannot carry the
    // bad news themselves - liveness is what keeps an empty status list from reading as green.
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_detection_heartbeat");

    var degradedResult =
        mockMvc
            .perform(
                get("/v1/watchboxes/{watchboxId}", created.id()).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var degraded = readResponse(degradedResult, WatchboxResponse.class);
    assertThat(degraded.alertingStatus()).isEqualTo(WatchboxAlertingStatus.DEGRADED);
    assertThat(degraded.alertingReasons())
        .containsExactly(WatchboxStatusReason.WATCHBOX_DETECTION_NOT_RUNNING);

    seedFreshDetectionHeartbeat();

    var recoveredResult =
        mockMvc
            .perform(
                get("/v1/watchboxes/{watchboxId}", created.id()).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(readResponse(recoveredResult, WatchboxResponse.class).alertingStatus())
        .isEqualTo(WatchboxAlertingStatus.ACTIVE);
  }

  @Test
  void stalledRetainedRetryOnAFreshHeartbeatDegradesStatusAsPartiallyStalled() throws Exception {
    var created = createWatchbox("partial-stall-proof");
    assertThat(created.alertingStatus()).isEqualTo(WatchboxAlertingStatus.ACTIVE);

    // Detection is alive (fresh, assigned heartbeat) but one listener partition has been wedged
    // in retained retry-in-place past the degrade threshold: the row itself stays green-fresh, so
    // only the retained_retry_* facts can carry the bad news to the rollup.
    seedStalledDetectionHeartbeat();

    var stalledResult =
        mockMvc
            .perform(
                get("/v1/watchboxes/{watchboxId}", created.id()).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var stalled = readResponse(stalledResult, WatchboxResponse.class);
    assertThat(stalled.alertingStatus()).isEqualTo(WatchboxAlertingStatus.DEGRADED);
    assertThat(stalled.alertingReasons())
        .containsExactly(WatchboxStatusReason.WATCHBOX_DETECTION_PARTIALLY_STALLED);

    // A fresh heartbeat without wedge facts restores ACTIVE - the degradation is read-time-only.
    seedFreshDetectionHeartbeat();

    var recoveredResult =
        mockMvc
            .perform(
                get("/v1/watchboxes/{watchboxId}", created.id()).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(readResponse(recoveredResult, WatchboxResponse.class).alertingStatus())
        .isEqualTo(WatchboxAlertingStatus.ACTIVE);
  }

  @Test
  void hardDeletingSourceCascadesSourceStatusButLeavesWatchbox() throws Exception {
    var created = createWatchbox("source-status-cascade-proof");
    insertSourceStatus(created.id(), "ACTIVE", "WATCHBOX_DISABLED", 0L, 1000L);

    jdbcTemplate.update("DELETE FROM profiles.data_source WHERE id = ?", SOURCE_ID);

    assertThat(countSourceStatuses(created.id())).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM watchbox.watchbox WHERE id = ?", Integer.class, created.id()))
        .isEqualTo(1);
  }

  @Test
  void patchAndDeleteVisibleUnmanageableWatchboxReturnWatchboxErrorEnvelope() throws Exception {
    var created = createWatchbox("management-denial-proof");
    layerFixtures.addMember(layerId, VIEWER_ID, LayerRole.MEMBER);

    var patchResult =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(VIEWER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\":\"viewer cannot manage\"}"))
            .andExpect(status().isForbidden())
            .andReturn();
    assertWatchboxError(
        patchResult, WatchboxErrorCode.WATCHBOX_MANAGEMENT_ROLE_REQUIRED, "layerIds");

    var deleteResult =
        mockMvc
            .perform(
                delete("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(VIEWER_AUTH)))
            .andExpect(status().isForbidden())
            .andReturn();
    assertWatchboxError(
        deleteResult, WatchboxErrorCode.WATCHBOX_MANAGEMENT_ROLE_REQUIRED, "layerIds");
  }

  @Test
  void readAndManage_whenViewerCannotSeeEveryLayer_hideWatchboxAsNotFound() throws Exception {
    var otherLayerId =
        layerFixtures.persistLayerWithMembers(
            "Partial Viewer Hidden Layer", Map.of(OWNER_ID, LayerRole.OWNER));
    layerFixtures.addServerAccess(SOURCE_ID, otherLayerId, ServerRole.VIEWER);
    layerFixtures.addMember(layerId, VIEWER_ID, LayerRole.MEMBER);
    var created = createTwoLayerWatchbox("partial-viewer-proof", otherLayerId);
    var descriptionBefore =
        jdbcTemplate.queryForObject(
            "SELECT description FROM watchbox.watchbox WHERE id = ?", String.class, created.id());

    var listResult =
        mockMvc
            .perform(get("/v1/watchboxes").with(authentication(VIEWER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var responses =
        objectMapper.readValue(
            listResult.getResponse().getContentAsString(), WatchboxResponse[].class);
    assertThat(responses).extracting(WatchboxResponse::id).doesNotContain(created.id());

    var getResult =
        mockMvc
            .perform(
                get("/v1/watchboxes/{watchboxId}", created.id()).with(authentication(VIEWER_AUTH)))
            .andExpect(status().isNotFound())
            .andReturn();
    assertNotFoundError(getResult);

    var patchResult =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(VIEWER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\":\"must remain hidden\"}"))
            .andExpect(status().isNotFound())
            .andReturn();
    assertNotFoundError(patchResult);

    var deleteResult =
        mockMvc
            .perform(
                delete("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(VIEWER_AUTH)))
            .andExpect(status().isNotFound())
            .andReturn();
    assertNotFoundError(deleteResult);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT description FROM watchbox.watchbox WHERE id = ?",
                String.class,
                created.id()))
        .isEqualTo(descriptionBefore);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT archived_at FROM watchbox.watchbox WHERE id = ?",
                Instant.class,
                created.id()))
        .isNull();
  }

  @Test
  void createRollsBackAndReturnsForbiddenWhenPostWriteVisibilityIsLost() throws Exception {
    doReturn(java.util.Optional.empty())
        .when(responseAssembler)
        .assembleVisible(any(), any(WatchboxCaller.class), any());

    var result =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest("auth-loss-create-proof")))
            .andExpect(status().isForbidden())
            .andReturn();
    assertWatchboxError(result, WatchboxErrorCode.WATCHBOX_MANAGEMENT_ROLE_REQUIRED, "layerIds");

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM watchbox.watchbox WHERE name = ?",
                Integer.class,
                "auth-loss-create-proof"))
        .isZero();
  }

  @Test
  void patchLeavesRowUnchangedAndReturnsForbiddenWhenPostWriteVisibilityIsLost() throws Exception {
    var created = createWatchbox("auth-loss-patch-proof");
    var rowBefore =
        jdbcTemplate.queryForMap(
            "SELECT name, description, direction FROM watchbox.watchbox WHERE id = ?",
            created.id());

    doReturn(java.util.Optional.empty())
        .when(responseAssembler)
        .assembleVisible(any(), any(WatchboxCaller.class), any());

    var result =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\":\"should not persist\"}"))
            .andExpect(status().isForbidden())
            .andReturn();
    assertWatchboxError(result, WatchboxErrorCode.WATCHBOX_MANAGEMENT_ROLE_REQUIRED, "layerIds");

    var rowAfter =
        jdbcTemplate.queryForMap(
            "SELECT name, description, direction FROM watchbox.watchbox WHERE id = ?",
            created.id());
    assertThat(rowAfter).isEqualTo(rowBefore);
  }

  @Test
  void listReturnsDistinctLayerSetsForMultipleWatchboxes() throws Exception {
    UUID otherLayerId =
        layerFixtures.persistLayerWithMembers(
            "Watchbox Layer 2", Map.of(OWNER_ID, LayerRole.OWNER));
    layerFixtures.addServerAccess(SOURCE_ID, otherLayerId, ServerRole.VIEWER);
    var first = createWatchbox("list-batch-proof-1");
    var secondResult =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        validCreateRequest("list-batch-proof-2")
                            .replace(layerId.toString(), otherLayerId.toString())))
            .andExpect(status().isCreated())
            .andReturn();
    var second = readResponse(secondResult, WatchboxResponse.class);

    var listResult =
        mockMvc
            .perform(get("/v1/watchboxes").with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    WatchboxResponse[] responses =
        objectMapper.readValue(
            listResult.getResponse().getContentAsString(), WatchboxResponse[].class);
    assertThat(responses)
        .extracting(WatchboxResponse::id)
        .containsExactlyInAnyOrder(first.id(), second.id());
    assertThat(responses)
        .anySatisfy(response -> assertThat(response.layerIds()).containsExactly(layerId));
    assertThat(responses)
        .anySatisfy(response -> assertThat(response.layerIds()).containsExactly(otherLayerId));
  }

  @Test
  void capabilities_reflectManagementAuthorizationForEachCallerArchetype() throws Exception {
    var created = createWatchbox("capabilities-archetype-proof");
    assertThat(created.capabilities()).isEqualTo(new WatchboxCapabilities(true, true));

    layerFixtures.addMember(layerId, VIEWER_ID, LayerRole.MEMBER);
    // The principal stamp in oidcGlobalAdmin is the sole admin authority this filter chain
    // consults; the user row deliberately stays administrator=false to prove the DB column is
    // not read on the request path.
    insertUser(ADMIN_ID);

    assertThat(listedCapabilities(OWNER_AUTH, created.id()))
        .isEqualTo(new WatchboxCapabilities(true, true));
    assertThat(fetchedCapabilities(OWNER_AUTH, created.id()))
        .isEqualTo(new WatchboxCapabilities(true, true));

    // Fully visible non-manager: sees the watchbox but may not manage it.
    assertThat(listedCapabilities(VIEWER_AUTH, created.id()))
        .isEqualTo(new WatchboxCapabilities(false, false));
    assertThat(fetchedCapabilities(VIEWER_AUTH, created.id()))
        .isEqualTo(new WatchboxCapabilities(false, false));

    // Global admin with no layer membership: management via the admin bypass.
    assertThat(listedCapabilities(GLOBAL_ADMIN_AUTH, created.id()))
        .isEqualTo(new WatchboxCapabilities(true, true));
    assertThat(fetchedCapabilities(GLOBAL_ADMIN_AUTH, created.id()))
        .isEqualTo(new WatchboxCapabilities(true, true));
  }

  @Test
  void capabilities_mixedRolesAcrossLayers_readFalseWhileWatchboxStaysVisible() throws Exception {
    var otherLayerId =
        layerFixtures.persistLayerWithMembers(
            "Mixed Role Layer", Map.of(OWNER_ID, LayerRole.OWNER));
    layerFixtures.addServerAccess(SOURCE_ID, otherLayerId, ServerRole.VIEWER);
    var created = createTwoLayerWatchbox("capabilities-mixed-role-proof", otherLayerId);
    layerFixtures.addMember(layerId, VIEWER_ID, LayerRole.ADMIN);
    layerFixtures.addMember(otherLayerId, VIEWER_ID, LayerRole.MEMBER);

    // ADMIN on one selected layer plus MEMBER on the other keeps the watchbox fully visible but
    // must advertise no management capability: managing requires OWNER/ADMIN on every layer.
    assertThat(listedCapabilities(VIEWER_AUTH, created.id()))
        .isEqualTo(new WatchboxCapabilities(false, false));
    assertThat(fetchedCapabilities(VIEWER_AUTH, created.id()))
        .isEqualTo(new WatchboxCapabilities(false, false));
  }

  @Test
  void capabilities_ignoreCreatorIdentityWhenLayerRolesAreDowngraded() throws Exception {
    var created = createWatchbox("capabilities-creator-proof");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT created_by FROM watchbox.watchbox WHERE id = ?", UUID.class, created.id()))
        .isEqualTo(OWNER_ID);

    jdbcTemplate.update(
        "UPDATE iam.layer_member SET role = 'MEMBER'::iam.layer_role WHERE layer_id = ? AND user_id = ?",
        layerId,
        OWNER_ID);

    // Creator identity is audit provenance, not authorization: with only MEMBER access the
    // creator still sees the watchbox but may no longer manage it.
    assertThat(listedCapabilities(OWNER_AUTH, created.id()))
        .isEqualTo(new WatchboxCapabilities(false, false));
    assertThat(fetchedCapabilities(OWNER_AUTH, created.id()))
        .isEqualTo(new WatchboxCapabilities(false, false));

    mockMvc
        .perform(
            patch("/v1/watchboxes/{watchboxId}", created.id())
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"creator cannot manage\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            delete("/v1/watchboxes/{watchboxId}", created.id())
                .with(csrf())
                .with(authentication(OWNER_AUTH)))
        .andExpect(status().isForbidden());
  }

  /**
   * List filters superseded status rows in memory from one bulk read while get filters them in SQL
   * per watchbox, so the epoch rule now has two implementations that must agree: a current-epoch
   * row degrades the rollup for every caller and carries its source detail to managers only, and a
   * row stranded at a superseded epoch reaches neither. This is the only assertion that reads
   * persisted status content back through a response - the statement budgets bound cost from above,
   * so a dropout in either implementation lowers the count rather than raising it and would
   * otherwise pass them unnoticed.
   */
  @Test
  void sourceStatuses_currentEpochOnly_agreeAcrossListAndGet() throws Exception {
    layerFixtures.addMember(layerId, VIEWER_ID, LayerRole.MEMBER);
    var current = createWatchbox("status-current-epoch-proof");
    var superseded = createWatchbox("status-superseded-epoch-proof");
    assertThat(resetTracking(superseded.id()).configEpoch()).isEqualTo(2);

    // Both rows are written at epoch 1: current for the first watchbox, stranded for the second.
    insertSourceStatus(
        current.id(), "DEGRADED", "WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN", 7L, 1000L);
    insertSourceStatus(
        superseded.id(), "DEGRADED", "WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN", 7L, 1000L);

    for (WatchboxResponse manager :
        List.of(listed(OWNER_AUTH, current.id()), fetched(OWNER_AUTH, current.id()))) {
      assertThat(manager.alertingStatus()).isEqualTo(WatchboxAlertingStatus.DEGRADED);
      assertThat(manager.alertingReasons())
          .containsExactly(WatchboxStatusReason.WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN);
      assertThat(manager.sourceStatuses()).hasSize(1);
      var status = manager.sourceStatuses().getFirst();
      assertThat(status.sourceId()).isEqualTo(SOURCE_ID);
      assertThat(status.sourceName()).isEqualTo("watchbox-source");
      assertThat(status.status()).isEqualTo(WatchboxAlertingStatus.DEGRADED);
      assertThat(status.reason())
          .isEqualTo(WatchboxStatusReason.WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN);
      assertThat(status.observedCount()).isEqualTo(7L);
      assertThat(status.cap()).isEqualTo(1000L);
    }

    for (WatchboxResponse stale :
        List.of(listed(OWNER_AUTH, superseded.id()), fetched(OWNER_AUTH, superseded.id()))) {
      assertThat(stale.alertingStatus()).isEqualTo(WatchboxAlertingStatus.ACTIVE);
      assertThat(stale.alertingReasons()).isEmpty();
      assertThat(stale.sourceStatuses()).isEmpty();
    }

    // Fully visible non-manager: same rollup, no source detail.
    for (WatchboxResponse viewer :
        List.of(listed(VIEWER_AUTH, current.id()), fetched(VIEWER_AUTH, current.id()))) {
      assertThat(viewer.alertingStatus()).isEqualTo(WatchboxAlertingStatus.DEGRADED);
      assertThat(viewer.alertingReasons())
          .containsExactly(WatchboxStatusReason.WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN);
      assertThat(viewer.sourceStatuses()).isEmpty();
    }
  }

  /**
   * Advances the config epoch the way production does: a confirmed tracking reset allocates
   * read-epoch + 1, stranding any status row already written against the previous epoch.
   */
  private WatchboxResponse resetTracking(UUID watchboxId) throws Exception {
    var result =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", watchboxId)
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"polygon":{"type":"Polygon","coordinates":[[[-100.1,39.9],[-99.9,39.9],[-99.9,40.1],[-100.1,40.1],[-100.1,39.9]]]},"confirmTrackingReset":true}
                        """))
            .andExpect(status().isOk())
            .andReturn();
    return readResponse(result, WatchboxResponse.class);
  }

  private WatchboxResponse listed(Authentication auth, UUID watchboxId) throws Exception {
    var listResult =
        mockMvc
            .perform(get("/v1/watchboxes").with(authentication(auth)))
            .andExpect(status().isOk())
            .andReturn();
    var responses =
        objectMapper.readValue(
            listResult.getResponse().getContentAsString(), WatchboxResponse[].class);
    return Arrays.stream(responses)
        .filter(response -> response.id().equals(watchboxId))
        .findFirst()
        .orElseThrow();
  }

  private WatchboxResponse fetched(Authentication auth, UUID watchboxId) throws Exception {
    var getResult =
        mockMvc
            .perform(get("/v1/watchboxes/{watchboxId}", watchboxId).with(authentication(auth)))
            .andExpect(status().isOk())
            .andReturn();
    return readResponse(getResult, WatchboxResponse.class);
  }

  private WatchboxCapabilities listedCapabilities(Authentication auth, UUID watchboxId)
      throws Exception {
    return listed(auth, watchboxId).capabilities();
  }

  private WatchboxCapabilities fetchedCapabilities(Authentication auth, UUID watchboxId)
      throws Exception {
    return fetched(auth, watchboxId).capabilities();
  }

  @Test
  void responses_whenCreatedAndListed_omitRetiredRecipientAudienceFields() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest("recipient-fields-retired-proof")))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
    assertRetiredRecipientAudienceFieldsAbsent(created);

    var listResult =
        mockMvc
            .perform(get("/v1/watchboxes").with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode listed = null;
    for (JsonNode item : objectMapper.readTree(listResult.getResponse().getContentAsString())) {
      if (created.get("id").equals(item.get("id"))) {
        listed = item;
        break;
      }
    }
    assertThat(listed).isNotNull();
    assertRetiredRecipientAudienceFieldsAbsent(listed);
  }

  @Test
  void createAndEnabledPatchSucceedAboveRetiredRecipientCap() throws Exception {
    // The retired recipient cap rejected create and enabled patch with a 409
    // (WATCHBOX_RECIPIENT_CAP_EXCEEDED) once a watchbox's layers reached 1,001 effective members
    // (the cap was 1,000). One past that former boundary must now behave exactly like a small
    // audience.
    seedAdditionalLayerMembers(layerId, 1_000);
    assertThat(countLayerMembers(layerId)).isEqualTo(1_001);

    var created = createWatchbox("former-recipient-cap-boundary-proof");
    assertThat(created.enabled()).isTrue();
    assertThat(created.layerIds()).containsExactly(layerId);

    // Explicit layerIds keeps the patch on the enabled-validation path that used to re-run the
    // cap check over the requested layer set. Same layer set, so no tracking reset is involved.
    var patchResult =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"description":"still enabled above the former cap","layerIds":["%s"]}
                        """
                            .formatted(layerId)))
            .andExpect(status().isOk())
            .andReturn();
    var patched = readResponse(patchResult, WatchboxResponse.class);
    assertThat(patched.enabled()).isTrue();
    assertThat(patched.layerIds()).containsExactly(layerId);
  }

  @Test
  void generatedOpenApiOmitsRetiredRecipientContract() throws Exception {
    var apiDocsResult =
        mockMvc
            .perform(get("/v3/api-docs").with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var document = apiDocsResult.getResponse().getContentAsString();
    JsonNode apiDocs = objectMapper.readTree(document);

    JsonNode watchboxResponseSchema =
        apiDocs.path("components").path("schemas").path("WatchboxResponse");
    assertThat(watchboxResponseSchema.isMissingNode()).isFalse();
    JsonNode properties = watchboxResponseSchema.path("properties");
    assertThat(properties.has("layerIds")).isTrue();
    assertThat(properties.has("capabilities")).isTrue();
    assertThat(properties.has("hasOmittedLayerIds")).isFalse();
    assertThat(properties.has("effectiveRecipientCount")).isFalse();
    assertThat(properties.has("effectiveRecipientCountDisplay")).isFalse();
    assertThat(properties.has("recipientCap")).isFalse();

    JsonNode createProperties =
        apiDocs.path("components").path("schemas").path("WatchboxCreateRequest").path("properties");
    JsonNode patchProperties =
        apiDocs.path("components").path("schemas").path("WatchboxPatchRequest").path("properties");
    for (JsonNode requestProperties : java.util.List.of(createProperties, patchProperties)) {
      assertThat(requestProperties.path("name").path("maxLength").asInt()).isEqualTo(255);
      assertThat(requestProperties.path("description").path("maxLength").asInt()).isEqualTo(4_000);
      assertThat(requestProperties.path("layerIds").path("minItems").asInt()).isEqualTo(1);
      assertThat(requestProperties.path("layerIds").path("maxItems").asInt()).isEqualTo(100);
      assertThat(requestProperties.path("layerIds").path("uniqueItems").asBoolean()).isTrue();
      assertThat(requestProperties.path("layerIds").path("items").path("format").asText())
          .isEqualTo("uuid");
    }

    // Both enum families render their literals into the generated document (error codes via
    // WatchboxErrorResponse, status reasons via alerting fields), so a whole-document scan is
    // meaningful: prove one live literal from each family is present, then that the retired pair
    // is gone everywhere - schemas and examples alike.
    assertThat(document).contains("WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED");
    assertThat(document).contains("WATCHBOX_SOURCE_ELIGIBILITY_FAILED");
    assertThat(document).contains("admission-source-a", "admission-source-b");
    assertThat(document).contains("WATCHBOX_DISABLED");
    assertThat(document).doesNotContain("WATCHBOX_RECIPIENT_CAP_EXCEEDED");
    assertThat(document).doesNotContain("WATCHBOX_AUDIENCE_UNBOUNDED");
  }

  @Test
  void createReadPatchAndDeleteWatchbox() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name":"phase-1-proof",
                          "description":"REST/config proof",
                          "direction":"BOTH",
                          "layerIds":["%s"],
                          "polygon":{"type":"Polygon","coordinates":[[[-100.2,39.8],[-99.8,39.8],[-99.8,40.2],[-100.2,40.2],[-100.2,39.8]]]},
                          "filter":{"combine":"ALL","predicates":[{"path":"properties.opencop.element.symbol","operation":"SUBSTRING_IN","offset":1,"length":1,"values":["h"]}]},
                          "enabled":true
                        }
                        """
                            .formatted(layerId)))
            .andExpect(status().isCreated())
            .andReturn();

    var created = readResponse(createResult, WatchboxResponse.class);
    assertThat(created.id()).isNotNull();
    assertThat(created.configEpoch()).isEqualTo(1);
    assertThat(created.layerIds()).containsExactly(layerId);
    assertThat(created.alertingStatus()).isEqualTo(WatchboxAlertingStatus.ACTIVE);

    var readResult =
        mockMvc
            .perform(
                get("/v1/watchboxes/{watchboxId}", created.id()).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(readResponse(readResult, WatchboxResponse.class).id()).isEqualTo(created.id());

    var rejectedPatch =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"polygon":{"type":"Polygon","coordinates":[[[-100.1,39.9],[-99.9,39.9],[-99.9,40.1],[-100.1,40.1],[-100.1,39.9]]]}}
                        """))
            .andExpect(status().isConflict())
            .andReturn();
    var error = readResponse(rejectedPatch, WatchboxErrorResponse.class);
    assertThat(error.code())
        .isEqualTo(WatchboxErrorCode.WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED);
    assertThat(error.fields()).contains("polygon");

    var confirmedPatch =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"polygon":{"type":"Polygon","coordinates":[[[-100.1,39.9],[-99.9,39.9],[-99.9,40.1],[-100.1,40.1],[-100.1,39.9]]]},"confirmTrackingReset":true}
                        """))
            .andExpect(status().isOk())
            .andReturn();
    var patched = readResponse(confirmedPatch, WatchboxResponse.class);
    assertThat(patched.configEpoch()).isEqualTo(2);
    assertThat(patched.layerIds()).containsExactly(layerId);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM watchbox.watchbox WHERE id = ? AND archived_at IS NULL",
                Integer.class,
                created.id()))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM watchbox.watchbox_layer WHERE watchbox_id = ?",
                Integer.class,
                created.id()))
        .isEqualTo(1);

    mockMvc
        .perform(
            delete("/v1/watchboxes/{watchboxId}", created.id())
                .with(csrf())
                .with(authentication(OWNER_AUTH)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/v1/watchboxes/{watchboxId}", created.id()).with(authentication(OWNER_AUTH)))
        .andExpect(status().isNotFound());
  }

  @Test
  void concurrentResetPatchesSerializeOntoDistinctEpochs() throws Exception {
    // Two overlapping reset patches must never allocate the same tracking generation: processor
    // state is keyed by epoch and the V37 guard only rejects decreases, so an equal-epoch pair
    // would silently cross-seed inside/outside state between two different configurations. The
    // patch path's locked read serializes epoch allocation on the config row.
    var created = createWatchbox("epoch-race-proof");

    var lockHeld = new CountDownLatch(1);
    var releaseLock = new CountDownLatch(1);
    var holder =
        CompletableFuture.runAsync(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        _ -> {
                          jdbcTemplate.queryForObject(
                              "SELECT config_epoch FROM watchbox.watchbox WHERE id = ? FOR UPDATE",
                              Long.class,
                              created.id());
                          lockHeld.countDown();
                          awaitOrFail(releaseLock);
                          jdbcTemplate.update(
                              "UPDATE watchbox.watchbox SET config_epoch = 2 WHERE id = ?",
                              created.id());
                        }));
    assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();

    var racingPatch =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return mockMvc
                    .perform(
                        patch("/v1/watchboxes/{watchboxId}", created.id())
                            .with(csrf())
                            .with(authentication(OWNER_AUTH))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                """
                                {"polygon":{"type":"Polygon","coordinates":[[[-100.1,39.9],[-99.9,39.9],[-99.9,40.1],[-100.1,40.1],[-100.1,39.9]]]},"confirmTrackingReset":true}
                                """))
                    .andReturn();
              } catch (Exception exception) {
                throw new IllegalStateException("racing patch failed", exception);
              }
            });

    // The patch must be waiting behind the held row lock, not proceeding from a pre-commit read.
    Thread.sleep(500);
    assertThat(racingPatch.isDone()).isFalse();

    releaseLock.countDown();
    holder.join();

    var result = racingPatch.join();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    // The locked read observes the committed generation (2) and allocates 3. An unlocked read
    // would compute 1 + 1 = 2, which the equality-tolerant V37 guard happily commits.
    assertThat(readResponse(result, WatchboxResponse.class).configEpoch()).isEqualTo(3);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT config_epoch FROM watchbox.watchbox WHERE id = ?",
                Long.class,
                created.id()))
        .isEqualTo(3);
  }

  @Test
  void archive_whenUpdateHoldsLock_waitsAndSnapshotsCommittedConfiguration() throws Exception {
    var created = createWatchbox("archive-lock-proof");
    var committedDescription = "committed immediately before archive";
    var lockHeld = new CountDownLatch(1);
    var releaseLock = new CountDownLatch(1);
    var holder =
        CompletableFuture.runAsync(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        _ -> {
                          jdbcTemplate.queryForObject(
                              "SELECT config_epoch FROM watchbox.watchbox WHERE id = ? FOR UPDATE",
                              Long.class,
                              created.id());
                          lockHeld.countDown();
                          awaitOrFail(releaseLock);
                          jdbcTemplate.update(
                              """
                              UPDATE watchbox.watchbox
                                 SET description = ?, updated_at = now()
                               WHERE id = ?
                              """,
                              committedDescription,
                              created.id());
                        }));

    CompletableFuture<MvcResult> racingArchive = null;
    try {
      assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
      racingArchive =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  return mockMvc
                      .perform(
                          delete("/v1/watchboxes/{watchboxId}", created.id())
                              .with(csrf())
                              .with(authentication(OWNER_AUTH)))
                      .andReturn();
                } catch (Exception exception) {
                  throw new IllegalStateException("racing archive failed", exception);
                }
              });

      Thread.sleep(500);
      assertThat(racingArchive.isDone()).isFalse();
    } finally {
      releaseLock.countDown();
    }

    holder.join();
    var result = racingArchive.join();
    assertThat(result.getResponse().getStatus()).isEqualTo(204);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT description FROM watchbox.watchbox WHERE id = ? AND archived_at IS NOT NULL",
                String.class,
                created.id()))
        .isEqualTo(committedDescription);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT before_snapshot ->> 'description'
                  FROM watchbox.watchbox_config_audit
                 WHERE watchbox_id = ? AND operation = 'ARCHIVE'
                """,
                String.class,
                created.id()))
        .isEqualTo(committedDescription);
  }

  private static void awaitOrFail(CountDownLatch latch) {
    try {
      if (!latch.await(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException("timed out waiting for latch");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  @AfterEach
  void tearDown() {
    resetWatchboxes();
  }

  private void resetWatchboxes() {
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_detection_heartbeat");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_config_audit");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_source_status");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_layer");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox");
  }

  /**
   * The status rollup fails closed on detection liveness, so tests asserting ACTIVE need a live
   * Processor heartbeat on record - exactly what a healthy deployment has.
   */
  private void seedFreshDetectionHeartbeat() {
    jdbcTemplate.update(
        """
        INSERT INTO watchbox.watchbox_detection_heartbeat
          (instance_id, listener_started_at, last_heartbeat_at, heartbeat_expires_at,
           assigned_partitions, heartbeat_sequence)
        VALUES ('processor-it', now(), now(), now() + INTERVAL '60 seconds', 2, 1)
        ON CONFLICT (instance_id) DO UPDATE SET
          heartbeat_expires_at = now() + INTERVAL '60 seconds',
          stopped_at = NULL,
          -- The production upsert overwrites all four facts on every flush, so a healthy seed
          -- clears any wedge a previous seed left behind.
          retained_retry_partitions = NULL,
          retained_retry_reason = NULL,
          retained_retry_since = NULL,
          retained_retry_attempts = NULL
        """);
  }

  /**
   * A fresh, assigned heartbeat whose retained-retry episode started well past the 60s degrade
   * threshold: alive, but one partition wedged - the exact row shape a healthy sibling child keeps
   * writing while its peer retries in place.
   */
  private void seedStalledDetectionHeartbeat() {
    jdbcTemplate.update(
        """
        INSERT INTO watchbox.watchbox_detection_heartbeat
          (instance_id, listener_started_at, last_heartbeat_at, heartbeat_expires_at,
           assigned_partitions, heartbeat_sequence,
           retained_retry_partitions, retained_retry_reason, retained_retry_since,
           retained_retry_attempts)
        VALUES ('processor-it', now(), now(), now() + INTERVAL '60 seconds', 2, 1,
                1, 'GENERATION_CHANGED', now() - INTERVAL '2 minutes', 25)
        ON CONFLICT (instance_id) DO UPDATE SET
          heartbeat_expires_at = now() + INTERVAL '60 seconds',
          stopped_at = NULL,
          retained_retry_partitions = EXCLUDED.retained_retry_partitions,
          retained_retry_reason = EXCLUDED.retained_retry_reason,
          retained_retry_since = EXCLUDED.retained_retry_since,
          retained_retry_attempts = EXCLUDED.retained_retry_attempts
        """);
  }

  private void insertUser(UUID userId) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
            id, idp_user_id, idp_username, idp_issuer, username, active, administrator, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
        ON CONFLICT (id) DO NOTHING
        """,
        userId,
        "idp-user-" + userId,
        "user-" + userId,
        "issuer",
        "user-" + userId,
        true,
        false);
  }

  /**
   * Bulk-seeds distinct direct members onto a layer. Direct membership is the simplest form of
   * effective audience, which is what the retired recipient cap counted. A single JDBC batch keeps
   * a four-digit audience fast, and iam.layer_member has no FK to opencop_user, so the members need
   * no user rows.
   */
  private void seedAdditionalLayerMembers(UUID layerId, int memberCount) {
    var rows =
        IntStream.range(0, memberCount)
            .mapToObj(_ -> new Object[] {layerId, UUID.randomUUID()})
            .toList();
    jdbcTemplate.batchUpdate(
        "INSERT INTO iam.layer_member (layer_id, user_id) VALUES (?, ?)", rows);
  }

  private int countLayerMembers(UUID layerId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM iam.layer_member WHERE layer_id = ?", Integer.class, layerId);
  }

  private WatchboxResponse createWatchbox(String name) throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest(name)))
            .andExpect(status().isCreated())
            .andReturn();
    return readResponse(createResult, WatchboxResponse.class);
  }

  private WatchboxResponse createTwoLayerWatchbox(String name, UUID otherLayerId) throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(twoLayerCreateRequest(name, otherLayerId)))
            .andExpect(status().isCreated())
            .andReturn();
    return readResponse(createResult, WatchboxResponse.class);
  }

  private String twoLayerCreateRequest(String name, UUID otherLayerId) {
    return validCreateRequest(name)
        .replace(
            "\"layerIds\":[\"" + layerId + "\"]",
            "\"layerIds\":[\"" + layerId + "\",\"" + otherLayerId + "\"]");
  }

  private void assertReadOnlyCapabilities(Authentication auth, UUID watchboxId) throws Exception {
    assertThat(listedCapabilities(auth, watchboxId))
        .isEqualTo(new WatchboxCapabilities(false, false));
    assertThat(fetchedCapabilities(auth, watchboxId))
        .isEqualTo(new WatchboxCapabilities(false, false));
  }

  private void assertManagementMutationsDenied(Authentication auth, UUID watchboxId)
      throws Exception {
    var deniedPatch =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", watchboxId)
                    .with(csrf())
                    .with(authentication(auth))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":false}"))
            .andExpect(status().isForbidden())
            .andReturn();
    assertWatchboxError(
        deniedPatch, WatchboxErrorCode.WATCHBOX_MANAGEMENT_ROLE_REQUIRED, "layerIds");
    var deniedDelete =
        mockMvc
            .perform(
                delete("/v1/watchboxes/{watchboxId}", watchboxId)
                    .with(csrf())
                    .with(authentication(auth)))
            .andExpect(status().isForbidden())
            .andReturn();
    assertWatchboxError(
        deniedDelete, WatchboxErrorCode.WATCHBOX_MANAGEMENT_ROLE_REQUIRED, "layerIds");
  }

  private static void assertRetiredRecipientAudienceFieldsAbsent(JsonNode response) {
    assertThat(response.has("hasOmittedLayerIds")).isFalse();
    assertThat(response.has("effectiveRecipientCount")).isFalse();
    assertThat(response.has("effectiveRecipientCountDisplay")).isFalse();
    assertThat(response.has("recipientCap")).isFalse();
  }

  private void assertWatchboxError(MvcResult result, WatchboxErrorCode code, String field)
      throws Exception {
    var error = readResponse(result, WatchboxErrorResponse.class);
    assertThat(error.code()).isEqualTo(code);
    assertThat(error.fields()).containsExactly(field);
    assertThat(error.reasons()).isEmpty();
  }

  private void assertNotFoundError(MvcResult result) throws Exception {
    var error = readResponse(result, WatchboxErrorResponse.class);
    assertThat(error.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_NOT_FOUND);
    assertThat(error.message()).isEqualTo("Watchbox not found");
    assertThat(error.fields()).isEmpty();
    assertThat(error.reasons()).isEmpty();
  }

  private void assertUnreadableBodyError(MvcResult result) throws Exception {
    var error = readResponse(result, WatchboxErrorResponse.class);
    assertThat(error.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_INVALID_REQUEST);
    assertThat(error.message()).isEqualTo("Invalid request body");
    assertThat(error.fields()).containsExactly("body");
    assertThat(error.reasons()).isEmpty();
  }

  private void makeLayerGlobal(UUID layerId) {
    jdbcTemplate.update(
        """
        UPDATE iam.layer
           SET access_scope = CAST('GLOBAL' AS iam.layer_access_scope)
         WHERE id = ?
        """,
        layerId);
  }

  private void insertSourceStatus(
      UUID watchboxId, String status, String reasonCode, Long observedCount, Long cap) {
    jdbcTemplate.update(
        """
        INSERT INTO watchbox.watchbox_source_status (
            watchbox_id, config_epoch, source_id, status, reason_code, observed_count, cap
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        watchboxId,
        1L,
        SOURCE_ID,
        status,
        reasonCode,
        observedCount,
        cap);
  }

  private int countSourceStatuses(UUID watchboxId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM watchbox.watchbox_source_status WHERE watchbox_id = ?",
        Integer.class,
        watchboxId);
  }

  private int countAuditRows(UUID watchboxId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM watchbox.watchbox_config_audit WHERE watchbox_id = ?",
        Integer.class,
        watchboxId);
  }

  private int countAllAuditRows() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM watchbox.watchbox_config_audit", Integer.class);
  }

  private String validCreateRequest(String name) {
    return """
        {
          "name":"%s",
          "description":"REST/config proof",
          "direction":"BOTH",
          "layerIds":["%s"],
          "polygon":{"type":"Polygon","coordinates":[[[-100.2,39.8],[-99.8,39.8],[-99.8,40.2],[-100.2,40.2],[-100.2,39.8]]]},
          "filter":{"combine":"ALL","predicates":[{"path":"properties.opencop.element.symbol","operation":"IN","values":["10061000001211000000","10061000001212000000","10061000001207000000"]}]},
          "enabled":true
        }
        """
        .formatted(name, layerId);
  }

  private String createRequestWithoutName() {
    return """
        {
          "direction":"BOTH",
          "layerIds":["%s"],
          "polygon":{"type":"Polygon","coordinates":[[[-100.2,39.8],[-99.8,39.8],[-99.8,40.2],[-100.2,40.2],[-100.2,39.8]]]},
          "filter":{"predicates":[]},
          "enabled":true
        }
        """
        .formatted(layerId);
  }

  private String createRequestWithInvalidPolygon() {
    return """
        {
          "name":"invalid-polygon-proof",
          "direction":"BOTH",
          "layerIds":["%s"],
          "polygon":{"type":"Polygon","coordinates":[[[-100.2,39.8],[-99.8,39.8],[-99.8,40.2]]]},
          "filter":{"predicates":[]},
          "enabled":true
        }
        """
        .formatted(layerId);
  }

  private String createRequestWithInvalidFilter() {
    return """
        {
          "name":"invalid-filter-proof",
          "direction":"BOTH",
          "layerIds":["%s"],
          "polygon":{"type":"Polygon","coordinates":[[[-100.2,39.8],[-99.8,39.8],[-99.8,40.2],[-100.2,40.2],[-100.2,39.8]]]},
          "filter":{"combine":"ALL","predicates":[{"path":"properties.not.allowed","operation":"EXISTS","values":[]}]},
          "enabled":true
        }
        """
        .formatted(layerId);
  }

  private String createRequestWithFilterMissingCombine() {
    return """
        {
          "name":"missing-combine-proof",
          "direction":"BOTH",
          "layerIds":["%s"],
          "polygon":{"type":"Polygon","coordinates":[[[-100.2,39.8],[-99.8,39.8],[-99.8,40.2],[-100.2,40.2],[-100.2,39.8]]]},
          "filter":{"predicates":[{"path":"properties.opencop.kind","operation":"EXISTS","values":[]}]},
          "enabled":true
        }
        """
        .formatted(layerId);
  }

  private String filterTestRequest(String symbol) {
    return """
        {
          "filter": {
            "combine": "ALL",
            "predicates": [{
              "path": "properties.opencop.element.symbol",
              "operation": "SUBSTRING_IN",
              "offset": 1,
              "length": 1,
              "values": ["h"]
            }]
          },
          "feature": {
            "type": "Feature",
            "properties": {
              "opencop": {
                "element": {
                  "type": "symbol",
                  "symbol": "%s"
                }
              }
            },
            "geometry": {
              "type": "Point",
              "coordinates": [-100.0, 40.0]
            }
          }
        }
        """
        .formatted(symbol);
  }

  private String filterTestRequestWithPartialFeatureObject(String symbol) {
    return """
        {
          "filter": {
            "combine": "ALL",
            "predicates": [{
              "path": "properties.opencop.element.symbol",
              "operation": "SUBSTRING_IN",
              "offset": 1,
              "length": 1,
              "values": ["h"]
            }]
          },
          "feature": {
            "properties": {
              "opencop": {
                "element": {
                  "symbol": "%s"
                }
              }
            }
          }
        }
        """
        .formatted(symbol);
  }

  private String filterTestRequestMissingOffset() {
    return filterTestRequest("shgp-----------").replace("\"offset\": 1,", "");
  }

  private String filterTestRequestUnknownOperation() {
    return filterTestRequest("shgp-----------").replace("SUBSTRING_IN", "CONTAINS_ANY");
  }

  private String filterTestRequestUnknownRootField() {
    return filterTestRequest("shgp-----------")
        .replace("\"filter\":", "\"unexpected\": true, \"filter\":");
  }

  private String filterTestRequestUnknownFilterField() {
    return filterTestRequest("shgp-----------")
        .replace("\"predicates\":", "\"unexpected\": true, \"predicates\":");
  }

  private String filterTestRequestUnknownCombine() {
    return filterTestRequest("shgp-----------")
        .replace("\"combine\": \"ALL\"", "\"combine\": \"SOME\"");
  }

  private String filterTestRequestUnknownPredicateField() {
    return filterTestRequest("shgp-----------")
        .replace("\"values\": [\"h\"]", "\"values\": [\"h\"], \"unexpected\": true");
  }

  private String filterTestRequestMalformedFeature() {
    return """
        {
          "filter": {
            "combine": "ALL",
            "predicates": []
          },
          "feature": []
        }
        """;
  }
}
