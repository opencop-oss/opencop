// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.gateway.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.watchbox.WatchboxAlertingStatus;
import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusReason;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxLayerEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxLayerIdEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxLayerJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxSourceStatusEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxSourceStatusIdEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxSourceStatusJpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class JpaWatchboxConfigRepositoryTest {

  @Mock WatchboxJpaRepository watchboxes;
  @Mock WatchboxLayerJpaRepository layers;
  @Mock WatchboxSourceStatusJpaRepository sourceStatuses;

  private JpaWatchboxConfigRepository repository;

  @BeforeEach
  void setUp() {
    repository =
        new JpaWatchboxConfigRepository(watchboxes, layers, sourceStatuses, new ObjectMapper());
  }

  @Test
  void listActiveLoadsLayersForAllWatchboxesInOneBatch() {
    var watchboxId = UUID.randomUUID();
    var otherWatchboxId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var otherLayerId = UUID.randomUUID();
    when(watchboxes.findAllByArchivedAtIsNullOrderByCreatedAtDesc())
        .thenReturn(List.of(entity(watchboxId, "first"), entity(otherWatchboxId, "second")));
    when(layers.findAllByIdWatchboxIdIn(any()))
        .thenReturn(
            List.of(
                new WatchboxLayerEntity(new WatchboxLayerIdEntity(watchboxId, layerId)),
                new WatchboxLayerEntity(new WatchboxLayerIdEntity(otherWatchboxId, otherLayerId))));

    var configs = repository.listActive();

    assertThat(configs).hasSize(2);
    assertThat(configs.get(0).layerIds()).containsExactly(layerId);
    assertThat(configs.get(1).layerIds()).containsExactly(otherLayerId);
    ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.captor();
    verify(layers).findAllByIdWatchboxIdIn(captor.capture());
    assertThat(captor.getValue()).containsExactlyInAnyOrder(watchboxId, otherWatchboxId);
    verify(layers, never()).findAllByIdWatchboxId(any());
  }

  @Test
  void listActiveSkipsLayerRepositoryWhenNoActiveWatchboxes() {
    when(watchboxes.findAllByArchivedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of());

    assertThat(repository.listActive()).isEmpty();

    verify(layers, never()).findAllByIdWatchboxIdIn(any());
    verify(layers, never()).findAllByIdWatchboxId(any());
  }

  @Test
  void findSourceStatusesForWatchboxesBatchesAndFiltersEachWatchboxToItsOwnEpoch() {
    var watchboxId = UUID.randomUUID();
    var otherWatchboxId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    when(sourceStatuses.findAllByIdWatchboxIdIn(any()))
        .thenReturn(
            List.of(
                statusEntity(watchboxId, 2L, sourceId),
                statusEntity(watchboxId, 1L, sourceId),
                statusEntity(otherWatchboxId, 5L, sourceId)));

    var statuses =
        repository.findSourceStatusesForWatchboxes(Map.of(watchboxId, 2L, otherWatchboxId, 5L));

    // The stale-epoch row for the first watchbox is filtered; each watchbox keeps only rows at
    // its own current epoch.
    assertThat(statuses.get(watchboxId)).hasSize(1);
    assertThat(statuses.get(otherWatchboxId)).hasSize(1);
    ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.captor();
    verify(sourceStatuses).findAllByIdWatchboxIdIn(captor.capture());
    assertThat(captor.getValue()).containsExactlyInAnyOrder(watchboxId, otherWatchboxId);
    verify(sourceStatuses, never()).findAllByIdWatchboxIdAndIdConfigEpoch(any(), anyLong());
  }

  @Test
  void findSourceStatusesForWatchboxesSkipsRepositoryForEmptyInput() {
    assertThat(repository.findSourceStatusesForWatchboxes(Map.of())).isEmpty();

    verify(sourceStatuses, never()).findAllByIdWatchboxIdIn(any());
  }

  private static WatchboxSourceStatusEntity statusEntity(
      UUID watchboxId, long configEpoch, UUID sourceId) {
    var entity = new WatchboxSourceStatusEntity();
    entity.setId(new WatchboxSourceStatusIdEntity(watchboxId, configEpoch, sourceId));
    entity.setStatus(WatchboxAlertingStatus.DEGRADED);
    entity.setReasonCode(WatchboxStatusReason.WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN);
    return entity;
  }

  private static WatchboxEntity entity(UUID id, String name) {
    var entity = new WatchboxEntity();
    entity.setId(id);
    entity.setName(name);
    entity.setEnabled(true);
    entity.setDirection(WatchboxDirection.BOTH);
    entity.setPolygonJson(
        """
        {"type":"Polygon","coordinates":[[[-100.2,39.8],[-99.8,39.8],[-99.8,40.2],[-100.2,40.2],[-100.2,39.8]]]}
        """);
    entity.setFilterJson("{\"predicates\":[]}");
    entity.setConfigEpoch(1L);
    return entity;
  }
}
