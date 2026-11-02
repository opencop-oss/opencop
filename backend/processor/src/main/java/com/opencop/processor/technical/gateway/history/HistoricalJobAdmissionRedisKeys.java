// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import java.util.List;
import java.util.UUID;

final class HistoricalJobAdmissionRedisKeys {

  private HistoricalJobAdmissionRedisKeys() {}

  static String activeRunningScanPattern() {
    return activeRunningScanPattern(HistoricalJobControlKeyLayout.STANDALONE);
  }

  static String activeRunningScanPattern(HistoricalJobControlKeyLayout layout) {
    return layout.activeRunningScanPattern();
  }

  static List<String> registerScriptKeys(UUID userId, UUID jobId) {
    return registerScriptKeys(HistoricalJobControlKeyLayout.STANDALONE, userId, jobId);
  }

  static List<String> registerScriptKeys(
      HistoricalJobControlKeyLayout layout, UUID userId, UUID jobId) {
    var payload = layout.queuedPayload(userId, jobId);
    return List.of(
        layout.activeRequestKey(userId),
        layout.activeRunningKey(userId),
        layout.activeBacklogKey(userId),
        layout.replacementRequestKey(userId),
        layout.replacementJobsKey(userId),
        layout.jobMeta(jobId),
        payload.redisKey());
  }

  static List<String> recordOutcomeScriptKeys(UUID userId, UUID jobId) {
    return recordOutcomeScriptKeys(HistoricalJobControlKeyLayout.STANDALONE, userId, jobId);
  }

  static List<String> recordOutcomeScriptKeys(
      HistoricalJobControlKeyLayout layout, UUID userId, UUID jobId) {
    return List.of(
        layout.activeRequestKey(userId),
        layout.activeRunningKey(userId),
        layout.activeBacklogKey(userId),
        layout.replacementRequestKey(userId),
        layout.replacementJobsKey(userId),
        layout.jobMeta(jobId));
  }

  static List<String> cleanupScriptKeys(UUID userId, String jobId) {
    return cleanupScriptKeys(HistoricalJobControlKeyLayout.STANDALONE, userId, jobId);
  }

  static List<String> cleanupScriptKeys(
      HistoricalJobControlKeyLayout layout, UUID userId, String jobId) {
    return List.of(
        layout.activeRequestKey(userId),
        layout.activeRunningKey(userId),
        layout.activeBacklogKey(userId),
        layout.replacementRequestKey(userId),
        layout.replacementJobsKey(userId),
        layout.queuedPayloadKey(userId, jobId),
        layout.jobMeta(jobId));
  }

  static String activeRequestKey(UUID userId) {
    return HistoricalJobControlKeyLayout.STANDALONE.activeRequestKey(userId);
  }

  static String activeRunningKey(UUID userId) {
    return HistoricalJobControlKeyLayout.STANDALONE.activeRunningKey(userId);
  }

  static String activeBacklogKey(UUID userId) {
    return HistoricalJobControlKeyLayout.STANDALONE.activeBacklogKey(userId);
  }

  static String replacementRequestKey(UUID userId) {
    return HistoricalJobControlKeyLayout.STANDALONE.replacementRequestKey(userId);
  }

  static String replacementJobsKey(UUID userId) {
    return HistoricalJobControlKeyLayout.STANDALONE.replacementJobsKey(userId);
  }

  static String jobMetaKey(UUID jobId) {
    return HistoricalJobControlKeyLayout.STANDALONE.jobMeta(jobId);
  }

  static String jobMetaKey(String jobId) {
    return HistoricalJobControlKeyLayout.STANDALONE.jobMeta(jobId);
  }
}
