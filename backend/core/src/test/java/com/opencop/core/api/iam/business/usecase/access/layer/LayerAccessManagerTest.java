// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.business.usecase.access.layer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.iam.ports.gateway.repository.LayerMemberRepository;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.ports.presenter.Presenter;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.model.iam.layer.LayerReadFilter;
import com.opencop.core.sdk.model.iam.layer.UpdateLayerRoleRequest;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LayerAccessManagerTest {

  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655447001");
  private static final UUID MEMBER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655447002");
  private static final UUID EDITOR_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655447003");
  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655447004");
  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655447005");

  private LayersRepository layersRepository;
  private LayerMemberRepository layerMemberRepository;
  private Presenter<Layer, Layer> layerPresenter;
  private LayerAccessManager<Layer, LayerMember> manager;

  @BeforeEach
  void setUp() {
    @SuppressWarnings("unchecked")
    Presenter<Layer, Layer> layerPresenter = mock(Presenter.class);
    @SuppressWarnings("unchecked")
    Presenter<LayerMember, LayerMember> layerMemberPresenter = mock(Presenter.class);

    this.layerPresenter = layerPresenter;
    layersRepository = mock(LayersRepository.class);
    layerMemberRepository = mock(LayerMemberRepository.class);
    manager =
        new LayerAccessManager<>(
            layerPresenter,
            layerMemberPresenter,
            layersRepository,
            layerMemberRepository,
            mock(UserService.class),
            mock(ServerLayerRepository.class),
            mock(EffectiveLayerAccessService.class));
  }

  @Test
  void updateLayerMemberRole_updatesEditorsAlongsideOtherBuckets() throws Exception {
    when(layerMemberRepository.getOwnerUserIds(LAYER_ID)).thenReturn(Set.of(OWNER_ID));

    manager.updateLayerMemberRole(
        LAYER_ID,
        new UpdateLayerRoleRequest(
            Set.of(MEMBER_ID), Set.of(EDITOR_ID), Set.of(ADMIN_ID), Set.of(OWNER_ID)));

    verify(layerMemberRepository).getOwnerUserIds(LAYER_ID);
    verify(layerMemberRepository)
        .addLayerMemberRoles(LAYER_ID, Set.of(MEMBER_ID), LayerRole.MEMBER);
    verify(layerMemberRepository)
        .addLayerMemberRoles(LAYER_ID, Set.of(EDITOR_ID), LayerRole.EDITOR);
    verify(layerMemberRepository).addLayerMemberRoles(LAYER_ID, Set.of(ADMIN_ID), LayerRole.ADMIN);
    verify(layerMemberRepository).addLayerMemberRoles(LAYER_ID, Set.of(OWNER_ID), LayerRole.OWNER);
    verifyNoMoreInteractions(layerMemberRepository);
  }

  @Test
  void list_withReadFilter_delegatesToRepositoryAndDefaultsRecordVisibility() {
    when(layersRepository.list(new LayerReadFilter(null, LayerAccessScope.GLOBAL, "Ops")))
        .thenReturn(List.of());

    manager.list(new LayerReadFilter(null, LayerAccessScope.GLOBAL, "Ops"));

    ArgumentCaptor<LayerReadFilter> captor = ArgumentCaptor.forClass(LayerReadFilter.class);
    verify(layersRepository, times(1)).list(captor.capture());
    var captured = captor.getValue();
    Assertions.assertEquals(RecordVisibility.ACTIVE, captured.recordVisibilityOrDefault());
    Assertions.assertEquals(LayerAccessScope.GLOBAL, captured.accessScope());
    Assertions.assertEquals("Ops", captured.name());
  }

  @Test
  void list_withoutFilter_usesActiveArchiveFilter() {
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, null, null, null, null)))
        .thenReturn(List.of());

    manager.list();

    verify(layersRepository)
        .list(new LayerReadFilter(RecordVisibility.ACTIVE, null, null, null, null));
  }

  @Test
  void get_withoutRecordVisibility_usesIdentityLookup() throws Exception {
    var layer = mock(Layer.class);
    when(layersRepository.get(LAYER_ID)).thenReturn(layer);

    manager.get(LAYER_ID);

    verify(layersRepository).get(LAYER_ID);
    verify(layersRepository, never()).get(LAYER_ID, RecordVisibility.ACTIVE);
  }

  @Test
  void get_withoutRecordVisibility_doesNotCallArchiveFilteredLookup() throws Exception {
    var layer = mock(Layer.class);
    when(layersRepository.get(LAYER_ID)).thenReturn(layer);

    manager.get(LAYER_ID);

    verify(layersRepository, never()).get(LAYER_ID, RecordVisibility.ALL);
  }

  @Test
  void get_withoutRecordVisibility_returnsPresentedLayer() throws Exception {
    var repositoryLayer = mock(Layer.class);
    var presentedLayer = mock(Layer.class);
    when(layersRepository.get(LAYER_ID)).thenReturn(repositoryLayer);
    when(layerPresenter.present(repositoryLayer)).thenReturn(presentedLayer);

    var actual = manager.get(LAYER_ID);

    Assertions.assertEquals(presentedLayer, actual);
  }
}
