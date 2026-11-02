// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static com.opencop.core.sdk.technical.mapper.engine.FeatureBuilder.DEFAULT_STALE_DAYS_OFFSET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.ports.gateway.repository.RepositoryException;
import com.opencop.core.sdk.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.core.sdk.technical.model.opencop.Contact;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.geojson.Feature;
import org.geojson.Point;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PostGisFeatureRecordMapperTest {

  private final PostGisFeatureRecordMapper mapper =
      new PostGisFeatureRecordMapper(new ObjectMapper());

  @Test
  void mapsFeatureIntoPersistableRecord() throws RepositoryException {
    var sourceId = UUID.randomUUID();
    var authorId = UUID.randomUUID();
    long now = System.currentTimeMillis();

    var record = mapper.toRecord(feature("persisted-feature", sourceId, authorId, now));

    assertEquals(
        UUID.nameUUIDFromBytes("persisted-feature".getBytes(StandardCharsets.UTF_8)), record.id());
    assertEquals("track", record.kind());
    assertEquals(sourceId, record.source());
    assertEquals(authorId, record.authorId());
    assertEquals("persisted-feature", record.objectId());
    assertEquals(Instant.ofEpochMilli(now), record.startAt());
    assertEquals(Instant.ofEpochMilli(now + 60_000), record.staleAt());
    assertTrue(record.geometryJson().contains("Point"));
    assertTrue(record.payloadJson().contains("persisted-feature"));
  }

  @Test
  void rejectsFeatureWithoutOpenCopProperties() {
    var feature = new Feature();
    feature.setId("missing-opencop-properties");
    feature.setGeometry(new Point(-75.1652, 39.9526));

    var exception = assertThrows(RepositoryException.class, () -> mapper.toRecord(feature));

    assertEquals(RepositoryExceptionCodes.BAD_REQUEST.code(), exception.getCode());
  }

  @Test
  void rejectsFeatureWithoutOpenCopId() {
    var exception =
        assertThrows(
            RepositoryException.class,
            () ->
                mapper.toRecord(
                    feature(
                        "missing-opencop-id",
                        null,
                        "missing-opencop-id",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        System.currentTimeMillis())));

    assertEquals(RepositoryExceptionCodes.BAD_REQUEST.code(), exception.getCode());
  }

  @Test
  void rejectsFeatureWithoutOpenCopSource() {
    var exception =
        assertThrows(
            RepositoryException.class,
            () ->
                mapper.toRecord(
                    feature(
                        "missing-opencop-source",
                        UUID.randomUUID(),
                        "missing-opencop-source",
                        null,
                        UUID.randomUUID(),
                        System.currentTimeMillis())));

    assertEquals(RepositoryExceptionCodes.BAD_REQUEST.code(), exception.getCode());
  }

  @Test
  void rejectsFeatureWithoutOpenCopObjectId() {
    var exception =
        assertThrows(
            RepositoryException.class,
            () ->
                mapper.toRecord(
                    feature(
                        "feature-without-object-id",
                        UUID.randomUUID(),
                        null,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        System.currentTimeMillis())));

    assertEquals(RepositoryExceptionCodes.BAD_REQUEST.code(), exception.getCode());
  }

  @Test
  void defaultsMissingOpenCopStartAtBeforeMapping() throws RepositoryException {
    long now = System.currentTimeMillis();
    var properties =
        new OpenCopProperties.Builder()
            .id(UUID.randomUUID())
            .objectId("missing-start-at")
            .kind("track")
            .source(UUID.randomUUID())
            .timestampz(new Timestampz(now, 0, now + 60_000))
            .build();
    var feature = OpenCopPropertiesParser.writeToFeature(properties, new Point(-75.1652, 39.9526));

    var record = mapper.toRecord(feature);
    var updatedProperties = OpenCopPropertiesParser.readFromFeature(feature);

    assertEquals(Instant.ofEpochMilli(now), record.startAt());
    assertEquals(now, updatedProperties.getTimestampz().getStart());
    assertTrue(record.payloadJson().contains("\"start\":" + now));
  }

  @Test
  void defaultsMissingOpenCopTimestampzBeforeMapping() throws RepositoryException {
    var properties =
        new OpenCopProperties.Builder()
            .id(UUID.randomUUID())
            .objectId("missing-timestampz")
            .kind("track")
            .source(UUID.randomUUID())
            .build();
    var feature = OpenCopPropertiesParser.writeToFeature(properties, new Point(-75.1652, 39.9526));

    var before = System.currentTimeMillis();
    var record = mapper.toRecord(feature);
    var after = System.currentTimeMillis();
    var updatedProperties = OpenCopPropertiesParser.readFromFeature(feature);

    assertTrue(!record.startAt().isBefore(Instant.ofEpochMilli(before)));
    assertTrue(!record.startAt().isAfter(Instant.ofEpochMilli(after)));
    assertEquals(
        record.startAt().plus(Duration.ofDays(DEFAULT_STALE_DAYS_OFFSET)), record.staleAt());
    assertEquals(
        record.startAt(), Instant.ofEpochMilli(updatedProperties.getTimestampz().getStart()));
    assertEquals(
        record.staleAt(), Instant.ofEpochMilli(updatedProperties.getTimestampz().getStale()));
    assertTrue(record.payloadJson().contains("\"timestampz\""));
  }

  private static Feature feature(String objectId, UUID sourceId, UUID authorId, long now) {
    return feature(
        objectId,
        UUID.nameUUIDFromBytes(objectId.getBytes(StandardCharsets.UTF_8)),
        objectId,
        sourceId,
        authorId,
        now);
  }

  private static Feature feature(
      String featureId, UUID opencopId, String objectId, UUID sourceId, UUID authorId, long now) {
    var properties =
        new OpenCopProperties.Builder()
            .id(opencopId)
            .objectId(objectId)
            .kind("track")
            .source(sourceId)
            .displayName(featureId)
            .author(new Contact.Builder().withUserId(authorId).build())
            .timestampz(new Timestampz(now, now, now + 60_000))
            .build();

    var feature = OpenCopPropertiesParser.writeToFeature(properties, new Point(-75.1652, 39.9526));
    feature.setId(featureId);
    return feature;
  }
}
