// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import com.opencop.core.sdk.model.layermerge.LayerMergeOperation;
import com.opencop.core.sdk.model.layermerge.LayerMergeOperationPhase;
import com.opencop.processor.ports.technical.gateway.db.LayerMergeGateway;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class LayerMergeRepository implements LayerMergeGateway {

  private static final String SOURCE_LAYER_IDS_REQUIRED_MESSAGE =
      "sourceLayerIds must contain at least one layer";
  private static final String LIFECYCLE_FINALIZATION_FAILURE_MESSAGE =
      "Failed to finalize layer merge lifecycle";
  private static final String MERGE_SQL =
      "CALL geo.merge_layers_ordered(CAST(? AS uuid[]), CAST(? AS uuid))";
  private static final String TARGET_ACTIVE_SQL =
      """
      SELECT EXISTS (
        SELECT 1
        FROM iam.layer
        WHERE id = CAST(? AS uuid)
          AND lifecycle_state = 'ACTIVE'::iam.layer_lifecycle_state
      )
      """;
  private static final String TARGET_PENDING_SQL =
      """
      SELECT EXISTS (
        SELECT 1
        FROM iam.layer
        WHERE id = CAST(? AS uuid)
          AND lifecycle_state = 'PENDING_MERGE'::iam.layer_lifecycle_state
      )
      """;
  private static final String FIND_OPERATION_SQL =
      """
      SELECT target_layer_id, job_id, source_layer_ids, phase::text, failure_reason
      FROM processor.layer_merge_operation
      WHERE target_layer_id = ?
      """;
  private static final String ADVANCE_OPERATION_SQL =
      """
      UPDATE processor.layer_merge_operation
      SET phase = ?::processor.layer_merge_operation_phase,
          failure_reason = NULL,
          updated_at = now()
      WHERE target_layer_id = ?
        AND phase = ?::processor.layer_merge_operation_phase
      """;
  private static final String FAIL_OPERATION_SQL =
      """
      UPDATE processor.layer_merge_operation
      SET phase = ?::processor.layer_merge_operation_phase,
          failure_reason = ?,
          updated_at = now()
      WHERE target_layer_id = ?
      """;
  private static final String FAIL_OPERATION_FROM_PHASE_SQL =
      """
      UPDATE processor.layer_merge_operation
      SET phase = 'FAILED'::processor.layer_merge_operation_phase,
          failure_reason = ?,
          updated_at = now()
      WHERE target_layer_id = ?
        AND phase = ?::processor.layer_merge_operation_phase
      """;
  private static final String FINALIZE_SUCCESS_SQL =
      """
      WITH target_update AS (
        UPDATE iam.layer
        SET lifecycle_state = 'ACTIVE'::iam.layer_lifecycle_state,
            updated_at = now(),
            updated_by = CAST(? AS uuid)
        WHERE id = CAST(? AS uuid)
          AND lifecycle_state = 'PENDING_MERGE'::iam.layer_lifecycle_state
        RETURNING id
      ),
      source_update AS (
        UPDATE iam.layer
        SET lifecycle_state = 'ARCHIVED'::iam.layer_lifecycle_state,
            archived_at = now(),
            archived_by = CAST(? AS uuid),
            updated_at = now(),
            updated_by = CAST(? AS uuid)
        WHERE id = ANY(CAST(? AS uuid[]))
          AND lifecycle_state = 'MERGE_SOURCE_LOCKED'::iam.layer_lifecycle_state
          AND EXISTS (SELECT 1 FROM target_update)
        RETURNING id
      )
      SELECT
        (SELECT count(*) FROM target_update) AS target_count,
        (SELECT count(*) FROM source_update) AS source_count
      """;
  private static final String FINALIZE_FAILURE_SQL =
      """
      WITH target_update AS (
        UPDATE iam.layer
        SET lifecycle_state = 'MERGE_FAILED'::iam.layer_lifecycle_state,
            updated_at = now(),
            updated_by = NULL
        WHERE id = CAST(? AS uuid)
          AND lifecycle_state = 'PENDING_MERGE'::iam.layer_lifecycle_state
        RETURNING id
      ),
      source_update AS (
        UPDATE iam.layer
        SET lifecycle_state = 'ACTIVE'::iam.layer_lifecycle_state,
            updated_at = now(),
            updated_by = NULL
        WHERE id = ANY(CAST(? AS uuid[]))
          AND lifecycle_state = 'MERGE_SOURCE_LOCKED'::iam.layer_lifecycle_state
          AND EXISTS (SELECT 1 FROM target_update)
        RETURNING id
      )
      SELECT
        (SELECT count(*) FROM target_update) AS target_count,
        (SELECT count(*) FROM source_update) AS source_count
      """;
  private static final String UUID_ARRAY_TYPE = "uuid";

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate repeatableReadTransaction;

  public LayerMergeRepository(
      JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
    this.jdbcTemplate = jdbcTemplate;
    this.repeatableReadTransaction = new TransactionTemplate(transactionManager);
    this.repeatableReadTransaction.setIsolationLevel(
        TransactionDefinition.ISOLATION_REPEATABLE_READ);
  }

  @Override
  public Optional<LayerMergeOperation> findMergeOperation(UUID targetLayerId) {
    return jdbcTemplate
        .query(
            FIND_OPERATION_SQL,
            (rs, _) ->
                new LayerMergeOperation(
                    rs.getObject("target_layer_id", UUID.class),
                    rs.getObject("job_id", UUID.class),
                    List.of((UUID[]) rs.getArray("source_layer_ids").getArray()),
                    LayerMergeOperationPhase.valueOf(rs.getString("phase")),
                    rs.getString("failure_reason")),
            targetLayerId)
        .stream()
        .findFirst();
  }

  @Override
  public void advanceMergeOperation(
      UUID targetLayerId,
      LayerMergeOperationPhase expectedPhase,
      LayerMergeOperationPhase nextPhase) {
    int updated =
        jdbcTemplate.update(
            ADVANCE_OPERATION_SQL, nextPhase.name(), targetLayerId, expectedPhase.name());
    if (updated != 1) {
      throw new IllegalStateException(
          "Expected one layer merge operation for target "
              + targetLayerId
              + " to advance from "
              + expectedPhase
              + " to "
              + nextPhase);
    }
  }

  @Override
  public void markMergeOperationFailed(
      UUID targetLayerId, LayerMergeOperationPhase expectedPhase, String failureReason) {
    int updated =
        jdbcTemplate.update(
            FAIL_OPERATION_FROM_PHASE_SQL, failureReason, targetLayerId, expectedPhase.name());
    if (updated != 1) {
      throw new IllegalStateException(
          "Expected one layer merge operation for target "
              + targetLayerId
              + " to fail from "
              + expectedPhase);
    }
  }

  @Override
  public boolean isMergeTargetActive(UUID targetLayerId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(TARGET_ACTIVE_SQL, Boolean.class, targetLayerId));
  }

  @Override
  public boolean isMergeTargetPending(UUID targetLayerId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(TARGET_PENDING_SQL, Boolean.class, targetLayerId));
  }

  @Override
  public void mergeLayers(List<UUID> sourceLayerIds, UUID targetLayerId) {
    validateSourceLayerIds(sourceLayerIds);

    try {
      repeatableReadTransaction.executeWithoutResult(
          _ -> materializeLayers(sourceLayerIds, targetLayerId));
    } catch (RuntimeException e) {
      var message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
      throw new IllegalStateException(
          message != null ? message : "Failed to merge layer contents", e);
    }
  }

  @Override
  public void mergeLayersAndAdvanceOperation(
      List<UUID> sourceLayerIds, UUID targetLayerId, LayerMergeOperationPhase nextPhase) {
    validateSourceLayerIds(sourceLayerIds);

    try {
      repeatableReadTransaction.executeWithoutResult(
          _ -> {
            materializeLayers(sourceLayerIds, targetLayerId);
            advanceMergeOperation(targetLayerId, LayerMergeOperationPhase.PENDING, nextPhase);
          });
    } catch (RuntimeException e) {
      var message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
      throw new IllegalStateException(
          message != null ? message : "Failed to merge layer contents", e);
    }
  }

  @Override
  public void finalizeMergeSuccess(UUID targetLayerId, List<UUID> sourceLayerIds, UUID archivedBy) {
    updateMergeSuccessLifecycle(targetLayerId, sourceLayerIds, archivedBy);
  }

  @Override
  public void finalizeMergeSuccessAndAdvanceOperation(
      UUID targetLayerId,
      List<UUID> sourceLayerIds,
      UUID archivedBy,
      LayerMergeOperationPhase nextPhase) {
    try {
      repeatableReadTransaction.executeWithoutResult(
          _ -> {
            updateMergeSuccessLifecycle(targetLayerId, sourceLayerIds, archivedBy);
            advanceMergeOperation(targetLayerId, LayerMergeOperationPhase.HYDRATED, nextPhase);
          });
    } catch (RuntimeException e) {
      var message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
      throw new IllegalStateException(
          message != null ? message : LIFECYCLE_FINALIZATION_FAILURE_MESSAGE, e);
    }
  }

  @Override
  public void finalizeMergeFailure(UUID targetLayerId, List<UUID> sourceLayerIds) {
    updateMergeFailureLifecycle(targetLayerId, sourceLayerIds);
  }

  @Override
  public void finalizeMergeFailureAndSetOperationPhase(
      UUID targetLayerId,
      List<UUID> sourceLayerIds,
      LayerMergeOperationPhase phase,
      String failureReason) {
    try {
      repeatableReadTransaction.executeWithoutResult(
          _ -> {
            setMergeOperationPhase(targetLayerId, phase, failureReason);
            updateMergeFailureLifecycle(targetLayerId, sourceLayerIds);
          });
    } catch (RuntimeException e) {
      var message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
      throw new IllegalStateException(
          message != null ? message : "Failed to finalize layer merge failure", e);
    }
  }

  private void materializeLayers(List<UUID> sourceLayerIds, UUID targetLayerId) {
    jdbcTemplate.update(
        connection -> {
          PreparedStatement statement = connection.prepareStatement(MERGE_SQL);
          Array sourceIds = connection.createArrayOf(UUID_ARRAY_TYPE, sourceLayerIds.toArray());
          statement.setArray(1, sourceIds);
          statement.setObject(2, targetLayerId);
          return statement;
        });
    // Both callers wrap this in their own transaction, so a failed merge records no activity for
    // it. Credited to the target only: the source layers lost content rather than gaining any.
    LayerCopyActivityWriter.record(jdbcTemplate, targetLayerId);
  }

  private static void validateSourceLayerIds(List<UUID> sourceLayerIds) {
    if (sourceLayerIds.isEmpty()) {
      throw new IllegalArgumentException(SOURCE_LAYER_IDS_REQUIRED_MESSAGE);
    }
  }

  private void setMergeOperationPhase(
      UUID targetLayerId, LayerMergeOperationPhase phase, String failureReason) {
    int updated =
        jdbcTemplate.update(FAIL_OPERATION_SQL, phase.name(), failureReason, targetLayerId);
    if (updated != 1) {
      throw new IllegalStateException(
          "Expected one layer merge operation for target "
              + targetLayerId
              + " to move to "
              + phase);
    }
  }

  private void updateMergeSuccessLifecycle(
      UUID targetLayerId, List<UUID> sourceLayerIds, UUID archivedBy) {
    try {
      repeatableReadTransaction.executeWithoutResult(
          _ -> {
            var counts =
                jdbcTemplate.query(
                    connection -> {
                      PreparedStatement statement =
                          connection.prepareStatement(FINALIZE_SUCCESS_SQL);
                      Array sourceIds =
                          connection.createArrayOf(UUID_ARRAY_TYPE, sourceLayerIds.toArray());
                      // Binding order follows FINALIZE_SUCCESS_SQL textually. Both CTEs put their
                      // SET clause ahead of their WHERE, and the source CTE now sets archived_by
                      // and updated_by — the same actor twice — before the id array.
                      statement.setObject(1, archivedBy); // target updated_by
                      statement.setObject(2, targetLayerId); // target WHERE id
                      statement.setObject(3, archivedBy); // source archived_by
                      statement.setObject(4, archivedBy); // source updated_by
                      statement.setArray(5, sourceIds); // source WHERE id array
                      return statement;
                    },
                    rs -> {
                      if (!rs.next()) {
                        throw new IllegalStateException(
                            "Layer merge success finalization did not return transition counts");
                      }
                      return new LifecycleTransitionCounts(
                          rs.getInt("target_count"), rs.getInt("source_count"));
                    });
            validateLifecycleTransition("success", targetLayerId, sourceLayerIds.size(), counts);
          });
    } catch (RuntimeException e) {
      var message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
      throw new IllegalStateException(
          message != null ? message : LIFECYCLE_FINALIZATION_FAILURE_MESSAGE, e);
    }
  }

  private void updateMergeFailureLifecycle(UUID targetLayerId, List<UUID> sourceLayerIds) {
    try {
      repeatableReadTransaction.executeWithoutResult(
          _ -> {
            var counts =
                jdbcTemplate.query(
                    connection -> {
                      PreparedStatement statement =
                          connection.prepareStatement(FINALIZE_FAILURE_SQL);
                      Array sourceIds =
                          connection.createArrayOf(UUID_ARRAY_TYPE, sourceLayerIds.toArray());
                      statement.setObject(1, targetLayerId);
                      statement.setArray(2, sourceIds);
                      return statement;
                    },
                    rs -> {
                      if (!rs.next()) {
                        throw new IllegalStateException(
                            "Layer merge failure finalization did not return transition counts");
                      }
                      return new LifecycleTransitionCounts(
                          rs.getInt("target_count"), rs.getInt("source_count"));
                    });
            validateLifecycleTransition("failure", targetLayerId, sourceLayerIds.size(), counts);
          });
    } catch (RuntimeException e) {
      var message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
      throw new IllegalStateException(
          message != null ? message : LIFECYCLE_FINALIZATION_FAILURE_MESSAGE, e);
    }
  }

  private static void validateLifecycleTransition(
      String outcome,
      UUID targetLayerId,
      int expectedSourceCount,
      LifecycleTransitionCounts counts) {
    if (counts.targetCount() != 1 || counts.sourceCount() != expectedSourceCount) {
      throw new IllegalStateException(
          "Expected layer merge "
              + outcome
              + " finalization for target "
              + targetLayerId
              + " to transition 1 target and "
              + expectedSourceCount
              + " sources, but transitioned "
              + counts.targetCount()
              + " target and "
              + counts.sourceCount()
              + " sources");
    }
  }

  private record LifecycleTransitionCounts(int targetCount, int sourceCount) {}
}
