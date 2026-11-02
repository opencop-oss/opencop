// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.business.usecase.attachment;

import com.opencop.core.sdk.model.media.StreamKind;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public record CreateEventAttachmentCommand(
    OpenCopPrincipal authentication, UUID sourceId, String objectId, Payload payload) {

  public UUID userId() {
    return authentication.internalUserId();
  }

  public sealed interface Payload permits FilePayload, UrlPayload {}

  public record FilePayload(EventAttachmentFile original, List<EventAttachmentFile> thumbnails)
      implements Payload {

    public FilePayload {
      thumbnails = List.copyOf(thumbnails);
    }
  }

  /**
   * @param streamKind the transport claimed by the client; {@code null} asks the server to infer
   */
  public record UrlPayload(String url, StreamKind streamKind) implements Payload {}

  public record EventAttachmentFile(
      String originalFilename, String contentType, long size, InputStream data) {}
}
