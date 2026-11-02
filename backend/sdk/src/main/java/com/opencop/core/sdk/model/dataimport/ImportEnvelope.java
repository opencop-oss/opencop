// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.dataimport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Kafka message body for blob-referenced layer imports.
 *
 * <p>Core serializes this as JSON bytes into the Kafka message body. The Processor deserializes it
 * and downloads the file from blob storage using {@code blobKey}.
 *
 * <p>The {@code filename}, {@code contentType}, and {@code sizeBytes} fields are included for
 * debugging and observability — the Processor's decoder does not strictly need them.
 *
 * @param blobKey the blob storage key where the import file was uploaded
 * @param filename the original filename (nullable, debugging aid)
 * @param contentType the MIME content type (nullable, debugging aid)
 * @param sizeBytes the file size in bytes (nullable, debugging aid)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportEnvelope(String blobKey, String filename, String contentType, Long sizeBytes) {

  /** Value for the {@code opencop-content-kind} Kafka header that identifies this envelope type. */
  public static final String CONTENT_KIND = "import-envelope";
}
