// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka;

import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.OPENCOP_TYPE;

import com.opencop.core.sdk.ports.gateway.broker.BrokerMessage;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.InvalidPidMappingException;
import org.apache.kafka.common.errors.InvalidProducerEpochException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

public class AcknowledgingKafkaProducer<T> implements MessageBrokerProducer<T> {

  private static final Logger log = LoggerFactory.getLogger(AcknowledgingKafkaProducer.class);

  private final String topic;
  private final KafkaTemplate<String, T> kafkaTemplate;
  private final Duration acknowledgementTimeout;
  private final ProducerFactoryResetter producerFactoryResetter;

  public AcknowledgingKafkaProducer(
      String topic,
      KafkaTemplate<String, T> kafkaTemplate,
      Duration acknowledgementTimeout,
      ProducerFactoryResetter producerFactoryResetter) {
    this.topic = Objects.requireNonNull(topic, "topic");
    this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
    this.acknowledgementTimeout =
        Objects.requireNonNull(acknowledgementTimeout, "acknowledgementTimeout");
    this.producerFactoryResetter =
        Objects.requireNonNull(producerFactoryResetter, "producerFactoryResetter");
  }

  @Override
  public void send(T event, Map<String, String> headers) {
    sendAll(List.of(BrokerMessage.of(event, headers)));
  }

  @Override
  public void sendDlt(T event) {
    sendAll(List.of(BrokerMessage.of(event)));
  }

  @Override
  public void send(T event) {
    sendAll(List.of(BrokerMessage.of(event)));
  }

  @Override
  public void sendAll(Collection<BrokerMessage<T>> messages) {
    if (messages.isEmpty()) {
      return;
    }

    List<BrokerMessage<T>> batch = List.copyOf(messages);
    long resetGeneration = producerFactoryResetter.generation();
    BatchOutcome<T> firstAttempt = sendAndClassify(batch);
    if (firstAttempt.succeeded()) {
      return;
    }

    if (!firstAttempt.canResetAndRetry()) {
      throw loggedFailure(firstAttempt, batch.size(), false);
    }

    BatchOutcome<T> retryAttempt;
    try {
      retryAttempt =
          producerFactoryResetter.resetAndRun(
              resetGeneration, () -> sendAndClassify(firstAttempt.resettableMessages()));
    } catch (RuntimeException e) {
      throw loggedFailure(firstAttempt.withRepresentativeException(e), batch.size(), true);
    }

    if (retryAttempt.succeeded()) {
      return;
    }

    throw loggedFailure(
        retryAttempt.withAcknowledgedOffset(firstAttempt.acknowledgedCount()), batch.size(), true);
  }

  private BatchOutcome<T> sendAndClassify(Collection<BrokerMessage<T>> messages) {
    List<TrackedSend<T>> sends = dispatch(messages);
    BatchWaitResult<T> waitResult = awaitBatch(sends);
    BatchOutcome<T> outcome = classify(sends, waitResult.timedOutSends());
    return waitResult.timedOut() ? outcome.withForcedTimeoutIfAbsent() : outcome;
  }

  private List<TrackedSend<T>> dispatch(Collection<BrokerMessage<T>> messages) {
    var sends = new ArrayList<TrackedSend<T>>(messages.size());
    for (BrokerMessage<T> message : messages) {
      CompletableFuture<SendResult<String, T>> future;
      try {
        future = kafkaTemplate.send(producerRecord(message));
      } catch (RuntimeException e) {
        future = CompletableFuture.failedFuture(e);
      }
      sends.add(new TrackedSend<>(message, future));
    }
    return sends;
  }

  private BatchWaitResult<T> awaitBatch(List<TrackedSend<T>> sends) {
    CompletableFuture<?>[] futures =
        sends.stream().map(TrackedSend::future).toArray(CompletableFuture[]::new);
    try {
      CompletableFuture.allOf(futures).get(acknowledgementTimeout.toNanos(), TimeUnit.NANOSECONDS);
      return new BatchWaitResult<>(false, List.of());
    } catch (TimeoutException _) {
      return new BatchWaitResult<>(true, timedOutSends(sends));
    } catch (ExecutionException _) {
      // Classification below accounts for every future outcome.
      return new BatchWaitResult<>(false, List.of());
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      return new BatchWaitResult<>(true, timedOutSends(sends));
    }
  }

  private List<TrackedSend<T>> timedOutSends(List<TrackedSend<T>> sends) {
    return sends.stream().filter(send -> send.future().state() == Future.State.RUNNING).toList();
  }

  private BatchOutcome<T> classify(List<TrackedSend<T>> sends, List<TrackedSend<T>> timedOutSends) {
    int acknowledgedCount = 0;
    int timeoutCount = 0;
    int resettableFailureCount = 0;
    int producerStateFailureCount = 0;
    int nonResettableFailureCount = 0;
    Throwable representativeException = null;
    var resettableMessages = new ArrayList<BrokerMessage<T>>();

    for (TrackedSend<T> send : sends) {
      if (timedOutSends.contains(send)) {
        timeoutCount++;
        representativeException =
            firstRepresentative(
                representativeException,
                new TimeoutException("Timed out waiting for Kafka send acknowledgement"));
        continue;
      }

      try {
        send.future().join();
        acknowledgedCount++;
      } catch (CancellationException | CompletionException e) {
        Throwable failure = unwrap(e);
        Throwable representativeFailure = representativeFailure(failure);
        representativeException =
            firstRepresentative(representativeException, representativeFailure);
        if (resettableFailureCause(failure) != null) {
          resettableFailureCount++;
          producerStateFailureCount++;
          resettableMessages.add(send.message());
        } else if (isProducerClosedCollateral(failure)) {
          resettableFailureCount++;
          resettableMessages.add(send.message());
        } else {
          nonResettableFailureCount++;
        }
      }
    }

    return new BatchOutcome<>(
        acknowledgedCount,
        timeoutCount,
        resettableFailureCount,
        producerStateFailureCount,
        nonResettableFailureCount,
        representativeException,
        resettableMessages);
  }

