// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.media;

/**
 * Abstracts media storage operations: upload, resolve (generate client-accessible URLs), and
 * delete.
 *
 * <p>This overlaps with {@link com.opencop.core.sdk.ports.gateway.blob.BlobClient} in that both
 * interact with blob storage, but the contracts serve different consumers:
 *
 * <ul>
 *   <li>{@code BlobClient} — server-side CRUD (upload/download/delete bytes). Consumers are backend
 *       services that read and write blobs directly.
 *   <li>{@code MediaStore} — produces URLs a <em>browser</em> can fetch, and handles media-specific
 *       upload/delete. Read-only URLs use inline disposition and are time-limited. Consumers are
 *       endpoints that return URLs to frontends and usecases that manage media lifecycle.
 * </ul>
 *
 * <p>Keeping these separate allows the storage/delivery strategy to change independently — e.g.,
 * swapping Azure SAS URLs for CDN-signed URLs requires a new {@code MediaStore} adapter without
 * touching {@code BlobClient} or its consumers.
 *
 * <p>Currently consumed only by Core (media endpoints). Placed in SDK for consistency with the
 * BlobClient port/adapter pattern.
 */
public interface MediaStore {

  /**
   * Generates a read-only pre-signed URL for the given blob key.
   *
   * @param blobKey the full blob storage path (e.g., "media/2026/03/12/{uuid}/icon.png")
   * @return a pre-signed URL string with time-limited read access
   */
  String resolve(String blobKey);

  /**
   * Uploads a file to blob storage in the media container.
   *
   * @param blobKey the full blob storage path
   * @param data the file content
   * @param size the file size in bytes
   * @param contentType the MIME type (e.g., "image/png")
   * @param mediaId the media record UUID (stored as blob metadata)
   */
  void upload(
      String blobKey,
      java.io.InputStream data,
      long size,
      String contentType,
      java.util.UUID mediaId);

  /**
   * Downloads the raw bytes of a blob from storage.
   *
   * @param blobKey the full blob storage path
   * @return the blob content as a byte array
   */
  byte[] download(String blobKey);

  /**
   * Deletes a blob from storage. Logs failures but does not throw.
   *
   * @param blobKey the full blob storage path
   */
  void delete(String blobKey);
}
