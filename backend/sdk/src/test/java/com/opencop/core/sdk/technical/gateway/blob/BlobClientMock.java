// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.blob;

import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import java.io.InputStream;
import java.util.List;

public class BlobClientMock implements BlobClient {

  @Override
  public void upload(String path, InputStream content) {}

  @Override
  public InputStream download(String path) {
    return null;
  }

  @Override
  public void move(String sourcePath, String targetPath) {}

  @Override
  public void delete(String path) {}

  @Override
  public void deleteFolder(String folderPrefix) {}

  @Override
  public boolean exists(String path) {
    return false;
  }

  @Override
  public List<String> listFolderContent(String basePath) {
    return List.of();
  }
}
