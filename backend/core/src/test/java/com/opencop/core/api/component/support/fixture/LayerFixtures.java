// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support.fixture;

import com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.entity.ServerLayerAccessEntity;
import com.opencop.core.sdk.component.TestFixture;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.LayerMemberEntity;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class LayerFixtures implements TestFixture {

  private final JdbcTemplate jdbcTemplate;
  private final IamLayerRepository layerRepository;
  private final IamLayerMembersRepository layerMembersRepository;
  private final IamServerLayerRepository serverLayerRepository;

  public LayerFixtures(
      JdbcTemplate jdbcTemplate,
      IamLayerRepository layerRepository,
      IamLayerMembersRepository layerMembersRepository,
      IamServerLayerRepository serverLayerRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.layerRepository = layerRepository;
    this.layerMembersRepository = layerMembersRepository;
    this.serverLayerRepository = serverLayerRepository;
  }

  @Override
  public void reset() {
    jdbcTemplate.update("DELETE FROM geo.remarks");
    jdbcTemplate.update("DELETE FROM event_attachment");
    jdbcTemplate.update("DELETE FROM layer_media");
    jdbcTemplate.update("DELETE FROM geo.features");
    jdbcTemplate.update("DELETE FROM media_file");
    jdbcTemplate.update("DELETE FROM media_attachment");
    jdbcTemplate.update("DELETE FROM iam.server_layer_access");
    jdbcTemplate.update("DELETE FROM iam.layer_member");
    jdbcTemplate.update("DELETE FROM iam.layer");
  }

  public UUID persistLayer(String name) {
    return persistLayer(name, LayerAccessScope.PRIVATE);
  }

  public UUID persistLayer(String name, LayerAccessScope accessScope) {
    var layer = new LayerEntity();
    layer.setName(name);
    layer.setAccessScope(accessScope);
    return layerRepository.saveAndFlush(layer).getId();
  }

  public UUID persistLayerWithMembers(String name, Map<UUID, LayerRole> members) {
    var layerId = persistLayer(name);
    members.forEach((userId, role) -> addMember(layerId, userId, role));
    return layerId;
  }

  public UUID persistLayerWithMembers(
      String name, LayerAccessScope accessScope, Map<UUID, LayerRole> members) {
    var layerId = persistLayer(name, accessScope);
    members.forEach((userId, role) -> addMember(layerId, userId, role));
    return layerId;
  }

  public void addMember(UUID layerId, UUID userId, LayerRole role) {
    var layer =
        layerRepository
            .findById(layerId)
            .orElseThrow(() -> new IllegalStateException("Missing layer " + layerId));
    layerMembersRepository.saveAndFlush(new LayerMemberEntity(layer, userId, role));
  }

  public void addServerAccess(UUID serverId, UUID layerId, ServerRole role) {
    var layer =
        layerRepository
            .findById(layerId)
            .orElseThrow(() -> new IllegalStateException("Missing layer " + layerId));
    serverLayerRepository.saveAndFlush(new ServerLayerAccessEntity(serverId, layer, role));
  }

  public LayerRole loadMemberRole(UUID layerId, UUID userId) {
    return layerMembersRepository
        .findByLayerIdAndUserId(layerId, userId)
        .orElseThrow(() -> new IllegalStateException("Missing member " + userId))
        .getRole();
  }

  public boolean layerExists(UUID layerId) {
    return layerRepository.existsById(layerId);
  }
}
