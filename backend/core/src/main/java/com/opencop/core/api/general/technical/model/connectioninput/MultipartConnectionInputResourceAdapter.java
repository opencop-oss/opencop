// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.connectioninput;

import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

/** Adapts one Spring multipart upload to the framework-neutral SDK resource model. */
public final class MultipartConnectionInputResourceAdapter {

  private MultipartConnectionInputResourceAdapter() {}

  public static ConnectionInputResource adapt(String role, MultipartFile multipartFile) {
    var mediaType = multipartFile.getContentType();
    if (mediaType == null || mediaType.isBlank()) {
      mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
    var filename = multipartFile.getOriginalFilename();
    if (filename == null || filename.isBlank()) {
      filename = multipartFile.getName();
    }
    return new ConnectionInputResource(
        role, filename, mediaType, multipartFile.getSize(), multipartFile::getInputStream);
  }
}
