// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.mapper;

import static com.opencop.core.sdk.technical.model.mapper.ValueUtil.valueFrom;

import com.google.protobuf.ByteString;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.elements.Element;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import jakarta.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import opencop.core.proto.deckgl.ElementProtos.ElementMessage;
import org.geojson.Feature;
import org.geojson.GeoJsonObject;
import org.geojson.LineString;
import org.geojson.Point;
import org.geojson.Polygon;

public class FeatureMapper implements ProtobufMapper<Feature, ElementMessage> {

  @Override
  @Nullable
  public ElementMessage toMessage(Feature input) {
    final OpenCopProperties openCopProperties = OpenCopPropertiesParser.readFromFeature(input);

    if (openCopProperties != null && input.getGeometry() != null) {
      final UUID id = openCopProperties.getId();
      final UUID source = openCopProperties.getSource();
      final ByteBuffer idBuffer = ByteBuffer.allocate(16);
      final ByteBuffer sourceBuffer = ByteBuffer.allocate(16);

      idBuffer.putLong(id.getMostSignificantBits());
      idBuffer.putLong(id.getLeastSignificantBits());
      final byte[] idBytes = idBuffer.array();

      sourceBuffer.putLong(source.getMostSignificantBits());
      sourceBuffer.putLong(source.getLeastSignificantBits());
      final byte[] sourceBytes = sourceBuffer.array();

      final Element element = openCopProperties.getElement();
      final ElementMessage.Builder messageBuilder =
          ElementMessage.newBuilder()
              .setType(element.getType())
              .setId(ByteString.copyFrom(idBytes))
              .setObjectId(openCopProperties.getObjectId())
              .setSource(ByteString.copyFrom(sourceBytes))
              .setStart(openCopProperties.getTimestampz().getStart())
              .setTime(openCopProperties.getTimestampz().getTime())
              .setStale(openCopProperties.getTimestampz().getStale())
              .setDisplayName(
                  openCopProperties.getDisplayName() == null
                      ? ""
                      : openCopProperties.getDisplayName())
              .addAllPositions(flattenGeometry(input.getGeometry()));

      element
          .getEntries()
          .forEach(entry -> messageBuilder.putValues(entry.getKey(), valueFrom(entry.getValue())));

      return messageBuilder.build();
    }

    return null;
  }

  // TODO: Currently just returns blank feature.
  public Feature fromMessage(ElementMessage message) {
    var feature = new Feature();
    feature.setGeometry(new Point(0, 0));
    return feature;
  }

  // TODO: Handle other geometry.
  private List<Double> flattenGeometry(GeoJsonObject geometry) {
    return switch (geometry) {
      case Point p ->
          Arrays.asList(p.getCoordinates().getLongitude(), p.getCoordinates().getLatitude());
      case Polygon p -> {
        final List<Double> flat = new ArrayList<>();

        // We currently don't support rings.
        p.getCoordinates()
            .getFirst()
            .forEach(
                point -> {
                  flat.add(point.getLongitude());
                  flat.add(point.getLatitude());
                });

        yield flat;
      }
      default -> {
        final LineString lineString = (LineString) geometry;
        final List<Double> flat = new ArrayList<>();

        lineString
            .getCoordinates()
            .forEach(
                point -> {
                  flat.add(point.getLongitude());
                  flat.add(point.getLatitude());
                });

        yield flat;
      }
    };
  }
}
