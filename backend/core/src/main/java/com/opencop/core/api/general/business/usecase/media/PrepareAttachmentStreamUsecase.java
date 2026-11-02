// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import com.opencop.core.api.event.business.policy.EventAttachmentPolicy;
import com.opencop.core.api.general.ports.gateway.media.MediaStreamGateway;
import com.opencop.core.api.general.ports.usecase.media.PrepareAttachmentStream;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PrepareAttachmentStreamUsecase implements PrepareAttachmentStream {

  private static final HttpStatus UNPROCESSABLE_ENTITY = HttpStatus.valueOf(422);

  private final EventAttachmentPolicy attachmentPolicy;
  private final List<StreamableAttachmentResolver> streamableAttachmentResolvers;
  private final MediaStreamGateway mediaStreamGateway;

  public PrepareAttachmentStreamUsecase(
      EventAttachmentPolicy attachmentPolicy,
      List<StreamableAttachmentResolver> streamableAttachmentResolvers,
      MediaStreamGateway mediaStreamGateway) {
    this.attachmentPolicy = attachmentPolicy;
    this.streamableAttachmentResolvers = List.copyOf(streamableAttachmentResolvers);
    this.mediaStreamGateway = mediaStreamGateway;
  }

  @Override
  public Optional<String> prepare(
      OpenCopPrincipal authentication, UUID sourceId, String objectId, UUID attachmentId) {

    attachmentPolicy.assertCanGet(authentication, sourceId, objectId, attachmentId);

    var streamableAttachment =
        resolveStreamableAttachment(attachmentId)
            .orElseThrow(
                () ->
                    new GlobalOpenCopException(
                        "Attachment is not a convertible stream", UNPROCESSABLE_ENTITY));

    return mediaStreamGateway.prepareStream(
        streamableAttachment.pathName(), streamableAttachment.sourceUrl());
  }

  private Optional<StreamableAttachment> resolveStreamableAttachment(UUID attachmentId) {
    return streamableAttachmentResolvers.stream()
        .map(resolver -> resolver.resolve(attachmentId))
        .flatMap(Optional::stream)
        .findFirst();
  }
}
