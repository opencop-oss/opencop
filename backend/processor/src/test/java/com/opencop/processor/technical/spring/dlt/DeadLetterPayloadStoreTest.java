// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeadLetterPayloadStoreTest {

  private static final byte[] PAYLOAD = "payload-body".getBytes(StandardCharsets.UTF_8);

  @Test
  void storeUploadsPayloadUnderFailureIdAndTopicHashWithoutRawTopicSeparators() throws Exception {
    var blobClient = mock(BlobClient.class);
    var payloadStore =
        new DeadLetterPayloadStore(
            blobClient, new DeadLetterObservability(ProcessorMetrics.noop()));
    var failureId = UUID.fromString("550e8400-e29b-41d4-a716-446655440201");
    var hostileTopic = "../opencop/../../tenant.topic.dlt";

    var storedPayload = payloadStore.store(failureId, hostileTopic, PAYLOAD);

    assertThat(storedPayload.payloadRef()).contains(failureId.toString());
    assertThat(storedPayload.payloadRef())
        .contains(sha256(hostileTopic.getBytes(StandardCharsets.UTF_8)));
    assertThat(storedPayload.payloadRef()).doesNotContain("..");
    assertThat(storedPayload.payloadRef()).doesNotContain(hostileTopic);
    assertThat(storedPayload.sha256()).isEqualTo(sha256(PAYLOAD));
    assertThat(storedPayload.sizeBytes()).isEqualTo(PAYLOAD.length);
    var contentCaptor = ArgumentCaptor.forClass(InputStream.class);
    verify(blobClient).upload(eq(storedPayload.payloadRef()), contentCaptor.capture());
    assertThat(contentCaptor.getValue().readAllBytes()).isEqualTo(PAYLOAD);
    verify(blobClient).upload(any(String.class), any(InputStream.class));
  }

  @Test
  void storeRecordsBlobFailureAndCauseMetricWhenUploadFails() {
    var blobClient = mock(BlobClient.class);
    doThrow(new IllegalStateException("blob unavailable"))
        .when(blobClient)
        .upload(any(String.class), any(InputStream.class));
    var registry = new SimpleMeterRegistry();
    var payloadStore =
        new DeadLetterPayloadStore(
            blobClient, new DeadLetterObservability(new ProcessorMetrics(registry)));

    assertThatThrownBy(
            () ->
                payloadStore.store(
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440202"),
                    "opencop.geo.validated.dlt",
                    PAYLOAD,
                    "opencop.geo.validated.dlt"))
        .isInstanceOf(IllegalStateException.class);

    assertThat(
            registry
                .counter(
                    DeadLetterObservability.BLOB_UPLOAD_FAILURES_METRIC,
                    DeadLetterObservability.TAG_TOPIC_OR_SOURCE_CLASS,
                    "opencop.geo.validated.dlt")
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .counter(
                    DeadLetterObservability.FAILURES_METRIC,
                    DeadLetterObservability.TAG_TOPIC_OR_SOURCE_CLASS,
                    "opencop.geo.validated.dlt",
                    DeadLetterObservability.TAG_CAUSE,
                    "blob")
                .count())
        .isEqualTo(1.0);
  }

  private static String sha256(byte[] value) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
  }
}
