// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.media;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.opencop.core.sdk.model.media.MediaRecord;
import com.opencop.core.sdk.model.media.MediaUrlRecord;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AttachmentView.File.class, name = "FILE"),
  @JsonSubTypes.Type(value = AttachmentView.Url.class, name = "URL")
})
public sealed interface AttachmentView permits AttachmentView.File, AttachmentView.Url {

  record File(MediaRecord record, String url, List<ThumbnailRef> thumbnails)
      implements AttachmentView {}

  record Url(MediaUrlRecord record) implements AttachmentView {}
}
