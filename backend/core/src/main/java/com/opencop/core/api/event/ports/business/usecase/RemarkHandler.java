// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.business.usecase;

import com.opencop.core.sdk.technical.model.opencop.CreateRemarkRequest;
import com.opencop.core.sdk.technical.model.opencop.Remark;
import com.opencop.core.sdk.technical.model.opencop.UpdateRemarkRequest;
import java.util.List;
import java.util.UUID;

public interface RemarkHandler {
  void addRemark(UUID userId, UUID sourceId, String objectId, CreateRemarkRequest remark);

  List<Remark> retrieveRemarks(UUID userId, UUID sourceId, String objectId);

  void updateRemark(UUID userId, UUID sourceId, String objectId, UpdateRemarkRequest remark);

  void deleteRemark(UUID userId, UUID sourceId, String objectId, UUID id);
}
