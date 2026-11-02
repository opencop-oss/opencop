// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import com.opencop.core.sdk.model.provenance.FeatureProvenance;
import com.opencop.core.sdk.ports.gateway.repository.RepositoryException;
import com.opencop.core.sdk.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.processor.business.usecase.event.FeatureStartAtSupport;
import com.opencop.processor.technical.gateway.model.PostGisFeatureRecord;
import java.time.Instant;
import org.geojson.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

public class PostGisFeatureRecordMapper {

  private static final Logger log = LoggerFactory.getLogger(PostGisFeatureRecordMapper.class);

  private final ObjectMapper objectMapper;

  public PostGisFeatureRecordMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Without provenance, for entry points outside the ingest pipeline. */
  public PostGisFeatureRecord toRecord(Feature feature) throws RepositoryException {
    return toRecord(feature, FeatureProvenance.NONE);
  }

  public PostGisFeatureRecord toRecord(Feature feature, FeatureProvenance provenance)
      throws RepositoryException {
    if (feature == null) {
      log.error("Could not map null GeoJSON feature into a persistable PostGIS record");
      throw new RepositoryException(RepositoryExceptionCodes.BAD_REQUEST);
    }

    OpenCopProperties properties = OpenCopPropertiesParser.readFromFeature(feature);
    if (properties == null) {
      log.error("Could not find OpenCOP properties inside GeoJSON");
      throw new RepositoryException(RepositoryExceptionCodes.BAD_REQUEST);
    }

    // Defensive: ensureStartAt also called in mapper,
    // but we set it early so the validated topic payload includes it
    FeatureStartAtSupport.ensureStartAt(properties);
    feature.setProperty(OpenCopPropertiesParser.OPENCOP_NODE, properties);

    if (properties.getTimestampz() == null) {
      log.error("Could not find OpenCOP timestampz inside GeoJSON");
      throw new RepositoryException(RepositoryExceptionCodes.BAD_REQUEST);
    }

    if (properties.getTimestampz().getStart() <= 0) {
      log.error("Could not find valid OpenCOP start timestamp inside GeoJSON");
      throw new RepositoryException(RepositoryExceptionCodes.BAD_REQUEST);
    }

    if (properties.getId() == null) {
      log.error("Could not find OpenCOP id inside GeoJSON");
      throw new RepositoryException(RepositoryExceptionCodes.BAD_REQUEST);
    }

    if (properties.getSource() == null) {
      log.error("Could not find OpenCOP source inside GeoJSON");
      throw new RepositoryException(RepositoryExceptionCodes.BAD_REQUEST);
    }

    if (properties.getObjectId() == null || properties.getObjectId().isBlank()) {
      log.error("Could not find OpenCOP objectId inside GeoJSON");
      throw new RepositoryException(RepositoryExceptionCodes.BAD_REQUEST);
    }

    return new PostGisFeatureRecord(
        properties.getId(),
        properties.getKind(),
        properties.getSource(),
        properties.getAuthor() != null ? properties.getAuthor().getUserId() : null,
        properties.getObjectId(),
        Instant.ofEpochMilli(properties.getTimestampz().getStart()),
        Instant.ofEpochMilli(properties.getTimestampz().getStale()),
        objectMapper.writeValueAsString(feature.getGeometry()),
        objectMapper.writeValueAsString(feature),
        provenance);
  }
}
