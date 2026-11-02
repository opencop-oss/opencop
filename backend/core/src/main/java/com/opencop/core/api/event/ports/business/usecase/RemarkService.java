// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.business.usecase;

import com.opencop.core.sdk.ports.gateway.idp.IdpException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.model.opencop.PersistRemarkRequest;
import com.opencop.core.sdk.technical.model.opencop.Remark;
import com.opencop.core.sdk.technical.model.opencop.UpdateRemarkRequest;
import java.util.List;
import java.util.UUID;

// TODO: Add updating.
public interface RemarkService {
  void addRemark(PersistRemarkRequest remarkRequest);

  List<Remark> retrieveRemarks(UUID sourceId, String objectId) throws IdpException;

  void updateRemark(UUID userId, UpdateRemarkRequest updateRemarkRequest)
      throws PersistenceException;

  void deleteRemark(UUID userId, UUID id) throws PersistenceException;
}
