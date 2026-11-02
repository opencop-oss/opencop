// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterPayloadStore {

  private static final String HASH_ALGORITHM = "SHA-256";
  private static final String BLOB_PREFIX = "dead-letter-payloads";
  private static final String PAYLOAD_FILE_NAME = "payload.bin";
  private static final HexFormat HEX = HexFormat.of();

  private final BlobClient blobClient;
  private final DeadLetterObservability observability;

  public DeadLetterPayloadStore(BlobClient blobClient, DeadLetterObservability observability) {
    this.blobClient = blobClient;
    this.observability = observability;
  }

  public StoredPayload store(UUID failureId, String topic, byte[] payload) {
    return store(failureId, topic, payload, topic);
  }

  public StoredPayload store(
      UUID failureId, String topic, byte[] payload, String topicOrSourceClass) {
    byte[] safePayload = payload == null ? new byte[0] : payload;
    String topicHash = sha256(topic == null ? new byte[0] : topic.getBytes(StandardCharsets.UTF_8));
    String payloadRef =
        String.join("/", BLOB_PREFIX, failureId.toString(), topicHash, PAYLOAD_FILE_NAME);
    var startedAt = Instant.now();
    try {
      blobClient.upload(payloadRef, new ByteArrayInputStream(safePayload));
    } catch (RuntimeException ex) {
      observability.recordBlobUploadFailure(
          topicOrSourceClass, Duration.between(startedAt, Instant.now()));
      throw ex;
    }
    observability.recordBlobUploadSuccess(
        topicOrSourceClass, safePayload.length, Duration.between(startedAt, Instant.now()));
    return new StoredPayload(payloadRef, sha256(safePayload), safePayload.length);
  }

  private static String sha256(byte[] value) {
    try {
      return HEX.formatHex(MessageDigest.getInstance(HASH_ALGORITHM).digest(value));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(HASH_ALGORITHM + " is not available", ex);
    }
  }

  public record StoredPayload(String payloadRef, String sha256, long sizeBytes) {}
}
