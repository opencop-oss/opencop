// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.kafka;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.core.sdk.model.job.LayerCopyJobSubmission;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

class KafkaJobPublisherTest {

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void scheduleAfterCommit_whenNoStrategySupportsSubmission_usesDefaultKafkaSend()
      throws Exception {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    ObjectMapper objectMapper = new ObjectMapper();
    var publisher = publisher(kafkaTemplate, objectMapper, List.of());
    var submission = layerCopySubmission();
    byte[] payload = objectMapper.writeValueAsBytes(submission);
    when(kafkaTemplate.send(submission.topic(), submission.key(), payload))
        .thenReturn(CompletableFuture.completedFuture(null));

    TransactionSynchronizationManager.initSynchronization();
    publisher.scheduleAfterCommit(submission);
    TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();

    verify(kafkaTemplate).send(submission.topic(), submission.key(), payload);
  }

  @Test
  void scheduleAfterCommit_whenDefaultPublishFails_afterCommitDoesNotThrow() throws Exception {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    ObjectMapper objectMapper = new ObjectMapper();
    var publisher = publisher(kafkaTemplate, objectMapper, List.of());
    var submission = layerCopySubmission();
    byte[] payload = objectMapper.writeValueAsBytes(submission);
    when(kafkaTemplate.send(submission.topic(), submission.key(), payload))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("boom")));

    TransactionSynchronizationManager.initSynchronization();
    publisher.scheduleAfterCommit(submission);
    var synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();

    assertDoesNotThrow(synchronization::afterCommit);
    verify(kafkaTemplate).send(submission.topic(), submission.key(), payload);
  }

  @Test
  @SuppressWarnings("unchecked")
  void scheduleAfterCommit_whenDefaultPublishFails_invokesFailureCallback() throws Exception {
    KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    ObjectMapper objectMapper = new ObjectMapper();
    var publisher = publisher(kafkaTemplate, objectMapper, List.of());
    var submission = layerCopySubmission();
    byte[] payload = objectMapper.writeValueAsBytes(submission);
    when(kafkaTemplate.send(submission.topic(), submission.key(), payload))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("boom")));
    Consumer<Exception> afterFailedPublish = mock(Consumer.class);

    TransactionSynchronizationManager.initSynchronization();
    publisher.scheduleAfterCommit(submission, afterFailedPublish);
    var synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();

    assertDoesNotThrow(synchronization::afterCommit);
    verify(afterFailedPublish).accept(any(IllegalStateException.class));
  }

  @Test
  void scheduleBatchAfterCommit_whenFirstPublishFails_doesNotInvokeSuccessCallback()
      throws Exception {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    ObjectMapper objectMapper = new ObjectMapper();
    var publisher = publisher(kafkaTemplate, objectMapper, List.of());
    var first = layerCopySubmission();
    var second = layerCopySubmission();
    @SuppressWarnings("unchecked")
    CompletableFuture<SendResult<String, byte[]>> failedFuture = mock(CompletableFuture.class);
    when(failedFuture.get(any(Long.class), any())).thenThrow(new ExecutionException("boom", null));
    when(kafkaTemplate.send(any(String.class), any(String.class), any(byte[].class)))
        .thenReturn(failedFuture)
        .thenReturn(CompletableFuture.completedFuture(null));
    Runnable afterSuccessfulBatch = mock(Runnable.class);

    TransactionSynchronizationManager.initSynchronization();
    publisher.scheduleBatchAfterCommit(List.of(first, second), afterSuccessfulBatch);
    TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();

    verify(afterSuccessfulBatch, never()).run();
  }

  @Test
  void scheduleBatchAfterCommit_whenAllPublishesSucceed_invokesSuccessCallbackOnce() {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    ObjectMapper objectMapper = new ObjectMapper();
    when(kafkaTemplate.send(any(String.class), any(String.class), any(byte[].class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    var publisher = publisher(kafkaTemplate, objectMapper, List.of());
    Runnable afterSuccessfulBatch = mock(Runnable.class);

    TransactionSynchronizationManager.initSynchronization();
    publisher.scheduleBatchAfterCommit(
        List.of(layerCopySubmission(), layerCopySubmission()), afterSuccessfulBatch);
    TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();

    verify(afterSuccessfulBatch, times(1)).run();
  }

  @Test
  void scheduleBatchAfterCommit_whenBatchIsEmpty_invokesSuccessCallbackAndDoesNotSend() {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    ObjectMapper objectMapper = new ObjectMapper();
    var publisher = publisher(kafkaTemplate, objectMapper, List.of());
    Runnable afterSuccessfulBatch = mock(Runnable.class);

    TransactionSynchronizationManager.initSynchronization();
    publisher.scheduleBatchAfterCommit(List.of(), afterSuccessfulBatch);
    TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();

    verify(afterSuccessfulBatch, times(1)).run();
    verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any(byte[].class));
  }

  @Test
  void scheduleBatchAfterCommit_whenSecondPublishFails_logsPartialPublishCounts() throws Exception {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    ObjectMapper objectMapper = new ObjectMapper();
    var publisher = publisher(kafkaTemplate, objectMapper, List.of());
    ListAppender<ILoggingEvent> appender = attachAppender();
    @SuppressWarnings("unchecked")
    CompletableFuture<SendResult<String, byte[]>> success = mock(CompletableFuture.class);
    when(success.get(any(Long.class), any())).thenReturn(null);
    @SuppressWarnings("unchecked")
    CompletableFuture<SendResult<String, byte[]>> failed = mock(CompletableFuture.class);
    when(failed.get(any(Long.class), any())).thenThrow(new ExecutionException("boom", null));
    when(kafkaTemplate.send(any(String.class), any(String.class), any(byte[].class)))
        .thenReturn(success)
        .thenReturn(failed);
    Runnable afterSuccessfulBatch = mock(Runnable.class);

    TransactionSynchronizationManager.initSynchronization();
    publisher.scheduleBatchAfterCommit(
        List.of(layerCopySubmission(), layerCopySubmission()), afterSuccessfulBatch);
    var synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();

    assertDoesNotThrow(synchronization::afterCommit);
    verify(afterSuccessfulBatch, never()).run();
    String errorMessages =
        appender.list.stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (left, right) -> left + "\n" + right);
    assertTrue(errorMessages.contains("publishedCount=1"));
    assertTrue(errorMessages.contains("totalCount=2"));
  }

  @Test
  void scheduleBatchAfterCommit_whenCallbackFails_logsCallbackFailureSeparately() {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    ObjectMapper objectMapper = new ObjectMapper();
    when(kafkaTemplate.send(any(String.class), any(String.class), any(byte[].class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    var publisher = publisher(kafkaTemplate, objectMapper, List.of());
    ListAppender<ILoggingEvent> appender = attachAppender();
    Runnable afterSuccessfulBatch =
        () -> {
          throw new IllegalStateException("callback exploded");
        };

    TransactionSynchronizationManager.initSynchronization();
    publisher.scheduleBatchAfterCommit(
        List.of(layerCopySubmission(), layerCopySubmission()), afterSuccessfulBatch);
    var synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();

    assertDoesNotThrow(synchronization::afterCommit);
    String errorMessages =
        appender.list.stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (left, right) -> left + "\n" + right);
    assertTrue(errorMessages.contains("Post-publish cancellation callback failed"));
    assertTrue(!errorMessages.contains("Failed to publish after-commit job batch"));
  }

  @Test
  void scheduleAfterCommit_whenStrategySupportsSubmission_usesStrategyInsteadOfDefaultSend() {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    ObjectMapper objectMapper = new ObjectMapper();
    var strategy = new RecordingStrategy();
    var publisher = publisher(kafkaTemplate, objectMapper, List.of(strategy));
    var submission = layerCopySubmission();

    TransactionSynchronizationManager.initSynchronization();
    publisher.scheduleAfterCommit(submission);
    TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();

    assertEquals(1, strategy.publishCount);
    assertEquals(submission, strategy.submission);
    verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any(byte[].class));
  }

  private static KafkaJobPublisher publisher(
      KafkaTemplate<String, byte[]> kafkaTemplate,
      ObjectMapper objectMapper,
      List<KafkaJobPublishStrategy> strategies) {
    return new KafkaJobPublisher(
        kafkaTemplate, objectMapper, strategies, new JobPublishLogSanitizer());
  }

  private static LayerCopyJobSubmission layerCopySubmission() {
    return LayerCopyJobSubmission.create(
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446099")),
        UUID.fromString("550e8400-e29b-41d4-a716-446655446011"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655446012"),
        true,
        false);
  }

  @SuppressWarnings("unchecked")
  private static ListAppender<ILoggingEvent> attachAppender() {
    Logger logger = (Logger) LoggerFactory.getLogger(KafkaJobPublisher.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.detachAndStopAllAppenders();
    logger.addAppender(appender);
    return appender;
  }

  private static final class RecordingStrategy implements KafkaJobPublishStrategy {
    private int publishCount;
    private JobSubmission submission;

    @Override
    public boolean supports(JobSubmission submission) {
      return true;
    }

    @Override
    public void publish(JobSubmission submission, byte[] payload) {
      this.publishCount++;
      this.submission = submission;
    }
  }
}
