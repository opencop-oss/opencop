// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.layermerge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LayerMergeOperationTest {

  private static final UUID TARGET_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655449001");
  private static final UUID JOB_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449002");
  private static final UUID FIRST_SOURCE_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655449003");
  private static final UUID SECOND_SOURCE_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655449004");

  @Test
  void constructorPreservesOrderedSourcesWhenAtLeastTwoAreProvided() {
    var operation =
        new LayerMergeOperation(
            TARGET_LAYER_ID,
            JOB_ID,
            List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID),
            LayerMergeOperationPhase.PENDING,
            null);

    assertEquals(
        List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID), operation.sourceLayerIds());
  }

  @Test
  void constructorRejectsSingleSourceLayer() {
    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new LayerMergeOperation(
                    TARGET_LAYER_ID,
                    JOB_ID,
                    List.of(FIRST_SOURCE_LAYER_ID),
                    LayerMergeOperationPhase.PENDING,
                    null));

    assertEquals("sourceLayerIds must contain at least two layers", exception.getMessage());
  }
}