  private ProducerRecord<String, T> producerRecord(BrokerMessage<T> message) {
    ProducerRecord<String, T> producerRecord =
        new ProducerRecord<>(topic, message.key(), message.event());
    message
        .headers()
        .forEach(
            (key, value) ->
                producerRecord.headers().add(key, value.getBytes(StandardCharsets.UTF_8)));
    if (message.event() != null) {
      producerRecord
          .headers()
          .add(
              OPENCOP_TYPE.getName(),
              message.event().getClass().getName().getBytes(StandardCharsets.UTF_8));
    }
    return producerRecord;
  }

  private AcknowledgedKafkaPublishException loggedFailure(
      BatchOutcome<T> outcome, int batchSize, boolean resetAttempted) {
    var failure =
        new AcknowledgedKafkaPublishException(
            topic,
            new AcknowledgedKafkaPublishException.FailureSummary(
                batchSize,
                outcome.acknowledgedCount(),
                outcome.timeoutCount(),
                outcome.resettableFailureCount(),
                outcome.nonResettableFailureCount(),
                resetAttempted),
            outcome.representativeException());
    log.error(
        "Acknowledged Kafka publish failed: topic={}, batchSize={}, acknowledgedCount={},"
            + " timeoutCount={}, resettableFailureCount={}, nonResettableFailureCount={},"
            + " resetAttempted={}, representativeExceptionClass={},"
            + " representativeExceptionMessage={}",
        failure.topic(),
        failure.batchSize(),
        failure.acknowledgedCount(),
        failure.timeoutCount(),
        failure.resettableFailureCount(),
        failure.nonResettableFailureCount(),
        failure.resetAttempted(),
        failure.representativeExceptionClass(),
        failure.representativeExceptionMessage());
    return failure;
  }

  private static Throwable unwrap(Throwable failure) {
    if ((failure instanceof CompletionException || failure instanceof ExecutionException)
        && failure.getCause() != null) {
      return failure.getCause();
    }
    return failure;
  }

  private static Throwable firstRepresentative(Throwable existing, Throwable candidate) {
    return existing == null ? candidate : existing;
  }

  private static boolean isResettableFailure(Throwable failure) {
    return failure instanceof InvalidPidMappingException
        || failure instanceof ProducerFencedException
        || failure instanceof InvalidProducerEpochException
        || failure instanceof OutOfOrderSequenceException;
  }

  private static boolean isProducerClosedCollateral(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if ((current instanceof IllegalStateException || current instanceof KafkaException)
          && isProducerClosedMessage(current.getMessage())) {
        return true;
      }
      if (current.getCause() == current) {
        return false;
      }
      current = current.getCause();
    }
    return false;
  }

  private static boolean isProducerClosedMessage(String message) {
    if (message == null) {
      return false;
    }
    String normalized = message.toLowerCase(Locale.ROOT);
    return normalized.contains("producer") && normalized.contains("closed");
  }

  private static Throwable representativeFailure(Throwable failure) {
    Throwable resettableCause = resettableFailureCause(failure);
    return resettableCause == null ? rootCause(failure) : resettableCause;
  }

  private static Throwable resettableFailureCause(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (isResettableFailure(current)) {
        return current;
      }
      if (current.getCause() == current) {
        return null;
      }
      current = current.getCause();
    }
    return null;
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current;
  }

  private record TrackedSend<T>(
      BrokerMessage<T> message, CompletableFuture<SendResult<String, T>> future) {}

  private record BatchWaitResult<T>(boolean timedOut, List<TrackedSend<T>> timedOutSends) {}

  private record BatchOutcome<T>(
      int acknowledgedCount,
      int timeoutCount,
      int resettableFailureCount,
      int producerStateFailureCount,
      int nonResettableFailureCount,
      Throwable representativeException,
      List<BrokerMessage<T>> resettableMessages) {

    boolean succeeded() {
      return timeoutCount == 0 && resettableFailureCount == 0 && nonResettableFailureCount == 0;
    }

    boolean canResetAndRetry() {
      return timeoutCount == 0
          && nonResettableFailureCount == 0
          && producerStateFailureCount > 0
          && resettableFailureCount > 0;
    }

    BatchOutcome<T> withForcedTimeoutIfAbsent() {
      if (timeoutCount > 0) {
        return this;
      }
      return new BatchOutcome<>(
          acknowledgedCount,
          1,
          resettableFailureCount,
          producerStateFailureCount,
          nonResettableFailureCount,
          firstRepresentative(
              representativeException,
              new TimeoutException("Timed out waiting for Kafka send acknowledgement")),
          resettableMessages);
    }

    BatchOutcome<T> withAcknowledgedOffset(int acknowledgedOffset) {
      return new BatchOutcome<>(
          acknowledgedOffset + acknowledgedCount,
          timeoutCount,
          resettableFailureCount,
          producerStateFailureCount,
          nonResettableFailureCount,
          representativeException,
          resettableMessages);
    }

    BatchOutcome<T> withRepresentativeException(Throwable representativeException) {
      return new BatchOutcome<>(
          acknowledgedCount,
          timeoutCount,
          resettableFailureCount,
          producerStateFailureCount,
          nonResettableFailureCount,
          representativeException,
          resettableMessages);
    }
  }
}
