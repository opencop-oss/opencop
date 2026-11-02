// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.protobuf.ByteString;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.elements.ElementTypes;
import com.opencop.core.sdk.technical.model.opencop.elements.PointElement;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import java.nio.ByteBuffer;
import java.util.UUID;
import opencop.core.proto.deckgl.ElementProtos.ElementMessage;
import org.geojson.Feature;
import org.geojson.Point;
import org.junit.jupiter.api.Test;

class FeatureMapperTest {

  @Test
  void toMessage_allowsMissingMotionFields() {
    UUID id = UUID.randomUUID();
    UUID source = UUID.randomUUID();

    OpenCopProperties properties = new OpenCopProperties();
    properties.setId(id);
    properties.setObjectId("object-1");
    properties.setSource(source);
    properties.setDisplayName("Test feature");
    properties.setTimestampz(new Timestampz(2000L, 1000L, 3000L));
    properties.setElement(new PointElement.Builder().type(ElementTypes.POINT).build());

    Feature feature =
        OpenCopPropertiesParser.writeToFeature(properties, new Point(-75.1652, 39.9526));

    ElementMessage message = new FeatureMapper().toMessage(feature);

    assertNotNull(message);
    assertEquals("point", message.getType());
    assertEquals("object-1", message.getObjectId());
    assertEquals("Test feature", message.getDisplayName());
    assertEquals(ByteString.copyFrom(uuidBytes(id)), message.getId());
    assertEquals(ByteString.copyFrom(uuidBytes(source)), message.getSource());
    assertEquals(1000L, message.getStart());
    assertEquals(2000L, message.getTime());
    assertEquals(3000L, message.getStale());
    assertEquals(2, message.getPositionsCount());
    assertEquals(-75.1652D, message.getPositions(0));
    assertEquals(39.9526D, message.getPositions(1));
  }

  private static byte[] uuidBytes(UUID value) {
    ByteBuffer buffer = ByteBuffer.allocate(16);
    buffer.putLong(value.getMostSignificantBits());
    buffer.putLong(value.getLeastSignificantBits());
    return buffer.array();
  }
}
