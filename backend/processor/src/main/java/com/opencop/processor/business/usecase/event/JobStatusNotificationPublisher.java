// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import com.opencop.core.sdk.model.job.JobIssuerType;
import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.core.sdk.model.notification.NotificationSeverityType;
import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.model.notification.content.JobStatusNotification;
import com.opencop.core.sdk.model.notification.content.JobStatusType;
import com.opencop.core.sdk.model.notification.content.NotificationStatus;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

public class JobStatusNotificationPublisher {

  private static final Logger log = LoggerFactory.getLogger(JobStatusNotificationPublisher.class);

  private final MessageBrokerProducer<byte[]> notificationProducer;
  private final ObjectMapper objectMapper;

  public JobStatusNotificationPublisher(
      MessageBrokerProducer<byte[]> notificationProducer, ObjectMapper objectMapper) {
    this.notificationProducer = notificationProducer;
    this.objectMapper = objectMapper;
  }

  public void notifySuccess(JobSubmission job) {
    publish(job, JobStatusType.SUCCEEDED, NotificationSeverityType.SUCCESS, null);
  }

  public void notifyFailure(JobSubmission job, Throwable exception) {
    publish(job, JobStatusType.FAILED, NotificationSeverityType.ERROR, exception.getMessage());
  }

  private void publish(
      JobSubmission job, JobStatusType status, NotificationSeverityType severity, String detail) {
    if (job.issuer().type() != JobIssuerType.USER) {
      return;
    }

    try {
      UUID userId = UUID.fromString(job.issuer().id());
      var notification =
          new OpencopNotification(
              UUID.randomUUID(),
              job.jobId(),
              userId,
              userId,
              severity,
              new JobStatusNotification(job.jobId(), job.jobType(), status, detail),
              NotificationStatus.CREATED,
              Instant.now(),
              false,
              null);
      notificationProducer.send(objectMapper.writeValueAsBytes(notification));
    } catch (Exception e) {
      log.error(
          "Failed to publish {} notification for job {} initiated by {}",
          status,
          job.jobId(),
          job.issuer(),
          e);
    }
  }
}
