// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.iam;

import com.opencop.core.sdk.ports.gateway.iam.AccessRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.iam.LayerAccessService;
import com.opencop.websocket.ports.gateway.iam.LiveEventResourceAccessGateway;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class SdkLiveEventResourceAccessGateway implements LiveEventResourceAccessGateway {

  private static final String SOURCE_ACTIVE_CHECK_SQL =
      """
      SELECT EXISTS (
        SELECT 1
        FROM profiles.data_source
        WHERE id = :sourceId
          AND archived_at IS NULL
      )
      """;
  private static final String LAYER_ACTIVE_CHECK_SQL =
      """
      SELECT EXISTS (
        SELECT 1
        FROM iam.layer
        WHERE id = :layerId
          AND lifecycle_state = 'ACTIVE'::iam.layer_lifecycle_state
      )
      """;

  private final LayerAccessService layerAccessService;
  private final AccessRepository<ServerRole> sourceAccessRepository;
  private final NamedParameterJdbcTemplate jdbcTemplate;

  public SdkLiveEventResourceAccessGateway(
      LayerAccessService layerAccessService,
      AccessRepository<ServerRole> sourceAccessRepository,
      NamedParameterJdbcTemplate jdbcTemplate) {
    this.layerAccessService = layerAccessService;
    this.sourceAccessRepository = sourceAccessRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public boolean userHasLayerAccess(UUID userId, UUID layerId) {
    return layerAccessService.userHasAccess(userId, layerId);
  }

  @Override
  public boolean userHasSourceAccess(UUID userId, UUID sourceId) {
    return sourceAccessRepository.userHasAccess(userId, sourceId);
  }

  @Override
  public boolean layerIsActive(UUID layerId) {
    var params = new MapSqlParameterSource().addValue("layerId", layerId);
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(LAYER_ACTIVE_CHECK_SQL, params, Boolean.class));
  }

  @Override
  public boolean sourceIsActive(UUID sourceId) {
    var params = new MapSqlParameterSource().addValue("sourceId", sourceId);
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(SOURCE_ACTIVE_CHECK_SQL, params, Boolean.class));
  }
}
