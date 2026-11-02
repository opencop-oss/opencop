// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.opencop.parser;

import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import org.geojson.Feature;
import org.geojson.GeoJsonObject;
import tools.jackson.databind.ObjectMapper;

public final class OpenCopPropertiesParser {

  public static final String OPENCOP_NODE = "opencop";
  private static final ObjectMapper mapper = new ObjectMapper();

  private OpenCopPropertiesParser() {}

  public static OpenCopProperties readFromFeature(Feature feature) {
    return mapper.convertValue(feature.getProperty(OPENCOP_NODE), OpenCopProperties.class);
  }

  public static Feature writeToFeature(OpenCopProperties oc, GeoJsonObject geometry) {
    var feature = new Feature();
    feature.setGeometry(geometry);
    feature.setProperty(OPENCOP_NODE, oc);
    return feature;
  }

  public static Feature writeToFeature(
      OpenCopProperties oc, GeoJsonObject geometry, String featureId) {
    var feature = new Feature();
    feature.setId(featureId);
    feature.setGeometry(geometry);
    feature.setProperty(OPENCOP_NODE, oc);
    return feature;
  }
}
