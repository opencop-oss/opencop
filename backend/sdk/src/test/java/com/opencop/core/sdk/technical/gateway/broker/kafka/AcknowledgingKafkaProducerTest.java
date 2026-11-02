// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka;

import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.OPENCOP_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.ports.gateway.broker.BrokerMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.InvalidPidMappingException;
import org.apache.kafka.common.errors.InvalidProducerEpochException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaProducerException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class AcknowledgingKafkaProducerTest {

  private static final String TOPIC = "mapped-output";
  private static final int MAX_REPRESENTATIVE_EXCEPTION_MESSAGE_LENGTH = 512;

  private final Logger logger = (Logger) LoggerFactory.getLogger(AcknowledgingKafkaProducer.class);
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUpAppender() {
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDownAppender() {
    logger.detachAppender(appender);
  }

  @Test
  void sendAll_dispatchesEveryMessageBeforeWaitingForAcknowledgement() throws Exception {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    var first = new CompletableFuture<SendResult<String, String>>();
    var second = new CompletableFuture<SendResult<String, String>>();
    var sendCount = new AtomicInteger();
    var allDispatched = new CountDownLatch(1);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenAnswer(
            _ -> {
              int count = sendCount.incrementAndGet();
              if (count == 2) {
                allDispatched.countDown();
              }
              return count == 1 ? first : second;
            });
    var producer = producer(kafkaTemplate, resetter(), Duration.ofSeconds(5));

    CompletableFuture<Void> publish =
        CompletableFuture.runAsync(
            () -> producer.sendAll(List.of(BrokerMessage.of("one"), BrokerMessage.of("two"))));

    assertTrue(allDispatched.await(1, TimeUnit.SECONDS));
    assertThat(sendCount).hasValue(2);
    first.complete(null);
    second.complete(null);
    publish.get(1, TimeUnit.SECONDS);
  }

  @Test
  void sendAll_returnsWhenEveryFutureIsAcknowledged() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    var resetter = resetter();

    producer(kafkaTemplate, resetter, Duration.ofSeconds(1))
        .sendAll(List.of(BrokerMessage.of("one"), BrokerMessage.of("two")));

    ArgumentCaptor<ProducerRecord<String, String>> records = producerRecordCaptor();
    verify(kafkaTemplate, times(2)).send(records.capture());
    assertThat(records.getAllValues())
        .extracting(AcknowledgingKafkaProducerTest::typeHeader)
        .containsExactly(String.class.getName(), String.class.getName());
    assertThat(resetter.resetCount()).isZero();
  }

  @Test
  void sendAll_classifiesSynchronousSendThrowAsFailedRecord() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    var sendFailure = new IllegalStateException("serializer failed before future");
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenThrow(sendFailure);
    var resetter = resetter();

    assertThatThrownBy(
            () ->
                producer(kafkaTemplate, resetter, Duration.ofSeconds(1))
                    .sendAll(List.of(BrokerMessage.of("one"))))
        .isInstanceOf(AcknowledgedKafkaPublishException.class)
        .satisfies(
            throwable -> {
              var failure = (AcknowledgedKafkaPublishException) throwable;
              assertThat(failure.nonResettableFailureCount()).isEqualTo(1);
              assertThat(failure.resettableFailureCount()).isZero();
              assertThat(failure.timeoutCount()).isZero();
              assertThat(failure.resetAttempted()).isFalse();
              assertThat(failure.representativeExceptionClass()).isEqualTo("IllegalStateException");
              assertThat(failure.representativeExceptionMessage())
                  .isEqualTo("serializer failed before future");
              assertThat(failure.getCause()).isSameAs(sendFailure);
            });

    assertThat(resetter.resetCount()).isZero();
    verify(kafkaTemplate).send(any(ProducerRecord.class));
  }

  @Test
  void sendAll_preservesBrokerMessageKeyAndHeaders() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    var resetter = resetter();

    producer(kafkaTemplate, resetter, Duration.ofSeconds(1))
        .sendAll(
            List.of(BrokerMessage.withKey("source-key", "payload", Map.of("trace-id", "abc"))));

    ArgumentCaptor<ProducerRecord<String, String>> records = producerRecordCaptor();
    verify(kafkaTemplate).send(records.capture());
    ProducerRecord<String, String> sentRecord = records.getValue();
    assertThat(sentRecord.key()).isEqualTo("source-key");
    assertThat(sentRecord.value()).isEqualTo("payload");
    assertThat(header(sentRecord, "trace-id")).isEqualTo("abc");
    assertThat(typeHeader(sentRecord)).isEqualTo(String.class.getName());
  }

  @Test
  void sendAll_timesOutTheBatchWithoutResetOrRetry() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());
    var resetter = resetter();

    assertThatThrownBy(
            () ->
                producer(kafkaTemplate, resetter, Duration.ofMillis(20))
                    .sendAll(List.of(BrokerMessage.of("one"), BrokerMessage.of("two"))))
        .isInstanceOf(AcknowledgedKafkaPublishException.class)
        .satisfies(
            throwable -> {
              var failure = (AcknowledgedKafkaPublishException) throwable;
              assertThat(failure.timeoutCount()).isEqualTo(2);
              assertThat(failure.resetAttempted()).isFalse();
            });

    assertThat(resetter.resetCount()).isZero();
    verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
  }

  @Test
  void sendAll_doesNotRetryWhenFutureCompletesAsResettableAfterTheBatchTimeout() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(
            new CompletingOnIsDoneFuture<>(
                new InvalidPidMappingException("completed after timeout")))
        .thenReturn(CompletableFuture.completedFuture(null));
    var resetter = resetter();

    assertThatThrownBy(
            () ->
                producer(kafkaTemplate, resetter, Duration.ofMillis(20))
                    .sendAll(List.of(BrokerMessage.of("unknown-acknowledgement-state"))))
        .isInstanceOf(AcknowledgedKafkaPublishException.class)
        .satisfies(
            throwable -> {
              var failure = (AcknowledgedKafkaPublishException) throwable;
              assertThat(failure.timeoutCount()).isEqualTo(1);
              assertThat(failure.resetAttempted()).isFalse();
            });

    assertThat(resetter.resetCount()).isZero();
    verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
  }

  @ParameterizedTest
  @MethodSource("resettableProducerFailures")
  void sendAll_resetsOnceAndRetriesOnlyUnacknowledgedResettableFailures(
      RuntimeException resettableFailure) {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null))
        .thenReturn(CompletableFuture.failedFuture(resettableFailure))
        .thenReturn(CompletableFuture.completedFuture(null));
    var resetter = resetter();

    producer(kafkaTemplate, resetter, Duration.ofSeconds(1))
        .sendAll(List.of(BrokerMessage.of("acknowledged"), BrokerMessage.of("retry-me")));

    assertThat(resetter.resetCount()).isEqualTo(1);
    ArgumentCaptor<ProducerRecord<String, String>> records = producerRecordCaptor();
    verify(kafkaTemplate, times(3)).send(records.capture());
    assertThat(records.getAllValues())
        .extracting(ProducerRecord::value)
        .containsExactly("acknowledged", "retry-me", "retry-me");
  }

  @ParameterizedTest
  @MethodSource("resettableProducerFailures")
  void sendAll_resetsWhenSpringKafkaWrapsAResettableProducerFailure(
      RuntimeException resettableFailure) {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                new KafkaProducerException(
                    new ProducerRecord<>(TOPIC, "retry-me"), "send failed", resettableFailure)))
        .thenReturn(CompletableFuture.completedFuture(null));
    var resetter = resetter();

    producer(kafkaTemplate, resetter, Duration.ofSeconds(1))
        .sendAll(List.of(BrokerMessage.of("retry-me")));

    assertThat(resetter.resetCount()).isEqualTo(1);
    verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
  }

  @Test
  void sendAll_doesNotResetOrRetryMixedResettableAndNonResettableFailures() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(
            CompletableFuture.failedFuture(new InvalidPidMappingException("invalid producer id")))
        .thenReturn(CompletableFuture.failedFuture(new IllegalArgumentException("bad config")));
    var resetter = resetter();

    assertThatThrownBy(
            () ->
                producer(kafkaTemplate, resetter, Duration.ofSeconds(1))
                    .sendAll(List.of(BrokerMessage.of("one"), BrokerMessage.of("two"))))
        .isInstanceOf(AcknowledgedKafkaPublishException.class)
        .satisfies(
            throwable -> {
              var failure = (AcknowledgedKafkaPublishException) throwable;
              assertThat(failure.resettableFailureCount()).isEqualTo(1);
              assertThat(failure.nonResettableFailureCount()).isEqualTo(1);
              assertThat(failure.resetAttempted()).isFalse();
            });

    assertThat(resetter.resetCount()).isZero();
    verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
  }

  @Test
  void sendAll_retriesProducerClosedCollateralWhenMixedWithProducerStateFailure() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(
            CompletableFuture.failedFuture(new InvalidPidMappingException("invalid producer id")))
        .thenReturn(
            CompletableFuture.failedFuture(
                new IllegalStateException("Producer is closed forcefully.")))
        .thenReturn(CompletableFuture.completedFuture(null))
        .thenReturn(CompletableFuture.completedFuture(null));
    var resetter = resetter();

    producer(kafkaTemplate, resetter, Duration.ofSeconds(1))
        .sendAll(List.of(BrokerMessage.of("state-failure"), BrokerMessage.of("closed-collateral")));

    assertThat(resetter.resetCount()).isEqualTo(1);
    ArgumentCaptor<ProducerRecord<String, String>> records = producerRecordCaptor();
    verify(kafkaTemplate, times(4)).send(records.capture());
    assertThat(records.getAllValues())
        .extracting(ProducerRecord::value)
        .containsExactly(
            "state-failure", "closed-collateral", "state-failure", "closed-collateral");
  }

  @Test
  void sendAll_retriesInsideResetterCoordination() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    var resetter = new GuardedProducerFactoryResetter();
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(
            CompletableFuture.failedFuture(new InvalidPidMappingException("invalid producer id")))
        .thenAnswer(
            _ -> {
              assertThat(resetter.insideRecovery()).isTrue();
              return CompletableFuture.completedFuture(null);
            });

    producer(kafkaTemplate, resetter, Duration.ofSeconds(1))
        .sendAll(List.of(BrokerMessage.of("one")));

    assertThat(resetter.resetCount()).isEqualTo(1);
    verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
  }

  @Test
  void sendAll_retainsResetFailureAsCauseWhenResetAttemptThrows() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    var resetFailure = new IllegalStateException("producer factory reset failed");
    var resetter = new ThrowingProducerFactoryResetter(resetFailure);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(
            CompletableFuture.failedFuture(new InvalidPidMappingException("invalid producer id")));

    assertThatThrownBy(
            () ->
                producer(kafkaTemplate, resetter, Duration.ofSeconds(1))
                    .sendAll(List.of(BrokerMessage.of("one"))))
        .isInstanceOf(AcknowledgedKafkaPublishException.class)
        .satisfies(
            throwable -> {
              var failure = (AcknowledgedKafkaPublishException) throwable;
              assertThat(failure.resetAttempted()).isTrue();
              assertThat(failure.resettableFailureCount()).isEqualTo(1);
              assertThat(failure.nonResettableFailureCount()).isZero();
              assertThat(failure.representativeExceptionClass()).isEqualTo("IllegalStateException");
              assertThat(failure.representativeExceptionMessage())
                  .isEqualTo("producer factory reset failed");
              assertThat(failure.getCause()).isSameAs(resetFailure);
            });

    assertThat(resetter.resetCount()).isEqualTo(1);
    verify(kafkaTemplate).send(any(ProducerRecord.class));
  }

  @Test
  void sendAll_doesNotResetOrRetryWhenResettableFailureIsMixedWithTimeout() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(
            CompletableFuture.failedFuture(new InvalidPidMappingException("invalid producer id")))
        .thenReturn(new CompletableFuture<>());
    var resetter = resetter();

    assertThatThrownBy(
            () ->
                producer(kafkaTemplate, resetter, Duration.ofMillis(20))
                    .sendAll(List.of(BrokerMessage.of("one"), BrokerMessage.of("two"))))
        .isInstanceOf(AcknowledgedKafkaPublishException.class)
        .satisfies(
            throwable -> {
              var failure = (AcknowledgedKafkaPublishException) throwable;
              assertThat(failure.timeoutCount()).isEqualTo(1);
              assertThat(failure.resettableFailureCount()).isEqualTo(1);
              assertThat(failure.resetAttempted()).isFalse();
            });

    assertThat(resetter.resetCount()).isZero();
    verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
  }

  @Test
  void sendAll_whenRetryAttemptTimesOut_doesNotResetAgain() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(
            CompletableFuture.failedFuture(new InvalidPidMappingException("invalid producer id")))
        .thenReturn(new CompletableFuture<>());
    var resetter = resetter();

    assertThatThrownBy(
            () ->
                producer(kafkaTemplate, resetter, Duration.ofMillis(20))
                    .sendAll(List.of(BrokerMessage.of("retry-me"))))
        .isInstanceOf(AcknowledgedKafkaPublishException.class)
        .satisfies(
            throwable -> {
              var failure = (AcknowledgedKafkaPublishException) throwable;
              assertThat(failure.resetAttempted()).isTrue();
              assertThat(failure.timeoutCount()).isEqualTo(1);
            });

    assertThat(resetter.resetCount()).isEqualTo(1);
    verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
  }

  @Test
  void sendAll_whenRetryAttemptFails_doesNotResetAgain() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(
            CompletableFuture.failedFuture(new InvalidPidMappingException("invalid producer id")))
        .thenReturn(
            CompletableFuture.failedFuture(new InvalidPidMappingException("still invalid")));
    var resetter = resetter();

    assertThatThrownBy(
            () ->
                producer(kafkaTemplate, resetter, Duration.ofSeconds(1))
                    .sendAll(List.of(BrokerMessage.of("retry-me"))))
        .isInstanceOf(AcknowledgedKafkaPublishException.class)
        .satisfies(
            throwable -> {
              var failure = (AcknowledgedKafkaPublishException) throwable;
              assertThat(failure.resetAttempted()).isTrue();
              assertThat(failure.resettableFailureCount()).isEqualTo(1);
            });

    assertThat(resetter.resetCount()).isEqualTo(1);
    verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
  }

  @Test
  void singleSendMethodsUseAcknowledgedPathAndPreserveHeaders() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    var producer = producer(kafkaTemplate, resetter(), Duration.ofSeconds(1));

    producer.send("plain");
    producer.send("headered", Map.of("trace-id", "abc"));
    producer.sendDlt("dlt");

    ArgumentCaptor<ProducerRecord<String, String>> records = producerRecordCaptor();
    verify(kafkaTemplate, times(3)).send(records.capture());
    assertThat(records.getAllValues())
        .extracting(ProducerRecord::topic)
        .containsExactly(TOPIC, TOPIC, TOPIC);
    assertThat(header(records.getAllValues().get(1), "trace-id")).isEqualTo("abc");
    assertThat(records.getAllValues())
        .extracting(AcknowledgingKafkaProducerTest::typeHeader)
        .containsExactly(String.class.getName(), String.class.getName(), String.class.getName());
  }

  @Test
  void failureLogIncludesAggregateCounts() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.failedFuture(new IllegalArgumentException("bad config")));

    assertThatThrownBy(
            () ->
                producer(kafkaTemplate, resetter(), Duration.ofSeconds(1))
                    .sendAll(List.of(BrokerMessage.of("one"))))
        .isInstanceOf(AcknowledgedKafkaPublishException.class);

    String errorMessages =
        appender.list.stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (left, right) -> left + "\n" + right);
    assertThat(errorMessages)
        .contains("topic=mapped-output")
        .contains("batchSize=1")
        .contains("acknowledgedCount=0")
        .contains("timeoutCount=0")
        .contains("resettableFailureCount=0")
        .contains("nonResettableFailureCount=1")
        .contains("resetAttempted=false")
        .contains("representativeExceptionClass=IllegalArgumentException")
        .contains("representativeExceptionMessage=bad config");
  }

  @Test
  void failureMessageAndLogRedactAndBoundRepresentativeExceptionMessage() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    String rawMessage = "password=hunter2 token=abc123 api-key=secret456 " + "x".repeat(700);
    var rawFailure = new IllegalArgumentException(rawMessage);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.failedFuture(rawFailure));

    assertThatThrownBy(
            () ->
                producer(kafkaTemplate, resetter(), Duration.ofSeconds(1))
                    .sendAll(List.of(BrokerMessage.of("one"))))
        .isInstanceOf(AcknowledgedKafkaPublishException.class)
        .satisfies(
            throwable -> {
              var failure = (AcknowledgedKafkaPublishException) throwable;
              assertThat(failure.representativeExceptionMessage())
                  .contains("password=[REDACTED]")
                  .contains("token=[REDACTED]")
                  .contains("api-key=[REDACTED]")
                  .doesNotContain("hunter2", "abc123", "secret456")
                  .endsWith("...[truncated]")
                  .hasSizeLessThanOrEqualTo(MAX_REPRESENTATIVE_EXCEPTION_MESSAGE_LENGTH);
              assertThat(failure.getMessage())
                  .doesNotContain("hunter2", "abc123", "secret456")
                  .contains(failure.representativeExceptionMessage());
              assertThat(failure.getCause()).isSameAs(rawFailure).hasMessage(rawMessage);
            });

    String errorMessages =
        appender.list.stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (left, right) -> left + "\n" + right);
    assertThat(errorMessages)
        .contains("password=[REDACTED]")
        .contains("token=[REDACTED]")
        .contains("api-key=[REDACTED]")
        .doesNotContain("hunter2", "abc123", "secret456")
        .contains("...[truncated]");
  }

  @SuppressWarnings("unchecked")
  private static KafkaTemplate<String, String> kafkaTemplate() {
    return mock(KafkaTemplate.class);
  }

  private static AcknowledgingKafkaProducer<String> producer(
      KafkaTemplate<String, String> kafkaTemplate,
      RecordingProducerFactoryResetter resetter,
      Duration acknowledgementTimeout) {
    return new AcknowledgingKafkaProducer<>(TOPIC, kafkaTemplate, acknowledgementTimeout, resetter);
  }

  private static RecordingProducerFactoryResetter resetter() {
    return new RecordingProducerFactoryResetter();
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<ProducerRecord<String, String>> producerRecordCaptor() {
    return ArgumentCaptor.forClass(ProducerRecord.class);
  }

  private static String header(ProducerRecord<String, String> record, String headerName) {
    return new String(record.headers().lastHeader(headerName).value(), StandardCharsets.UTF_8);
  }

  private static String typeHeader(ProducerRecord<String, String> record) {
    return header(record, OPENCOP_TYPE.getName());
  }

  private static Stream<RuntimeException> resettableProducerFailures() {
    return Stream.of(
        new InvalidPidMappingException("invalid producer id"),
        new ProducerFencedException("producer fenced"),
        new InvalidProducerEpochException("invalid producer epoch"),
        new OutOfOrderSequenceException("out of order sequence"));
  }

  private static final class CompletingOnIsDoneFuture<T> extends CompletableFuture<T> {
    private final RuntimeException failure;

    private CompletingOnIsDoneFuture(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public boolean isDone() {
      if (!super.isDone()) {
        completeExceptionally(failure);
      }
      return super.isDone();
    }
  }

  private static class RecordingProducerFactoryResetter implements ProducerFactoryResetter {
    private int resetCount;

    @Override
    public void reset() {
      resetCount++;
    }

    int resetCount() {
      return resetCount;
    }
  }

  private static final class GuardedProducerFactoryResetter
      extends RecordingProducerFactoryResetter {
    private boolean insideRecovery;

    @Override
    public <T> T resetAndRun(long observedGeneration, Supplier<T> retry) {
      reset();
      insideRecovery = true;
      try {
        return retry.get();
      } finally {
        insideRecovery = false;
      }
    }

    boolean insideRecovery() {
      return insideRecovery;
    }
  }

  private static final class ThrowingProducerFactoryResetter
      extends RecordingProducerFactoryResetter {
    private final RuntimeException failure;

    private ThrowingProducerFactoryResetter(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public <T> T resetAndRun(long observedGeneration, Supplier<T> retry) {
      reset();
      throw failure;
    }
  }
}
