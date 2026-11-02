// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.blob.azure;

import static com.opencop.core.sdk.ports.gateway.blob.CloudStorageExceptionCodes.BLOB_DOES_NOT_EXIST;
import static com.opencop.core.sdk.ports.gateway.blob.CloudStorageExceptionCodes.FILE_MOVE_FAILED;

import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobCopyInfo;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.ports.gateway.blob.CloudStorageException;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureBlobStorageClient implements BlobClient {

  private static final Logger log = LoggerFactory.getLogger(AzureBlobStorageClient.class);
  private final String containerName;
  private final BlobServiceClient client;

  AzureBlobStorageClient(final String containerName, final BlobServiceClient blobServiceClient) {
    this.containerName = containerName;
    this.client = blobServiceClient;

    BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
    // `createIfNotExists()`, instead of `.create()`, fixed a check-then-act race condition that
    // made connect fail to start. There's enough latency between the "check"
    // (getBlobContainerClient) and the "act" (create) that the act became invalid. This is a good
    // example of why we need to be very careful in the multi-threaded connect code because invalid
    // "act" operations will not always complain loudly and sometimes only become visible under
    // load.
    containerClient.createIfNotExists();
  }

  @Override
  public void upload(String path, InputStream content) {
    BlobContainerClient containerClient = client.getBlobContainerClient(containerName);
    com.azure.storage.blob.BlobClient blobClient = containerClient.getBlobClient(path);

    blobClient.upload(content, true); // Overwrites existing blob
  }

  @Override
  public InputStream download(String path) {
    com.azure.storage.blob.BlobClient blobClient =
        client.getBlobContainerClient(containerName).getBlobClient(path);
    return blobClient.openInputStream();
  }

  @Override
  public void move(String sourcePath, String targetPath) throws CloudStorageException {
    com.azure.storage.blob.BlobClient sourceClient =
        client.getBlobContainerClient(containerName).getBlobClient(sourcePath);
    com.azure.storage.blob.BlobClient targetClient =
        client.getBlobContainerClient(containerName).getBlobClient(targetPath);
    if (Boolean.FALSE.equals(sourceClient.exists())) {
      log.error("File does not exists at: {}", sourcePath);
      throw new CloudStorageException(BLOB_DOES_NOT_EXIST);
    }
    SyncPoller<BlobCopyInfo, Void> syncPoller =
        targetClient.beginCopy(sourceClient.getBlobUrl(), null);

    PollResponse<?> response =
        syncPoller.waitUntil(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED);

    if (response.getStatus().isComplete()
        && LongRunningOperationStatus.SUCCESSFULLY_COMPLETED.equals(response.getStatus())) {
      sourceClient.delete();
      return;
    }
    log.error("Failed to move file: {}", response.getStatus());
    throw new CloudStorageException(FILE_MOVE_FAILED);
  }

  @Override
  public void delete(String path) {
    client.getBlobContainerClient(containerName).getBlobClient(path).deleteIfExists();
  }

  @Override
  public void deleteFolder(String folderPrefix) {
    BlobContainerClient containerClient = client.getBlobContainerClient(containerName);
    for (BlobItem blobItem :
        containerClient.listBlobs(new ListBlobsOptions().setPrefix(folderPrefix), null)) {
      containerClient.getBlobClient(blobItem.getName()).deleteIfExists();
    }
  }

  @Override
  public boolean exists(String path) {
    return client.getBlobContainerClient(containerName).getBlobClient(path).exists();
  }

  @Override
  public List<String> listFolderContent(String basePath) {
    var it =
        client
            .getBlobContainerClient(containerName)
            .listBlobsByHierarchy(basePath.endsWith("/") ? basePath : basePath + "/");
    return it.stream().map(BlobItem::getName).toList();
  }
}
