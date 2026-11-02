// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.policy;

import com.opencop.core.api.general.technical.gateway.authorization.AuthorizationService;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class EventAttachmentPolicy {

  private static final String UPLOAD_ATTACHMENT_OPERATION = "upload attachment";
  private static final String DELETE_ATTACHMENT_OPERATION = "delete attachment";

  private final EventAuthorizationPolicy eventAuth;
  private final EventAttachmentRepository eventAttachmentRepository;
  private final AuthorizationService authz;
  private final int maxAttachmentsPerEvent;

  public EventAttachmentPolicy(
      EventAuthorizationPolicy eventAuth,
      EventAttachmentRepository eventAttachmentRepository,
      AuthorizationService authz,
      @Value("${opencop.media.max-attachments-per-event:20}") int maxAttachmentsPerEvent) {
    this.eventAuth = eventAuth;
    this.eventAttachmentRepository = eventAttachmentRepository;
    this.authz = authz;
    this.maxAttachmentsPerEvent = maxAttachmentsPerEvent;
  }

  public void assertCanCreate(OpenCopPrincipal authentication, UUID sourceId, String objectId) {
    eventAuth.assertEditorForActiveResource(
        authentication, sourceId, objectId, UPLOAD_ATTACHMENT_OPERATION);
    if (eventAttachmentRepository.countAttachmentsByObjectId(sourceId, objectId)
        >= maxAttachmentsPerEvent) {
      throw new GlobalOpenCopException(
          "Event has reached the maximum of " + maxAttachmentsPerEvent + " attachments",
          HttpStatus.BAD_REQUEST);
    }
  }

  public void assertCanDelete(
      OpenCopPrincipal authentication, UUID sourceId, String objectId, UUID attachmentId) {
    eventAuth.assertEditorForActiveResource(
        authentication, sourceId, objectId, DELETE_ATTACHMENT_OPERATION);
    if (!eventAttachmentRepository.exists(sourceId, objectId, attachmentId)) {
      throw new GlobalOpenCopException("Attachment not found", HttpStatus.NOT_FOUND);
    }
  }

  public void assertCanList(OpenCopPrincipal authentication, UUID sourceId, String objectId) {
    eventAuth.assertMember(authentication, sourceId, objectId);
  }

  public void assertCanGet(
      OpenCopPrincipal authentication, UUID sourceId, String objectId, UUID attachmentId) {
    eventAuth.assertMember(authentication, sourceId, objectId);
    if (!eventAttachmentRepository.exists(sourceId, objectId, attachmentId)) {
      throw new GlobalOpenCopException("Attachment not found", HttpStatus.NOT_FOUND);
    }
  }

  public boolean canGet(
      OpenCopPrincipal authentication, UUID sourceId, String objectId, UUID attachmentId) {
    try {
      return eventAuth.canMember(authentication, sourceId, objectId)
          && eventAttachmentRepository.exists(sourceId, objectId, attachmentId);
    } catch (GlobalOpenCopException _) {
      return false;
    }
  }

  public void assertOwnerOrAdmin(OpenCopPrincipal authentication, UUID ownerId) {
    if (authz.isAdmin(authentication)) return;
    if (ownerId == null || !ownerId.equals(authentication.internalUserId())) {
      throw new GlobalOpenCopException(
          "Only the attachment owner or an admin can delete this attachment", HttpStatus.FORBIDDEN);
    }
  }
}
