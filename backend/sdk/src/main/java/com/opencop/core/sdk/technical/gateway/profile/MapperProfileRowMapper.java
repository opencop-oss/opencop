// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.profile;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

/** Reads a mapper revision row, with its last activity, into a mapper profile. */
final class MapperProfileRowMapper implements RowMapper<MapperProfile> {

  private final ObjectMapper objectMapper;

  MapperProfileRowMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public MapperProfile mapRow(ResultSet rs, int rowNum) throws SQLException {
    return MapperProfile.builder()
        .withId(rs.getObject("id", UUID.class))
        .withName(rs.getString("name"))
        .withDescription(rs.getString("description"))
        .withEditable(rs.getBoolean("editable"))
        .withIntegrationId(rs.getObject("integration_id", UUID.class))
        .withQualifier(MapperQualifier.valueOf(rs.getString("qualifier")))
        .withVersion(rs.getInt("version"))
        .withDynamicMapper(objectMapper.readValue(rs.getString("mapper"), DynamicMapper.class))
        .withArchived(rs.getBoolean("archived"))
        .withCreatedAt(instant(rs, "created_at"))
        .withCreatedBy(rs.getObject("created_by", UUID.class))
        .withUpdatedAt(instant(rs, "updated_at"))
        .withUpdatedBy(rs.getObject("updated_by", UUID.class))
        .withLastEventAt(instant(rs, "last_event_at"))
        .build();
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    var timestamp = rs.getObject(column, OffsetDateTime.class);
    return timestamp != null ? timestamp.toInstant() : null;
  }
}
