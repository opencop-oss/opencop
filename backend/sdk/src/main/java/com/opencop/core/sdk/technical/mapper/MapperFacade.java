// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper;

import static com.opencop.core.sdk.model.mapper.config.MapperFormat.KML_KMZ;

import com.opencop.core.sdk.helper.zip.GzipUtil;
import com.opencop.core.sdk.helper.zip.KmzUtil;
import com.opencop.core.sdk.model.mapper.MappedRecord;
import com.opencop.core.sdk.model.mapper.MappingError;
import com.opencop.core.sdk.model.mapper.MappingRequest;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.model.mapper.MappingWarning;
import com.opencop.core.sdk.model.mapper.config.MapperFormat;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import com.opencop.core.sdk.technical.mapper.engine.extractor.RecordExtractor;
import com.opencop.core.sdk.technical.mapper.engine.extractor.csv.CsvExtractor;
import com.opencop.core.sdk.technical.mapper.engine.extractor.json.JsonExtractor;
import com.opencop.core.sdk.technical.mapper.engine.extractor.kml.KmlExtractor;
import com.opencop.core.sdk.technical.mapper.engine.extractor.xml.XmlExtractor;
import com.opencop.core.sdk.technical.mapper.engine.session.MappingSession;
import com.opencop.core.sdk.technical.mapper.engine.transformer.TransformEngine;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class MapperFacade {

  private final TransformEngine transformEngine;

  MapperFacade(TransformEngine transformEngine) {
    this.transformEngine = transformEngine;
  }

  public MappingResponse map(MappingRequest request) {
    Objects.requireNonNull(request, "request");
    DynamicMapper profile = Objects.requireNonNull(request.profile(), "profile");
    Objects.requireNonNull(request.payloadBytes(), "payloadBytes");
    if (!profile.enabled()) {
      return new MappingResponse(
          profile.id(),
          profile.formatConfig().format(),
          List.of(),
          List.of(
              new MappingError(
                  request.payloadBytes(),
                  "PROFILE_DISABLED",
                  "Mapper profile is disabled",
                  null,
                  null)),
          List.of());
    }

    try {
      RecordExtractor<?> extractor = buildRecordExtractor(request);
      MappingSession<?> session =
          new MappingSession<>(
              this.transformEngine, request.profile(), extractor, request.properties());

      List<byte[]> payloads = normalizePayload(request);

      List<MappedRecord> allRecords = new ArrayList<>();
      List<MappingError> allErrors = new ArrayList<>();
      List<MappingWarning> allWarnings = new ArrayList<>();

      for (byte[] payload : payloads) {
        MappingResponse response = session.execute(payload);
        allRecords.addAll(response.records());
        allErrors.addAll(response.errors());
        allWarnings.addAll(response.warnings());
      }

      return new MappingResponse(
          profile.id(), profile.formatConfig().format(), allRecords, allErrors, allWarnings);
    } catch (MapperEngineException e) {
      return new MappingResponse(
          profile.id(),
          profile.formatConfig().format(),
          List.of(),
          List.of(
              new MappingError(
                  request.properties().preview() ? null : request.payloadBytes(),
                  e.getCode(),
                  e.getMessage(),
                  null,
                  null)),
          List.of());
    } catch (Exception e) {
      // Last-resort catch: the engine should ideally return structured errors itself.
      return new MappingResponse(
          profile.id(),
          profile.formatConfig().format(),
          List.of(),
          List.of(
              new MappingError(
                  request.properties().preview() ? null : request.payloadBytes(),
                  "ENGINE_EXCEPTION",
                  e.getMessage(),
                  null,
                  null)),
          List.of());
    }
  }

  private List<byte[]> normalizePayload(MappingRequest request)
      throws MapperEngineException, IOException {
    byte[] bytes = request.payloadBytes();

    // unwrap gzip if present
    if (GzipUtil.isGzip(bytes)) {
      bytes = GzipUtil.decompress(bytes, 10_240_000);
    }

    // if the (possibly decompressed) payload is a zip, treat it as KMZ-ish and extract KMLs
    if (KmzUtil.isZip(bytes)) {
      // For now, reject all zip that are not KMZ, since we do not support anything else atm
      if (request.profile().formatConfig().format() != KML_KMZ) {
        throw new MapperEngineException(
            "PAYLOAD_ERROR", "Received a ZIP/KMZ payload but format is not KML_KMZ", null);
      }
      return KmzUtil.unwrapToKmlIfNeeded(bytes);
    }

    // raw content (plain KML, GeoJSON, CSV, etc.)
    return List.of(bytes);
  }

  private RecordExtractor<?> buildRecordExtractor(MappingRequest request)
      throws MapperEngineException {
    return switch (request.profile().formatConfig().format()) {
      case MapperFormat.XML -> XmlExtractor.fromProfile(request.profile());
      case MapperFormat.JSON -> JsonExtractor.fromProfile(request.profile());
      case MapperFormat.CSV -> CsvExtractor.fromProfile(request.profile());
      case KML_KMZ -> KmlExtractor.fromProfile(request.profile());
      default ->
          throw new MapperEngineException(
              "UNSUPPORTED_MAPPER_FORMAT",
              request.profile().formatConfig().format() + " is not supported",
              null);
    };
  }
}
