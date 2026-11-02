// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.blob.export;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.opencop.core.api.general.ports.gateway.export.ExportDownloadUrlGateway;
import com.opencop.core.sdk.technical.gateway.blob.azure.AzureProperties;
import com.opencop.core.sdk.technical.gateway.blob.azure.PublicBlobUrlRewriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class BlobExportDownloadUrlGateway implements ExportDownloadUrlGateway {

  private static final long SIGNED_URL_TTL_MINUTES = 1;

  private final BlobServiceClient blobServiceClient;
  private final AzureProperties azureProperties;
  private final Clock clock;

  public BlobExportDownloadUrlGateway(
      BlobServiceClient blobServiceClient, AzureProperties azureProperties, Clock clock) {
    this.blobServiceClient = blobServiceClient;
    this.azureProperties = azureProperties;
    this.clock = clock;
  }

  @Override
  public String createSignedUrl(
      String blobPath, String filename, MediaType contentType, OffsetDateTime maxExpiry) {
    var blobClient =
        blobServiceClient
            .getBlobContainerClient(azureProperties.getContainerName())
            .getBlobClient(blobPath);
    var ttlExpiry = OffsetDateTime.now(clock).plusMinutes(SIGNED_URL_TTL_MINUTES);
    var sasExpiry = ttlExpiry.isBefore(maxExpiry) ? ttlExpiry : maxExpiry;
    var values =
        new BlobServiceSasSignatureValues(
                sasExpiry, new BlobSasPermission().setReadPermission(true))
            .setContentDisposition(
                ContentDisposition.attachment().filename(filename).build().toString())
            .setContentType(contentType.toString());
    var sasToken = blobClient.generateSas(values);
    var blobUrl =
        PublicBlobUrlRewriter.rewrite(
            blobClient.getBlobUrl(),
            azureProperties.getEndpoint(),
            azureProperties.getPublicEndpoint());
    return blobUrl + "?" + sasToken;
  }
}
