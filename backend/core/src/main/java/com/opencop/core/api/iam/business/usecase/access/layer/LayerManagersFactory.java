// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.business.usecase.access.layer;

import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.iam.ports.gateway.repository.LayerMemberRepository;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.technical.presenter.LayerMemberPresenter;
import com.opencop.core.api.iam.technical.presenter.LayerPresenter;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupLayerMemberRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupUserGroupRepository;

public class LayerManagersFactory {

  private LayerManagersFactory() {}

  public static LayerManager<Layer, LayerMember> layerAccessManager(
      final LayersRepository layersRepository,
      final LayerMemberRepository layerMemberRepository,
      final ServerLayerRepository serverLayerRepository,
      final UserService userService,
      final IamGroupMembersRepository groupMembersRepository,
      final IamGroupLayerMemberRepository groupLayerRepository,
      final IamLayerGroupMembersRepository layerGroupMembersRepository,
      final IamLayerGroupLayerRepository layerGroupLayerRepository,
      final IamLayerGroupUserGroupRepository layerGroupUserGroupRepository,
      final IamLayerGroupRepository iamLayerGroupRepository) {
    var effectiveLayerAccessService =
        new EffectiveLayerAccessService(
            layersRepository,
            layerMemberRepository,
            groupMembersRepository,
            groupLayerRepository,
            layerGroupMembersRepository,
            layerGroupLayerRepository,
            layerGroupUserGroupRepository,
            iamLayerGroupRepository);
    return new LayerAccessManager<>(
        new LayerPresenter(),
        new LayerMemberPresenter(),
        layersRepository,
        layerMemberRepository,
        userService,
        serverLayerRepository,
        effectiveLayerAccessService);
  }
}
