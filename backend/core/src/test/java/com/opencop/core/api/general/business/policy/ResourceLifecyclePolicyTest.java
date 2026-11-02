// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ResourceLifecyclePolicyTest {

  @Test
  void assertLayerActive_whenLayerArchived_throwsConflict() {
    var layer = new Layer.Builder().withArchivedAt(Instant.now()).build();

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> ResourceLifecyclePolicy.assertLayerActive(layer, "import data"));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    assertTrue(exception.getMessage().contains("Archived layer is read-only"));
  }

  @Test
  void assertLayerActive_whenLayerIsActive_doesNotThrow() {
    var layer = new Layer.Builder().build();

    assertDoesNotThrow(() -> ResourceLifecyclePolicy.assertLayerActive(layer, "import data"));
  }

  @Test
  void assertLayerActive_whenLayerIsMergeSourceLocked_throwsConflict() {
    var layer =
        new Layer.Builder().withLifecycleState(LayerLifecycleState.MERGE_SOURCE_LOCKED).build();

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> ResourceLifecyclePolicy.assertLayerActive(layer, "broadcast"));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    assertTrue(exception.getMessage().contains("Layer is not active"));
    assertTrue(exception.getMessage().contains("broadcast"));
  }

  @Test
  void assertLayerActive_whenLayerLifecycleIsNotActive_throwsConflict() {
    var inactiveStates =
        List.of(
            LayerLifecycleState.PENDING_MERGE,
            LayerLifecycleState.MERGE_FAILED,
            LayerLifecycleState.ARCHIVED);

    for (var inactiveState : inactiveStates) {
      var layer = new Layer.Builder().withLifecycleState(inactiveState).build();

      var exception =
          assertThrows(
              GlobalOpenCopException.class,
              () -> ResourceLifecyclePolicy.assertLayerActive(layer, "update features"));

      assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
      assertTrue(exception.getMessage().contains("Layer is not active"));
    }
  }

  @Test
  void assertDataSourceActive_whenDataSourceArchived_throwsConflict() {
    var dataSource =
        new KafkaDataSource(
            UUID.randomUUID(),
            "source",
            "tenant",
            "topic",
            "username",
            null,
            null,
            AccessMode.READ_WRITE,
            true,
            Instant.now(),
            UUID.randomUUID(),
            Instant.now(),
            null,
            Instant.now(),
            null,
            null,
            TransportKind.KAFKA.name(),
            true);

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> ResourceLifecyclePolicy.assertDataSourceActive(dataSource, "start"));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    assertTrue(exception.getMessage().contains("Archived data source is read-only"));
  }

  @Test
  void assertDataSourceActive_whenDataSourceIsActive_doesNotThrow() {
    var dataSource =
        new KafkaDataSource(
            UUID.randomUUID(),
            "source",
            "tenant",
            "topic",
            "username",
            null,
            null,
            AccessMode.READ_WRITE,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            TransportKind.KAFKA.name(),
            true);

    assertDoesNotThrow(() -> ResourceLifecyclePolicy.assertDataSourceActive(dataSource, "start"));
  }
}
