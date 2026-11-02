// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.media;

import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import java.io.InputStream;
import java.util.UUID;

public interface UploadMedia {

  MediaRecord upload(
      UUID userId,
      String originalFilename,
      String mimeType,
      long size,
      MediaAccess access,
      InputStream data,
      UUID thumbnailOf,
      MediaUsage usage);
}
