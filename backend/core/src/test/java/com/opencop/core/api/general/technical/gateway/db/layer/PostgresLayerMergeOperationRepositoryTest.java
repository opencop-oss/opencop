// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.layer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.layermerge.LayerMergeOperation;
import com.opencop.core.sdk.model.layermerge.LayerMergeOperationPhase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostgresLayerMergeOperationRepositoryTest {

  private static final UUID FIRST_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655447001");
  private static final UUID SECOND_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655447002");
  private static final UUID TARGET_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655447004");
  private static final UUID JOB_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655447006");
  private static final String FAILURE_REASON = "PUBLISH_FAILED: IllegalStateException";
  private static final List<UUID> SOURCE_LAYER_IDS = List.of(FIRST_LAYER_ID, SECOND_LAYER_ID);

  @Mock private LayerMergeOperationJpaRepository jpaRepository;
  @Captor private ArgumentCaptor<LayerMergeOperationEntity> entityCaptor;

  private PostgresLayerMergeOperationRepository repository;

  @BeforeEach
  void setUp() {
    repository = new PostgresLayerMergeOperationRepository(jpaRepository);
  }

  @Test
  void createPending_savesOperationEntity() {
    repository.createPending(
        new LayerMergeOperation(
            TARGET_LAYER_ID, JOB_ID, SOURCE_LAYER_IDS, LayerMergeOperationPhase.PENDING, null));

    verify(jpaRepository).save(entityCaptor.capture());
    var entity = entityCaptor.getValue();
    assertEquals(TARGET_LAYER_ID, entity.getTargetLayerId());
    assertEquals(JOB_ID, entity.getJobId());
    assertArrayEquals(SOURCE_LAYER_IDS.toArray(UUID[]::new), entity.getSourceLayerIds());
    assertEquals(LayerMergeOperationPhase.PENDING, entity.getPhase());
  }

  @Test
  void createPending_whenOperationIsNotPending_throws() {
    var operation =
        new LayerMergeOperation(
            TARGET_LAYER_ID, JOB_ID, SOURCE_LAYER_IDS, LayerMergeOperationPhase.FAILED, "failed");

    assertThrows(IllegalArgumentException.class, () -> repository.createPending(operation));
  }

  @Test
  void markFailed_updatesPendingOperationForTarget() {
    when(jpaRepository.markFailed(
            TARGET_LAYER_ID,
            FAILURE_REASON,
            LayerMergeOperationPhase.PENDING,
            LayerMergeOperationPhase.FAILED))
        .thenReturn(1);

    repository.markFailed(TARGET_LAYER_ID, FAILURE_REASON);

    verify(jpaRepository)
        .markFailed(
            TARGET_LAYER_ID,
            FAILURE_REASON,
            LayerMergeOperationPhase.PENDING,
            LayerMergeOperationPhase.FAILED);
  }

  @Test
  void markFailed_whenNoPendingOperationIsUpdated_throws() {
    when(jpaRepository.markFailed(
            TARGET_LAYER_ID,
            FAILURE_REASON,
            LayerMergeOperationPhase.PENDING,
            LayerMergeOperationPhase.FAILED))
        .thenReturn(0);

    assertThrows(
        IllegalStateException.class, () -> repository.markFailed(TARGET_LAYER_ID, FAILURE_REASON));
  }
}
