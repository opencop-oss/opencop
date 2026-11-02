// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import static com.opencop.core.api.event.business.usecase.event.HistoricalErrorCode.HISTORICAL_TOO_MANY_CHUNKS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.business.model.BoundingBox;
import com.opencop.core.api.event.business.model.HistogramBucketValue;
import com.opencop.core.api.event.business.model.HistogramMetric;
import com.opencop.core.api.event.business.model.SourceHistogramBucket;
import com.opencop.core.api.event.business.model.SourceHistogramResponse;
import com.opencop.core.api.event.business.policy.EventAuthorizationPolicy;
import com.opencop.core.api.event.business.policy.HistoricalTracksPreflightPlanner;
import com.opencop.core.api.event.business.policy.HistoricalTracksRequestPolicy;
import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RetrieveSourceHistogramUsecaseTest {

  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final Instant FROM = Instant.parse("2026-03-12T10:00:17Z");
  private static final Instant TO = Instant.parse("2026-03-12T10:03:02Z");
  private static final Instant EFFECTIVE_FROM = Instant.parse("2026-03-12T10:00:00Z");
  private static final Instant EFFECTIVE_TO = Instant.parse("2026-03-12T10:04:00Z");
  private static final int PLANNED_BARS = 4;
  private static final Duration PLANNED_BUCKET_WIDTH = Duration.ofMinutes(1);
  private static final long TOO_MANY_CHUNKS = 721;
  private static final Instant TOO_MANY_CHUNKS_FROM = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant TOO_MANY_CHUNKS_TO =
      TOO_MANY_CHUNKS_FROM.plus(TOO_MANY_CHUNKS, ChronoUnit.DAYS);
  private static final UUID USER_ID = UUID.randomUUID();

  private PostGisService service;
  private EventAuthorizationPolicy authorizationPolicy;
  private HistoricalTracksPreflightPlanner planner;
  private HistoricalTracksRequestPolicy requestPolicy;
  private RetrieveSourceHistogramUsecase usecase;

  @BeforeEach
  void setUp() {
    service = mock(PostGisService.class);
    authorizationPolicy = mock(EventAuthorizationPolicy.class);
    planner = new HistoricalTracksPreflightPlanner();
    requestPolicy = new HistoricalTracksRequestPolicy();
    usecase =
        new RetrieveSourceHistogramUsecase(service, authorizationPolicy, planner, requestPolicy);
  }

  @Test
  void getHistogram_delegatesPlannerAlignedWindowToService() throws PersistenceException {
    var principal = principal();
    var response =
        new SourceHistogramResponse(
            List.of(
                new SourceHistogramBucket(
                    EFFECTIVE_FROM,
                    EFFECTIVE_TO,
                    List.of(
                        new HistogramBucketValue(SOURCE_ID, HistogramMetric.ACTIVE, 1),
                        new HistogramBucketValue(SOURCE_ID, HistogramMetric.UPDATED, 1)))));
    when(service.getSourceHistogram(
            SOURCE_ID, EFFECTIVE_FROM, EFFECTIVE_TO, PLANNED_BARS, PLANNED_BUCKET_WIDTH, null))
        .thenReturn(response);

    var actual = usecase.getHistogram(principal, SOURCE_ID, FROM, TO, null);

    assertEquals(response.buckets(), actual.buckets());
    assertEquals(FROM, actual.requestedFrom());
    assertEquals(TO, actual.requestedTo());
    assertEquals(EFFECTIVE_FROM, actual.effectiveFrom());
    assertEquals(EFFECTIVE_TO, actual.effectiveTo());
    assertEquals(HistoricalResolution.MINUTE_1, actual.resolution());
    assertEquals(PLANNED_BARS, actual.chunks());
    assertEquals(PLANNED_BUCKET_WIDTH.toMillis(), actual.bucketWidthMillis());
    verify(authorizationPolicy).assertSourceMember(principal, SOURCE_ID);
    verify(service)
        .getSourceHistogram(
            SOURCE_ID, EFFECTIVE_FROM, EFFECTIVE_TO, PLANNED_BARS, PLANNED_BUCKET_WIDTH, null);
  }

  @Test
  void getHistogram_withBbox_delegatesToService() throws PersistenceException {
    var principal = principal();
    var bbox = new BoundingBox(-73.7, 45.4, -73.5, 45.6);
    var response =
        new SourceHistogramResponse(
            List.of(
                new SourceHistogramBucket(
                    EFFECTIVE_FROM,
                    EFFECTIVE_TO,
                    List.of(
                        new HistogramBucketValue(SOURCE_ID, HistogramMetric.ACTIVE, 1),
                        new HistogramBucketValue(SOURCE_ID, HistogramMetric.UPDATED, 1)))));
    when(service.getSourceHistogram(
            SOURCE_ID, EFFECTIVE_FROM, EFFECTIVE_TO, PLANNED_BARS, PLANNED_BUCKET_WIDTH, bbox))
        .thenReturn(response);

    var actual = usecase.getHistogram(principal, SOURCE_ID, FROM, TO, bbox);

    assertEquals(response.buckets(), actual.buckets());
    assertEquals(EFFECTIVE_FROM, actual.effectiveFrom());
    assertEquals(EFFECTIVE_TO, actual.effectiveTo());
    verify(service)
        .getSourceHistogram(
            SOURCE_ID, EFFECTIVE_FROM, EFFECTIVE_TO, PLANNED_BARS, PLANNED_BUCKET_WIDTH, bbox);
  }

  @Test
  void getHistogram_whenRangeIsInvalid_throwsBadRequest() {
    var principal = principal();

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.getHistogram(principal, SOURCE_ID, TO, FROM, null));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    assertEquals("'from' must be before 'to'", exception.getMessage());
  }

  @Test
  void getHistogram_whenPlannerProducesTooManyChunks_throwsBeforePostGis() {
    var principal = principal();

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                usecase.getHistogram(
                    principal, SOURCE_ID, TOO_MANY_CHUNKS_FROM, TOO_MANY_CHUNKS_TO, null));

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getHttpStatus());
    assertEquals(HISTORICAL_TOO_MANY_CHUNKS.name(), exception.getCode());
    verifyNoInteractions(service);
  }

  @Test
  void getHistogram_whenPersistenceFails_throwsInternalServerError() throws PersistenceException {
    var principal = principal();
    when(service.getSourceHistogram(
            SOURCE_ID, EFFECTIVE_FROM, EFFECTIVE_TO, PLANNED_BARS, PLANNED_BUCKET_WIDTH, null))
        .thenThrow(new PersistenceException("db failure"));

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.getHistogram(principal, SOURCE_ID, FROM, TO, null));

    assertEquals("CORE-0000", exception.getCode());
    assertEquals("db failure", exception.getMessage());
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatus());
  }

  @Test
  void getHistogram_whenAuthorizationFails_propagatesException() {
    var principal = principal();
    var exception =
        new GlobalOpenCopException(
            "User is not authorized to view data for this source", HttpStatus.FORBIDDEN);
    doThrow(exception).when(authorizationPolicy).assertSourceMember(principal, SOURCE_ID);

    var actual =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.getHistogram(principal, SOURCE_ID, FROM, TO, null));

    assertSame(exception, actual);
  }

  private static OpenCopPrincipal principal() {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID);
  }
}
