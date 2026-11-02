// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.media;

import com.opencop.core.api.general.ports.gateway.media.MediaRepository;
import com.opencop.core.api.general.ports.usecase.media.MediaListFilter;
import com.opencop.core.api.general.ports.usecase.media.MediaPage;
import com.opencop.core.sdk.model.media.MediaRecord;
import com.opencop.core.sdk.technical.gateway.db.media.MediaAttachmentEntity;
import com.opencop.core.sdk.technical.gateway.db.media.MediaAttachmentJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaMediaRepository implements MediaRepository {

  private final MediaJpaRepository jpa;
  private final MediaAttachmentJpaRepository attachmentJpa;

  @PersistenceContext private EntityManager entityManager;

  public JpaMediaRepository(MediaJpaRepository jpa, MediaAttachmentJpaRepository attachmentJpa) {
    this.jpa = jpa;
    this.attachmentJpa = attachmentJpa;
  }

  @Override
  @Transactional
  public MediaRecord save(MediaRecord record) {
    // media_attachment must be inserted before media_file (FK constraint)
    var attachment = toAttachmentEntity(record);
    var entity = toEntity(record);
    attachmentJpa.save(attachment);
    jpa.save(entity);
    // open-in-view binds a single EntityManager to the HTTP request, so the L1 cache survives
    // across @Transactional boundaries within the same request. The persisted entities have
    // attachment=null (toEntity doesn't set it). Flushing sends the writes to the DB connection;
    // detaching evicts the entries so the next find() reloads fresh from DB and the EAGER
    // @PrimaryKeyJoinColumn join is populated correctly.
    entityManager.flush();
    entityManager.detach(entity);
    entityManager.detach(attachment);
    return record;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<MediaRecord> findById(UUID id) {
    return jpa.findById(id).map(JpaMediaRepository::toRecord);
  }

  @Override
  @Transactional(readOnly = true)
  public MediaPage list(int page, int size, MediaListFilter filter) {
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "attachment.createdAt"));

    Specification<MediaEntity> spec = (root, query, cb) -> cb.conjunction();
    if (filter.contentType() != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("contentType"), filter.contentType()));
    }
    if (filter.usage() != null) {
      spec =
          spec.and(
              (root, query, cb) -> cb.equal(root.get("attachment").get("usage"), filter.usage()));
    } else if (filter.usageIsNull()) {
      spec = spec.and((root, query, cb) -> cb.isNull(root.get("attachment").get("usage")));
    }
    if (!filter.includeThumbnails()) {
      spec = spec.and((root, query, cb) -> cb.isNull(root.get("thumbnailOf")));
    }

    var result = jpa.findAll(spec, pageable);
    var items = result.getContent().stream().map(JpaMediaRepository::toRecord).toList();
    return new MediaPage(
        items, result.getNumber(), result.getTotalPages(), result.getTotalElements());
  }

  @Override
  @Transactional
  public void deleteById(UUID id) {
    // media_file (child) deleted first so FK to media_attachment is released,
    // then media_attachment deletion cascades to event_attachment.
    jpa.deleteById(id);
    attachmentJpa.deleteById(id);
  }

  @Override
  @Transactional
  public Optional<MediaRecord> findByIdForUpdate(UUID id) {
    return jpa.findByIdForUpdate(id).map(JpaMediaRepository::toRecord);
  }

  @Override
  @Transactional(readOnly = true)
  public List<MediaRecord> findThumbnailsOf(UUID originalMediaId) {
    return jpa.findByThumbnailOf(originalMediaId).stream()
        .map(JpaMediaRepository::toRecord)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByBlobKeyExcludingIds(String blobKey, Collection<UUID> excludedIds) {
    return jpa.existsByBlobKeyAndIdNotIn(blobKey, excludedIds);
  }

  private static MediaAttachmentEntity toAttachmentEntity(MediaRecord r) {
    return MediaAttachmentEntity.of(
        r.id(), r.access(), r.creatorId(), r.ownerId(), r.createdAt(), r.usage());
  }

  private static MediaEntity toEntity(MediaRecord r) {
    var e = new MediaEntity();
    e.setId(r.id());
    e.setBlobKey(r.blobKey());
    e.setOriginalFilename(r.originalFilename());
    e.setContentType(r.contentType());
    e.setSizeBytes(r.sizeBytes());
    e.setMimeType(r.mimeType());
    e.setAttributes(r.attributes());
    e.setThumbnailOf(r.thumbnailOf());
    return e;
  }

  private static MediaRecord toRecord(MediaEntity e) {
    var a = e.getAttachment();
    return new MediaRecord(
        e.getId(),
        e.getBlobKey(),
        e.getOriginalFilename(),
        e.getContentType(),
        e.getSizeBytes(),
        a.getAccess(),
        a.getCreatorId(),
        a.getOwnerId(),
        a.getCreatedAt(),
        e.getMimeType(),
        e.getAttributes(),
        e.getThumbnailOf(),
        a.getUsage());
  }
}
