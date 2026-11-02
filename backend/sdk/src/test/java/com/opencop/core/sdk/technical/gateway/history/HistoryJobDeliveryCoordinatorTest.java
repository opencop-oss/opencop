// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoryEvent;
import com.opencop.core.sdk.model.history.HistoryJobCompletedEvent;
import com.opencop.core.sdk.model.history.HistoryJobFailedEvent;
import com.opencop.core.sdk.model.history.HistoryJobProgressEvent;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.core.sdk.technical.model.history.HistoricalJobDeliverySnapshot;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoryJobDeliveryCoordinatorTest {

  @Test
  void onChunkDeliveryFailed_publishesFailureOnce() {
    var repository = new InMemoryDeliveryRepository();
    var lifecycleUpdater = new RecordingLifecycleUpdater();
    var publisher = new RecordingPublisher();
    var coordinator =
        new HistoryJobDeliveryCoordinator(
            repository, lifecycleUpdater, publisher, Duration.ofHours(24));
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440102");

    repository.ensureInitialized(
        jobId,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440100"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440101"),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440103"),
        2,
        0,
        true,
        Duration.ofHours(24));
    repository.markExecutionComplete(jobId, 1, 0, Duration.ofHours(24));

    coordinator.onChunkDeliveryFailed(jobId, "missing payload part");
    coordinator.onChunkDeliveryFailed(jobId, "ignored second failure");

    assertEquals(3, publisher.events.size());
    assertTrue(publisher.events.getFirst() instanceof HistoryJobProgressEvent);
    assertTrue(publisher.events.get(1) instanceof HistoryJobFailedEvent);
    assertTrue(publisher.events.get(2) instanceof HistoryJobProgressEvent);
    var failed = (HistoryJobFailedEvent) publisher.events.get(1);
    assertEquals("DELIVERY_FAILED", failed.stage());
    assertEquals("missing payload part", failed.reason());
    assertTrue(repository.failureClaimed);
    assertFalse(lifecycleUpdater.completed);
    assertTrue(lifecycleUpdater.failed);
    assertEquals("missing payload part", lifecycleUpdater.failureReason);
  }

  @Test
  void deliveryFailureBeforeExecutionFinished_survivesLaterExecutionAccounting() {
    var repository = new InMemoryDeliveryRepository();
    var lifecycleUpdater = new RecordingLifecycleUpdater();
    var publisher = new RecordingPublisher();
    var coordinator =
        new HistoryJobDeliveryCoordinator(
            repository, lifecycleUpdater, publisher, Duration.ofHours(24));
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440302");

    repository.ensureInitialized(
        jobId,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440300"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440301"),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440303"),
        2,
        0,
        true,
        Duration.ofHours(24));

    coordinator.onChunkDeliveryFailed(jobId, "lost websocket subscription");
    assertFalse(repository.failureClaimed);
    assertFalse(lifecycleUpdater.failed);
    assertEquals(
        0, publisher.events.stream().filter(HistoryJobFailedEvent.class::isInstance).count());

    coordinator.recordExecutionProgress(jobId, 1, 0);
    coordinator.onExecutionFinished(jobId, 2, 0);
    coordinator.onChunkDelivered(jobId);

    assertEquals(1, repository.snapshot.failedChunks());
    assertEquals("lost websocket subscription", repository.snapshot.failureReason());
    assertTrue(repository.failureClaimed);
    assertFalse(repository.completionClaimed);
    assertTrue(lifecycleUpdater.failed);
    assertFalse(lifecycleUpdater.completed);
    assertEquals(
        0, publisher.events.stream().filter(HistoryJobCompletedEvent.class::isInstance).count());
    assertEquals(
        1, publisher.events.stream().filter(HistoryJobFailedEvent.class::isInstance).count());
  }

  @Test
  void executionFailureWithoutDeliveryFailureDoesNotPublishDeliveryFailedTerminal() {
    var repository = new InMemoryDeliveryRepository();
    var lifecycleUpdater = new RecordingLifecycleUpdater();
    var publisher = new RecordingPublisher();
    var coordinator =
        new HistoryJobDeliveryCoordinator(
            repository, lifecycleUpdater, publisher, Duration.ofHours(24));
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440702");

    repository.ensureInitialized(
        jobId,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440700"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440701"),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440703"),
        2,
        0,
        true,
        Duration.ofHours(24));

    coordinator.onExecutionFinished(jobId, 1, 1);

    assertFalse(repository.failureClaimed);
    assertFalse(repository.completionClaimed);
    assertFalse(lifecycleUpdater.failed);
    assertFalse(lifecycleUpdater.completed);
    assertEquals(0, publisher.events.size());
  }

  @Test
  void deliveryFailureAfterExecutionComplete_blocksLaterCompletionPublication() {
    var repository = new InMemoryDeliveryRepository();
    var lifecycleUpdater = new RecordingLifecycleUpdater();
    var publisher = new RecordingPublisher();
    var coordinator =
        new HistoryJobDeliveryCoordinator(
            repository, lifecycleUpdater, publisher, Duration.ofHours(24));
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440402");

    repository.ensureInitialized(
        jobId,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440400"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440401"),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440403"),
        2,
        0,
        true,
        Duration.ofHours(24));
    repository.markExecutionComplete(jobId, 2, 0, Duration.ofHours(24));

    coordinator.onChunkDeliveryFailed(jobId, "payload send failed");
    coordinator.onChunkDelivered(jobId);

    assertTrue(repository.failureClaimed);
    assertFalse(repository.completionClaimed);
    assertTrue(lifecycleUpdater.failed);
    assertFalse(lifecycleUpdater.completed);
    assertEquals(
        0, publisher.events.stream().filter(HistoryJobCompletedEvent.class::isInstance).count());
    assertEquals(
        1, publisher.events.stream().filter(HistoryJobFailedEvent.class::isInstance).count());
  }

  @Test
  void onChunkDelivered_afterExecutionComplete_publishesProgressAndCompletion() {
    var repository = new InMemoryDeliveryRepository();
    var lifecycleUpdater = new RecordingLifecycleUpdater();
    var publisher = new RecordingPublisher();
    var coordinator =
        new HistoryJobDeliveryCoordinator(
            repository, lifecycleUpdater, publisher, Duration.ofHours(24));
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440202");

    repository.ensureInitialized(
        jobId,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440200"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440201"),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440203"),
        1,
        0,
        true,
        Duration.ofHours(24));
    repository.markExecutionComplete(jobId, 1, 0, Duration.ofHours(24));

    coordinator.onChunkDelivered(jobId);

    assertEquals(3, publisher.events.size());
    assertTrue(publisher.events.getFirst() instanceof HistoryJobProgressEvent);
    assertTrue(publisher.events.get(1) instanceof HistoryJobProgressEvent);
    assertTrue(publisher.events.get(2) instanceof HistoryJobCompletedEvent);
    assertTrue(repository.completionClaimed);
    assertFalse(repository.failureClaimed);
    assertTrue(lifecycleUpdater.completed);
    assertFalse(lifecycleUpdater.failed);
  }

  @Test
  void onChunkDelivered_doesNotPublishCompletionWhenLifecycleUpdateFails() {
    var repository = new InMemoryDeliveryRepository();
    var lifecycleUpdater = new RecordingLifecycleUpdater();
    lifecycleUpdater.completedResult = false;
    var publisher = new RecordingPublisher();
    var coordinator =
        new HistoryJobDeliveryCoordinator(
            repository, lifecycleUpdater, publisher, Duration.ofHours(24));
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440502");

    repository.ensureInitialized(
        jobId,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440500"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440501"),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440503"),
        1,
        0,
        true,
        Duration.ofHours(24));
    repository.markExecutionComplete(jobId, 1, 0, Duration.ofHours(24));

    coordinator.onChunkDelivered(jobId);

    assertTrue(repository.completionClaimed);
    assertTrue(lifecycleUpdater.completed);
    assertEquals(
        0, publisher.events.stream().filter(HistoryJobCompletedEvent.class::isInstance).count());
  }

  @Test
  void onChunkDeliveryFailed_doesNotPublishFailureWhenLifecycleUpdateFails() {
    var repository = new InMemoryDeliveryRepository();
    var lifecycleUpdater = new RecordingLifecycleUpdater();
    lifecycleUpdater.failedResult = false;
    var publisher = new RecordingPublisher();
    var coordinator =
        new HistoryJobDeliveryCoordinator(
            repository, lifecycleUpdater, publisher, Duration.ofHours(24));
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440602");

    repository.ensureInitialized(
        jobId,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440600"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440601"),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440603"),
        1,
        0,
        true,
        Duration.ofHours(24));
    repository.markExecutionComplete(jobId, 1, 0, Duration.ofHours(24));

    coordinator.onChunkDeliveryFailed(jobId, "payload send failed");

    assertTrue(repository.failureClaimed);
    assertTrue(lifecycleUpdater.failed);
    assertEquals(
        0, publisher.events.stream().filter(HistoryJobFailedEvent.class::isInstance).count());
  }

  private static final class RecordingLifecycleUpdater implements HistoricalJobLifecycleUpdater {
    private boolean completed;
    private boolean failed;
    private String failureReason;
    private boolean completedResult = true;
    private boolean failedResult = true;

    @Override
    public boolean markCompleted(UUID jobId, Duration ttl) {
      completed = true;
      return completedResult;
    }

    @Override
    public boolean markFailed(UUID jobId, String failureReason, Duration ttl) {
      this.failed = true;
      this.failureReason = failureReason;
      return failedResult;
    }
  }

  private static final class RecordingPublisher implements CachePublisher<HistoryEvent> {
    private final List<HistoryEvent> events = new ArrayList<>();

    @Override
    public void publish(HistoryEvent event) {
      events.add(event);
    }
  }

  private static final class InMemoryDeliveryRepository
      extends RedisHistoricalJobDeliveryRepository {
    private HistoricalJobDeliverySnapshot snapshot;
    private long executionFailedChunks;
    private long deliveryFailedChunks;
    private boolean completionClaimed;
    private boolean failureClaimed;

    InMemoryDeliveryRepository() {
      super(null);
    }

    @Override
    public void ensureInitialized(
        UUID jobId,
        UUID targetUserId,
        UUID requestId,
        SubjectType subjectType,
        UUID subjectId,
        long totalChunks,
        long cachedChunks,
        boolean userLifecycle,
        Duration ttl) {
      snapshot =
          new HistoricalJobDeliverySnapshot(
              targetUserId,
              requestId,
              jobId,
              subjectType,
              subjectId,
              totalChunks,
              cachedChunks,
              0,
              0,
              0,
              false,
              userLifecycle,
              null);
      executionFailedChunks = 0;
      deliveryFailedChunks = 0;
    }

    @Override
    public void recordExecutionProgress(
        UUID jobId, long computedChunks, long failedChunks, Duration ttl) {
      executionFailedChunks = failedChunks;
      snapshot =
          new HistoricalJobDeliverySnapshot(
              snapshot.targetUserId(),
              snapshot.requestId(),
              snapshot.jobId(),
              snapshot.subjectType(),
              snapshot.subjectId(),
              snapshot.totalChunks(),
              snapshot.cachedChunks(),
              computedChunks,
              executionFailedChunks + deliveryFailedChunks,
              snapshot.deliveredChunks(),
              snapshot.executionComplete(),
              snapshot.userLifecycle(),
              snapshot.failureReason());
    }

    @Override
    public void markExecutionComplete(
        UUID jobId, long computedChunks, long failedChunks, Duration ttl) {
      executionFailedChunks = failedChunks;
      snapshot =
          new HistoricalJobDeliverySnapshot(
              snapshot.targetUserId(),
              snapshot.requestId(),
              snapshot.jobId(),
              snapshot.subjectType(),
              snapshot.subjectId(),
              snapshot.totalChunks(),
              snapshot.cachedChunks(),
              computedChunks,
              executionFailedChunks + deliveryFailedChunks,
              snapshot.deliveredChunks(),
              true,
              snapshot.userLifecycle(),
              snapshot.failureReason());
    }

    @Override
    public long incrementDeliveredIfTracked(UUID jobId, Duration ttl) {
      snapshot =
          new HistoricalJobDeliverySnapshot(
              snapshot.targetUserId(),
              snapshot.requestId(),
              snapshot.jobId(),
              snapshot.subjectType(),
              snapshot.subjectId(),
              snapshot.totalChunks(),
              snapshot.cachedChunks(),
              snapshot.computedChunks(),
              snapshot.failedChunks(),
              snapshot.deliveredChunks() + 1,
              snapshot.executionComplete(),
              snapshot.userLifecycle(),
              snapshot.failureReason());
      return snapshot.deliveredChunks();
    }

    @Override
    public long incrementFailedIfTracked(UUID jobId, Duration ttl, String failureReason) {
      deliveryFailedChunks++;
      snapshot =
          new HistoricalJobDeliverySnapshot(
              snapshot.targetUserId(),
              snapshot.requestId(),
              snapshot.jobId(),
              snapshot.subjectType(),
              snapshot.subjectId(),
              snapshot.totalChunks(),
              snapshot.cachedChunks(),
              snapshot.computedChunks(),
              executionFailedChunks + deliveryFailedChunks,
              snapshot.deliveredChunks(),
              snapshot.executionComplete(),
              snapshot.userLifecycle(),
              failureReason);
      return snapshot.failedChunks();
    }

    @Override
    public boolean hasDeliveryFailure(UUID jobId) {
      return deliveryFailedChunks > 0;
    }

    @Override
    public boolean hasFailurePublicationClaim(UUID jobId) {
      return failureClaimed;
    }

    @Override
    public Optional<HistoricalJobDeliverySnapshot> loadSnapshot(UUID jobId) {
      return Optional.ofNullable(snapshot);
    }

    @Override
    public boolean tryClaimCompletionPublication(UUID jobId, Duration ttl) {
      if (completionClaimed || failureClaimed || deliveryFailedChunks > 0) {
        return false;
      }
      completionClaimed = true;
      return true;
    }

    @Override
    public boolean tryClaimFailurePublication(UUID jobId, Duration ttl) {
      if (failureClaimed || completionClaimed) {
        return false;
      }
      failureClaimed = true;
      return true;
    }
  }
}
