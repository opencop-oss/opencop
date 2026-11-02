// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history.chunk;

import com.google.protobuf.CodedOutputStream;
import com.opencop.core.sdk.technical.model.mapper.FeatureMapper;
import com.opencop.processor.business.model.history.HistoricalSampledPlaybackPoint;
import com.opencop.processor.business.model.history.SourceObjectKey;
import opencop.core.proto.deckgl.ElementProtos.ElementMessage;
import opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkPayload;
import org.geojson.Feature;
import tools.jackson.databind.ObjectMapper;

final class HistoricalChunkPointPayloadMapper {

  private static final int ELEMENTS_FIELD_NUMBER = HistoricalChunkPayload.ELEMENTS_FIELD_NUMBER;

  private final ObjectMapper objectMapper;
  private final FeatureMapper featureMapper;

  HistoricalChunkPointPayloadMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.featureMapper = new FeatureMapper();
  }

  HistoricalChunkPointPayload map(HistoricalSampledPlaybackPoint point) {
    Feature feature = readFeature(point);
    ElementMessage elementMessage = featureMapper.toMessage(feature);
    if (elementMessage == null) {
      throw new IllegalStateException(
          "Historical payload point could not be mapped to ElementMessage for event "
              + point.eventId());
    }
    return new HistoricalChunkPointPayload(
        new SourceObjectKey(point.sourceId(), point.objectId()),
        point.objectId(),
        elementMessage,
        serializedElementSize(elementMessage));
  }

  private Feature readFeature(HistoricalSampledPlaybackPoint point) {
    try {
      return objectMapper.readValue(point.payloadJson(), Feature.class);
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "Failed to parse sampled payload JSON for event " + point.eventId(), e);
    }
  }

  private static int serializedElementSize(ElementMessage point) {
    int messageSize = point.getSerializedSize();
    return CodedOutputStream.computeTagSize(ELEMENTS_FIELD_NUMBER)
        + CodedOutputStream.computeUInt32SizeNoTag(messageSize)
        + messageSize;
  }
}
