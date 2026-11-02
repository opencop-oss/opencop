// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.event.business.model.EventDetails;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.elements.Element;
import java.util.UUID;
import org.geojson.Point;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EventDetailsResponseTest {
  @Test
  void serialize_unwrapsPropertiesToRoot_andAddsGeometry() {
    var properties = new OpenCopProperties();
    properties.setId(UUID.randomUUID());
    properties.setObjectId("track-1");
    properties.setSource(UUID.randomUUID());
    properties.setDisplayName("Truck 1");
    properties.setElement(new Element("point"));
    var mapper = new ObjectMapper();
    var body =
        mapper.readTree(
            mapper.writeValueAsString(
                EventDetailsResponse.from(new EventDetails(properties, new Point(-97, 32)))));
    for (var key :
        java.util.List.of("id", "objectId", "source", "displayName", "element", "geometry")) {
      assertThat(body.hasNonNull(key)).as(key).isTrue();
    }
    assertThat(body.has("properties")).isFalse();
    assertThat(body.path("geometry").path("type").asText()).isEqualTo("Point");
  }
}
