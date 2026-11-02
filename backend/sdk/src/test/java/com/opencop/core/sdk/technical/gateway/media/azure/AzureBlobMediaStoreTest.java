// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.media.azure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AzureBlobMediaStoreTest {

  private static final String CONTAINER_NAME = "opencop-media";
  private static final String BLOB_KEY = "media/2026/03/23/file.png";
  private static final int SAS_EXPIRY_MINUTES = 60;
  private static final String INTERNAL_ENDPOINT = "http://azurite:10000/devstoreaccount1";
  private static final String PUBLIC_ENDPOINT = "http://localhost:10000/devstoreaccount1";
  private static final String BLOB_URL = INTERNAL_ENDPOINT + "/" + CONTAINER_NAME + "/" + BLOB_KEY;
  private static final String SAS_TOKEN = "sig=abc123";

  private BlobServiceClient blobServiceClient;
  private BlobContainerClient containerClient;
  private com.azure.storage.blob.BlobClient blobClient;

  @BeforeEach
  void setUp() {
    blobServiceClient = mock(BlobServiceClient.class);
    containerClient = mock(BlobContainerClient.class);
    blobClient = mock(com.azure.storage.blob.BlobClient.class);

    when(blobServiceClient.getBlobContainerClient(CONTAINER_NAME)).thenReturn(containerClient);
    when(containerClient.getBlobClient(BLOB_KEY)).thenReturn(blobClient);
    when(blobClient.generateSas(any(BlobServiceSasSignatureValues.class))).thenReturn(SAS_TOKEN);
  }

  @Test
  void resolve_publicEndpointBlank_returnsOriginalUrl() {
    when(blobClient.getBlobUrl()).thenReturn(BLOB_URL);

    var mediaStore =
        new AzureBlobMediaStore(
            blobServiceClient, CONTAINER_NAME, SAS_EXPIRY_MINUTES, INTERNAL_ENDPOINT, "  ");

    var resolvedUrl = mediaStore.resolve(BLOB_KEY);

    assertEquals(BLOB_URL + "?" + SAS_TOKEN, resolvedUrl);
  }

  @Test
  void resolve_matchingInternalPrefix_rewritesToPublicEndpoint() {
    when(blobClient.getBlobUrl()).thenReturn(BLOB_URL);

    var mediaStore =
        new AzureBlobMediaStore(
            blobServiceClient,
            CONTAINER_NAME,
            SAS_EXPIRY_MINUTES,
            INTERNAL_ENDPOINT,
            PUBLIC_ENDPOINT);

    var resolvedUrl = mediaStore.resolve(BLOB_KEY);

    assertEquals(
        PUBLIC_ENDPOINT + "/" + CONTAINER_NAME + "/" + BLOB_KEY + "?" + SAS_TOKEN, resolvedUrl);
  }

  @Test
  void resolve_internalPrefixMismatch_returnsOriginalUrl() {
    var nonMatchingBlobUrl =
        "http://other-host:10000/devstoreaccount1/" + CONTAINER_NAME + "/" + BLOB_KEY;
    when(blobClient.getBlobUrl()).thenReturn(nonMatchingBlobUrl);

    var mediaStore =
        new AzureBlobMediaStore(
            blobServiceClient,
            CONTAINER_NAME,
            SAS_EXPIRY_MINUTES,
            INTERNAL_ENDPOINT,
            PUBLIC_ENDPOINT);

    var resolvedUrl = mediaStore.resolve(BLOB_KEY);

    assertEquals(nonMatchingBlobUrl + "?" + SAS_TOKEN, resolvedUrl);
  }
}
