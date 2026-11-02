// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.gateway;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.geojson.Feature;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

public class FeatureRowMapper implements RowMapper<Feature> {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Feature mapRow(ResultSet rs, int rowNum) throws SQLException {
    return objectMapper.readValue(rs.getString("payload"), Feature.class);
  }
}
