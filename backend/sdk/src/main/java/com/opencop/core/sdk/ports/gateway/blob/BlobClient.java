// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.blob;

import java.io.InputStream;
import java.util.List;

public interface BlobClient {

  void upload(String path, InputStream content);

  InputStream download(String path);

  void move(String sourcePath, String targetPath) throws CloudStorageException;

  void delete(String path);

  void deleteFolder(String folderPrefix);

  boolean exists(String path);

  List<String> listFolderContent(String basePath);
}
