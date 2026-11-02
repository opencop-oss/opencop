// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.remarks;

import com.opencop.core.api.event.ports.business.usecase.RemarkService;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.model.opencop.Contact;
import com.opencop.core.sdk.technical.model.opencop.PersistRemarkRequest;
import com.opencop.core.sdk.technical.model.opencop.Remark;
import com.opencop.core.sdk.technical.model.opencop.UpdateRemarkRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemarkRepository implements RemarkService {

  private final RemarkJpaRepository repo;

  public RemarkRepository(RemarkJpaRepository repo) {
    this.repo = repo;
  }

  @Override
  @Transactional
  public void addRemark(PersistRemarkRequest remarkRequest) {
    final RemarkEntity entity = new RemarkEntity();

    entity.setContent(remarkRequest.content());
    entity.setSourceId(remarkRequest.sourceId());
    entity.setObjectId(remarkRequest.objectId());
    if (remarkRequest.userId() != null) {
      entity.setUserId(remarkRequest.userId());
    }

    repo.save(entity);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Remark> retrieveRemarks(UUID sourceId, String objectId) {
    final List<RemarkEntity> entities =
        repo.findBySourceIdAndObjectIdOrderByCreatedAtDesc(sourceId, objectId);
    final List<Remark> remarks = new ArrayList<>();

    for (final RemarkEntity entity : entities) {
      final InternalUserEntity internalUser = entity.getInternalUser();
      remarks.add(
          new Remark(
              entity.getId(),
              entity.getobjectId(),
              entity.getContent(),
              internalUser != null
                  ? new Contact.Builder()
                      .withUsername(internalUser.getUsername())
                      .withFirstName(internalUser.getFirstName())
                      .withLastName(internalUser.getLastName())
                      .withEmail(internalUser.getEmail())
                      .withUserId(entity.getUserId())
                      .build()
                  : null,
              entity.getCreatedAt().toEpochMilli(),
              entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toEpochMilli()));
    }

    return remarks;
  }

  @Override
  @Transactional
  public void updateRemark(UUID userId, UpdateRemarkRequest remarkRequest)
      throws PersistenceException {
    final Optional<RemarkEntity> entityOptional = repo.findById(remarkRequest.id());
    if (entityOptional.isEmpty()) throw new PersistenceException("Remark does not exist.");

    final RemarkEntity entity = entityOptional.get();
    if (!userId.equals(entity.getUserId()))
      throw new PersistenceException("User " + userId + " is not the author of the remark.");

    entity.setContent(remarkRequest.content());
    entity.update();
    repo.save(entity);
  }

  @Override
  @Transactional
  public void deleteRemark(UUID userId, UUID id) throws PersistenceException {
    var remark = this.repo.findById(id);

    if (remark.isEmpty()) {
      throw new PersistenceException("Could not find remark: " + id);
    }
    if (!userId.equals(remark.get().getUserId())) {
      throw new PersistenceException("User " + userId + " is not the author of the remark.");
    }

    this.repo.deleteById(id);
  }
}
