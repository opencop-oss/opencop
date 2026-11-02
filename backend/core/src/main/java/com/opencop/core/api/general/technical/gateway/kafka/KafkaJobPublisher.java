// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.kafka;

import com.opencop.core.api.general.ports.gateway.job.JobPublisher;
import com.opencop.core.sdk.model.job.JobSubmission;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@Component
public class KafkaJobPublisher implements JobPublisher {

  private static final Logger log = LoggerFactory.getLogger(KafkaJobPublisher.class);
  private static final Duration ACK_TIMEOUT = Duration.ofSeconds(10);

  private final KafkaTemplate<String, byte[]> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final List<KafkaJobPublishStrategy> publishStrategies;
  private final JobPublishLogSanitizer logSanitizer;

  public KafkaJobPublisher(
      KafkaTemplate<String, byte[]> kafkaTemplate,
      ObjectMapper objectMapper,
      List<KafkaJobPublishStrategy> publishStrategies,
      JobPublishLogSanitizer logSanitizer) {
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
    this.publishStrategies = List.copyOf(publishStrategies);
    this.logSanitizer = logSanitizer;
  }

  @Override
  public void publishNow(JobSubmission submission) {
    publishAndWait(submission);
  }

  @Override
  public void scheduleBatchAfterCommit(
      List<? extends JobSubmission> submissions, Runnable afterSuccessfulBatch) {
    scheduleBatchAfterCommit(submissions, afterSuccessfulBatch, _ -> {});
  }

  @Override
  public void scheduleBatchAfterCommit(
      List<? extends JobSubmission> submissions,
      Runnable afterSuccessfulBatch,
      Consumer<Exception> afterFailedPublish) {
    Context registrationContext = Context.current();
    log.info(
        "Registering {} job submissions to publish after transaction commit", submissions.size());
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            log.info("Transaction committed; publishing {} job submissions", submissions.size());
            try (Scope ignored = registrationContext.makeCurrent()) {
              int publishedCount = 0;
              try {
                for (JobSubmission submission : submissions) {
                  publishAndWait(submission);
                  publishedCount++;
                }
              } catch (Exception e) {
                log.error(
                    "Failed to publish after-commit job batch. publishedCount={} totalCount={}. Manual backfill required",
                    publishedCount,
                    submissions.size(),
                    e);
                try {
                  afterFailedPublish.accept(e);
                } catch (Exception callbackFailure) {
                  log.error(
                      "Post-publish failure callback failed. publishedCount={} totalCount={}",
                      publishedCount,
                      submissions.size(),
                      callbackFailure);
                }
                return;
              }
              try {
                afterSuccessfulBatch.run();
              } catch (Exception e) {
                log.error(
                    "Post-publish cancellation callback failed. publishedCount={} totalCount={}",
                    publishedCount,
                    submissions.size(),
                    e);
              }
            }
          }
        });
  }

  private void publishAndWait(JobSubmission submission) {
    try {
      byte[] payload = objectMapper.writeValueAsBytes(submission);
      String safeKey = logSanitizer.safeKey(submission.key());
      log.info(
          "Publishing job to topic {} with key {} and {} bytes",
          submission.topic(),
          safeKey,
          payload.length);
      publishWithStrategyOrDefault(submission, payload);
      log.info("Published job to topic {} with key {}", submission.topic(), safeKey);
    } catch (Exception e) {
      throw sanitizedPublishException(submission.topic(), submission.key(), e);
    }
  }

  private void publishWithStrategyOrDefault(JobSubmission submission, byte[] payload)
      throws Exception {
    for (KafkaJobPublishStrategy strategy : publishStrategies) {
      if (strategy.supports(submission)) {
        strategy.publish(submission, payload);
        return;
      }
    }

    kafkaTemplate
        .send(submission.topic(), submission.key(), payload)
        .get(ACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
  }

  private IllegalStateException sanitizedPublishException(
      String topic, String key, Exception originalException) {
    String safeMessage = logSanitizer.safePublishFailureMessage(topic, key);
    log.error("{} failureType={}", safeMessage, originalException.getClass().getSimpleName());
    return new IllegalStateException(safeMessage);
  }
}
