// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import com.opencop.core.api.event.business.policy.EventAuthorizationPolicy;
import com.opencop.core.api.event.ports.business.usecase.RemarkHandler;
import com.opencop.core.api.event.ports.business.usecase.RemarkService;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.idp.IdpException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.model.opencop.CreateRemarkRequest;
import com.opencop.core.sdk.technical.model.opencop.PersistRemarkRequest;
import com.opencop.core.sdk.technical.model.opencop.Remark;
import com.opencop.core.sdk.technical.model.opencop.UpdateRemarkRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RemarkUsecase implements RemarkHandler {

  private static final String ADD_REMARK_OPERATION = "add remark";
  private static final String UPDATE_REMARK_OPERATION = "update remark";
  private static final String DELETE_REMARK_OPERATION = "delete remark";

  private final RemarkService remarkService;
  private final EventAuthorizationPolicy eventAuthorizationPolicy;

  RemarkUsecase(RemarkService remarkService, EventAuthorizationPolicy eventAuthorizationPolicy) {
    this.remarkService = remarkService;
    this.eventAuthorizationPolicy = eventAuthorizationPolicy;
  }

  public void addRemark(UUID userId, UUID sourceId, String objectId, CreateRemarkRequest remark) {
    eventAuthorizationPolicy.assertEditorForActiveResource(
        userId, sourceId, objectId, ADD_REMARK_OPERATION);
    if (remark == null || remark.content().isBlank()) {
      throw new GlobalOpenCopException("Bad remark request", HttpStatus.BAD_REQUEST);
    }

    remarkService.addRemark(new PersistRemarkRequest(sourceId, objectId, remark.content(), userId));
  }

  public List<Remark> retrieveRemarks(UUID userId, UUID sourceId, String objectId) {
    eventAuthorizationPolicy.assertMember(userId, sourceId, objectId);

    try {
      return remarkService.retrieveRemarks(sourceId, objectId);
    } catch (IdpException _) {
      throw new GlobalOpenCopException("Could not find remarks for event.", HttpStatus.NOT_FOUND);
    }
  }

  public void updateRemark(
      UUID userId, UUID sourceId, String objectId, UpdateRemarkRequest remark) {
    eventAuthorizationPolicy.assertEditorForActiveResource(
        userId, sourceId, objectId, UPDATE_REMARK_OPERATION);
    if (remark == null || remark.content().isBlank()) {
      throw new GlobalOpenCopException("Bad remark request", HttpStatus.BAD_REQUEST);
    }

    try {
      remarkService.updateRemark(userId, remark);
    } catch (PersistenceException _) {
      throw new GlobalOpenCopException("Failed to update remark", HttpStatus.NOT_FOUND);
    }
  }

  public void deleteRemark(UUID userId, UUID sourceId, String objectId, UUID id) {
    eventAuthorizationPolicy.assertEditorForActiveResource(
        userId, sourceId, objectId, DELETE_REMARK_OPERATION);
    try {
      remarkService.deleteRemark(userId, id);
    } catch (PersistenceException _) {
      throw new GlobalOpenCopException("Failed to delete remark", HttpStatus.NOT_FOUND);
    }
  }
}
