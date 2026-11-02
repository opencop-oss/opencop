// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencop.core.sdk.technical.model.history.HistoricalJobDeliveryRedisKeys;
import io.lettuce.core.cluster.SlotHash;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoricalJobControlKeyLayoutTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
  private static final UUID REQUEST_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440121");
  private static final UUID JOB_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440122");

  @Test
  void standalone_preservesEveryLegacyControlKeyAndPayloadShape() {
    var layout = HistoricalJobControlKeyLayout.STANDALONE;

    assertEquals("opencop:history:job:" + JOB_ID, layout.jobState(JOB_ID));
    assertEquals("opencop:history:job-index:user:" + USER_ID, layout.userJobIndex(USER_ID));
    assertEquals(
        "opencop:history:job-index:user:" + USER_ID + ":request:" + REQUEST_ID,
        layout.userRequestJobIndex(USER_ID, REQUEST_ID));
    assertEquals("opencop:history:request-index:user:" + USER_ID, layout.userRequestIndex(USER_ID));
    assertEquals("opencop:history:job-sequence:user:" + USER_ID, layout.userJobSequence(USER_ID));
    assertEquals(
        "opencop:history:job-sequence:user:" + USER_ID + ":request:" + REQUEST_ID,
        layout.userRequestJobSequence(USER_ID, REQUEST_ID));
    assertEquals(
        "opencop:history:admission:user:" + USER_ID + ":active-request",
        layout.activeRequestKey(USER_ID));
    assertEquals(
        "opencop:history:admission:user:" + USER_ID + ":active-running",
        layout.activeRunningKey(USER_ID));
    assertEquals(
        "opencop:history:admission:user:" + USER_ID + ":active-backlog",
        layout.activeBacklogKey(USER_ID));
    assertEquals(
        "opencop:history:admission:user:" + USER_ID + ":replacement-request",
        layout.replacementRequestKey(USER_ID));
    assertEquals(
        "opencop:history:admission:user:" + USER_ID + ":replacement-jobs",
        layout.replacementJobsKey(USER_ID));
    assertEquals(
        "opencop:history:admission:user:*:active-running", layout.activeRunningScanPattern());
    assertEquals("opencop:history:admission:job-meta:" + JOB_ID, layout.jobMeta(JOB_ID));
    assertEquals("opencop:history:admission:job-meta:raw-token", layout.jobMeta("raw-token"));

    var payload = layout.queuedPayload(USER_ID, JOB_ID);
    assertEquals(HistoricalJobControlKeyLayout.StorageKind.PER_JOB_STRING, payload.storageKind());
    assertEquals("opencop:history:job-payload:" + JOB_ID, payload.redisKey());
    assertThrows(IllegalStateException.class, payload::payloadField);
    assertThrows(IllegalStateException.class, payload::deadlineField);
  }

  @Test
  void cluster_tagsAllHistoricalControlKeysAndUsesAUserPayloadHash() {
    var layout = HistoricalJobControlKeyLayout.CLUSTER;
    var secondUserId = UUID.fromString("550e8400-e29b-41d4-a716-446655440123");
    var payload = layout.queuedPayload(USER_ID, JOB_ID);
    var keys =
        List.of(
            layout.jobState(JOB_ID),
            layout.userJobIndex(USER_ID),
            layout.userRequestJobIndex(USER_ID, REQUEST_ID),
            layout.userRequestIndex(USER_ID),
            layout.userJobSequence(USER_ID),
            layout.userRequestJobSequence(USER_ID, REQUEST_ID),
            layout.activeRequestKey(USER_ID),
            layout.activeRunningKey(USER_ID),
            layout.activeBacklogKey(USER_ID),
            layout.replacementRequestKey(USER_ID),
            layout.replacementJobsKey(USER_ID),
            layout.jobMeta(JOB_ID),
            payload.redisKey());
    var slot = SlotHash.getSlot(keys.getFirst());

    keys.forEach(key -> assertEquals(slot, SlotHash.getSlot(key), key));
    assertEquals(slot, SlotHash.getSlot(layout.userJobIndex(secondUserId)));
    assertEquals(slot, SlotHash.getSlot(layout.activeRunningKey(secondUserId)));
    assertEquals(
        "opencop:history:{history}:admission:user:*:active-running",
        layout.activeRunningScanPattern());
    assertEquals(HistoricalJobControlKeyLayout.StorageKind.USER_HASH, payload.storageKind());
    assertEquals(JOB_ID.toString(), payload.payloadField());
    assertEquals(JOB_ID + ":expiresAt", payload.deadlineField());

    var deliveryKey = HistoricalJobDeliveryRedisKeys.stateHashKey(JOB_ID);
    assertEquals(-1, deliveryKey.indexOf("{history}"));
    assertNotEquals(slot, SlotHash.getSlot(deliveryKey));
  }

  @Test
  void methods_rejectNullIdentifiersAtTheBoundary() {
    var layout = HistoricalJobControlKeyLayout.CLUSTER;

    assertThrows(NullPointerException.class, () -> layout.jobState(null));
    assertThrows(NullPointerException.class, () -> layout.userJobIndex(null));
    assertThrows(NullPointerException.class, () -> layout.userRequestJobIndex(USER_ID, null));
    assertThrows(NullPointerException.class, () -> layout.userRequestIndex(null));
    assertThrows(NullPointerException.class, () -> layout.userRequestJobSequence(null, REQUEST_ID));
    assertThrows(NullPointerException.class, () -> layout.jobMeta((String) null));
    assertThrows(NullPointerException.class, () -> layout.queuedPayload(USER_ID, null));
    assertThrows(
        NullPointerException.class, () -> layout.queuedPayloadKey(null, JOB_ID.toString()));
  }
}
