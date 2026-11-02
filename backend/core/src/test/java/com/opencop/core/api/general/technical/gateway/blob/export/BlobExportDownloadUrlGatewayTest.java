// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.blob.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.opencop.core.sdk.technical.gateway.blob.azure.AzureProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

class BlobExportDownloadUrlGatewayTest {

  private static final String CONTAINER_NAME = "opencop-common";
  private static final String BLOB_PATH = "exports/test.csv";
  private static final String INTERNAL_ENDPOINT = "http://azurite:10000/devstoreaccount1";
  private static final String PUBLIC_ENDPOINT = "http://localhost:10000/devstoreaccount1";
  private static final String BLOB_URL = INTERNAL_ENDPOINT + "/" + CONTAINER_NAME + "/" + BLOB_PATH;
  private static final String SAS_TOKEN = "sig=abc";
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-08T12:00:00Z"), ZoneOffset.UTC);

  private BlobServiceClient blobServiceClient;
  private BlobContainerClient containerClient;
  private com.azure.storage.blob.BlobClient blobClient;
  private BlobExportDownloadUrlGateway gateway;

  @BeforeEach
  void setUp() {
    blobServiceClient = mock(BlobServiceClient.class);
    containerClient = mock(BlobContainerClient.class);
    blobClient = mock(com.azure.storage.blob.BlobClient.class);

    when(blobServiceClient.getBlobContainerClient(CONTAINER_NAME)).thenReturn(containerClient);
    when(containerClient.getBlobClient(BLOB_PATH)).thenReturn(blobClient);
    when(blobClient.generateSas(any(BlobServiceSasSignatureValues.class))).thenReturn(SAS_TOKEN);
    when(blobClient.getBlobUrl()).thenReturn(BLOB_URL);

    gateway = new BlobExportDownloadUrlGateway(blobServiceClient, azureProperties(), CLOCK);
  }

  @Test
  void createSignedUrl_generatesReadOnlyAttachmentSasForStoredBlobPath() {
    var artifactExpiresAt = OffsetDateTime.parse("2026-06-08T13:00:00Z");

    var result =
        gateway.createSignedUrl(
            BLOB_PATH, "layer-export.csv", new MediaType("text", "csv"), artifactExpiresAt);

    assertEquals(
        PUBLIC_ENDPOINT + "/" + CONTAINER_NAME + "/" + BLOB_PATH + "?" + SAS_TOKEN, result);
    verify(containerClient).getBlobClient(BLOB_PATH);

    var valuesCaptor = ArgumentCaptor.forClass(BlobServiceSasSignatureValues.class);
    verify(blobClient).generateSas(valuesCaptor.capture());
    var values = valuesCaptor.getValue();
    assertEquals("r", values.getPermissions());
    assertEquals("attachment; filename=\"layer-export.csv\"", values.getContentDisposition());
    assertEquals("text/csv", values.getContentType());
    assertEquals("2026-06-08T12:01Z", values.getExpiryTime().toString());
  }

  @Test
  void createSignedUrl_whenArtifactExpiresSoonerThanTtl_capsSasExpiryToArtifactExpiry() {
    var artifactExpiresAt = OffsetDateTime.parse("2026-06-08T12:00:30Z");

    gateway.createSignedUrl(
        BLOB_PATH, "layer-export.csv", new MediaType("text", "csv"), artifactExpiresAt);

    var valuesCaptor = ArgumentCaptor.forClass(BlobServiceSasSignatureValues.class);
    verify(blobClient).generateSas(valuesCaptor.capture());
    assertEquals(artifactExpiresAt, valuesCaptor.getValue().getExpiryTime());
  }

  private static AzureProperties azureProperties() {
    var properties = new AzureProperties();
    properties.setContainerName(CONTAINER_NAME);
    properties.setEndpoint(INTERNAL_ENDPOINT);
    properties.setPublicEndpoint(PUBLIC_ENDPOINT);
    return properties;
  }
}
