// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.media.azure;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.MediaStore;
import com.opencop.core.sdk.technical.gateway.blob.azure.PublicBlobUrlRewriter;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Azure Blob Storage implementation of {@link MediaStore}. Generates read-only SAS URLs and handles
 * media upload/delete.
 *
 * <p>Shares the same {@link BlobServiceClient} as {@link
 * com.opencop.core.sdk.technical.gateway.blob.azure.AzureBlobStorageClient} (wired in {@link
 * com.opencop.core.sdk.technical.gateway.blob.azure.AzureClientSpringConfig}), but operates on a
 * separate container ({@code opencop-media}) and produces read-only URLs with inline disposition
 * rather than server-side byte streams.
 *
 * <p>If media serving moves to a CDN, this class would be replaced by a CDN-aware adapter (e.g.,
 * returning signed CDN URLs) without affecting BlobClient or its consumers.
 */
public class AzureBlobMediaStore implements MediaStore {

  private static final Logger log = LoggerFactory.getLogger(AzureBlobMediaStore.class);

  private final BlobServiceClient blobServiceClient;
  private final String containerName;
  private final int sasExpiryMinutes;
  private final String internalEndpoint;
  private final String publicEndpoint;

  public AzureBlobMediaStore(
      BlobServiceClient blobServiceClient, String containerName, int sasExpiryMinutes) {
    this(blobServiceClient, containerName, sasExpiryMinutes, null, null);
  }

  public AzureBlobMediaStore(
      BlobServiceClient blobServiceClient,
      String containerName,
      int sasExpiryMinutes,
      String internalEndpoint,
      String publicEndpoint) {
    this.blobServiceClient = blobServiceClient;
    this.containerName = containerName;
    this.sasExpiryMinutes = sasExpiryMinutes;
    this.internalEndpoint = internalEndpoint;
    this.publicEndpoint = publicEndpoint;

    BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
    // Guards against TOCTOU when there are multiple replicas. See detailed comment in
    // AzureBlobStorageClient
    containerClient.createIfNotExists();
    log.info(
        "Media store initialized for container '{}' (SAS expiry: {} min)",
        containerName,
        sasExpiryMinutes);
  }

  @Override
  public String resolve(String blobKey) {
    var blobClient = blobServiceClient.getBlobContainerClient(containerName).getBlobClient(blobKey);

    var permissions = new BlobSasPermission().setReadPermission(true);
    var expiryTime = OffsetDateTime.now().plusMinutes(sasExpiryMinutes);
    var values =
        new BlobServiceSasSignatureValues(expiryTime, permissions).setContentDisposition("inline");

    var sasToken = blobClient.generateSas(values);
    var blobUrl =
        PublicBlobUrlRewriter.rewrite(blobClient.getBlobUrl(), internalEndpoint, publicEndpoint);
    return blobUrl + "?" + sasToken;
  }

  @Override
  public void upload(
      String blobKey, InputStream data, long size, String contentType, UUID mediaId) {
    var blobClient = blobServiceClient.getBlobContainerClient(containerName).getBlobClient(blobKey);

    var headers = new BlobHttpHeaders().setContentType(contentType);
    var options =
        new BlobParallelUploadOptions(data, size)
            .setHeaders(headers)
            .setMetadata(Map.of("media_id", mediaId.toString()));

    blobClient.uploadWithResponse(options, null, null);
    log.info("Uploaded media blob: {}", blobKey);
  }

  @Override
  public byte[] download(String blobKey) {
    var blobClient = blobServiceClient.getBlobContainerClient(containerName).getBlobClient(blobKey);
    try {
      return blobClient.downloadContent().toBytes();
    } catch (BlobStorageException e) {
      if (e.getStatusCode() == 404) {
        throw new GlobalOpenCopException("Blob not found: " + blobKey, HttpStatus.NOT_FOUND);
      }
      throw e;
    }
  }

  @Override
  public void delete(String blobKey) {
    try {
      blobServiceClient
          .getBlobContainerClient(containerName)
          .getBlobClient(blobKey)
          .deleteIfExists();
      log.info("Deleted media blob: {}", blobKey);
    } catch (Exception e) {
      log.warn("Failed to delete media blob {}: {}", blobKey, e.getMessage());
    }
  }
}
