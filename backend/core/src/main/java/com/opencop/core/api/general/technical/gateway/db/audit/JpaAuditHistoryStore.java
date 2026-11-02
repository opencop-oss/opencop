// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.audit;

import com.opencop.core.api.general.business.model.BoundedText;
import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.model.audit.AuditPage;
import com.opencop.core.api.general.business.model.audit.AuditQuery;
import com.opencop.core.api.general.ports.gateway.audit.AuditReader;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.sdk.technical.gateway.db.audit.AuditHistoryEntity;
import com.opencop.core.sdk.technical.gateway.db.audit.AuditHistoryJpaRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** The one implementation of both audit ports. */
@Component
public class JpaAuditHistoryStore implements AuditRecorder, AuditReader {

  private static final Logger log = LoggerFactory.getLogger(JpaAuditHistoryStore.class);

  private final AuditHistoryJpaRepository repository;
  private final ObjectMapper objectMapper;
  private final AuditChangeWriter changeWriter;

  public JpaAuditHistoryStore(AuditHistoryJpaRepository repository, ObjectMapper objectMapper) {
    this.changeWriter = new AuditChangeWriter(objectMapper);
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(AuditEvent event) {
    var name = event.entityName();
    var storedName = BoundedText.truncate(name, AuditEvent.ENTITY_NAME_MAXIMUM);
    var truncated = name != null && !name.equals(storedName);
    var occurredAt = Instant.now();

    var entity =
        new AuditHistoryEntity(
            event.entityType().name(),
            event.entityId(),
            storedName,
            event.operation().name(),
            occurredAt,
            event.actor().userId(),
            event.actor().fullName(),
            event.actor().email(),
            event.actor().globalAdmin(),
            changeWriter.write(event.change(), truncated),
            event.changeVersion());

    try {
      // Flushed rather than left to the caller's commit, so a constraint violation surfaces here,
      // inside the transaction it belongs to, instead of after the request reports success.
      this.repository.saveAndFlush(entity);
    } catch (RuntimeException failure) {
      // Operation and entity only: never the change payload, and never the actor's name or email.
      log.error(
          "audit entry failed for {} {} at {}, run by {}",
          event.entityType(),
          event.entityId(),
          occurredAt,
          event.actor().userId(),
          failure);
      throw failure;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public AuditPage findPage(AuditQuery query) {
    var specification = matching(query);
    // occurredAt alone is not unique, so id breaks ties; without it a tied row can repeat on one
    // page and be missing from the next.
    var pageable =
        PageRequest.of(
            query.page(),
            query.size(),
            Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));

    var page = this.repository.findAll(specification, pageable);
    return new AuditPage(
        page.getContent().stream().map(this::toEntry).toList(), page.getTotalElements());
  }

  private static Specification<AuditHistoryEntity> matching(AuditQuery query) {
    return (root, criteriaQuery, builder) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (query.entityType() != null) {
        predicates.add(builder.equal(root.get("entityType"), query.entityType().name()));
      }
      if (query.entityId() != null) {
        predicates.add(builder.equal(root.get("entityId"), query.entityId()));
      }
      if (query.operation() != null) {
        predicates.add(builder.equal(root.get("operation"), query.operation().name()));
      }
      if (query.actorUserId() != null) {
        predicates.add(builder.equal(root.get("actorUserId"), query.actorUserId()));
      }
      // Both bounds inclusive, matching the endpoint's documented contract.
      if (query.occurredFrom() != null) {
        predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), query.occurredFrom()));
      }
      if (query.occurredTo() != null) {
        predicates.add(builder.lessThanOrEqualTo(root.get("occurredAt"), query.occurredTo()));
      }
      return builder.and(predicates.toArray(Predicate[]::new));
    };
  }

  private AuditEntry toEntry(AuditHistoryEntity entity) {
    return new AuditEntry(
        entity.getId(),
        AuditEntry.EntityType.valueOf(entity.getEntityType()),
        entity.getEntityId(),
        entity.getEntityName(),
        AuditEntry.Operation.valueOf(entity.getOperation()),
        entity.getOccurredAt(),
        new Caller(
            entity.getActorUserId(),
            entity.getActorFullName(),
            entity.getActorEmail(),
            entity.isActorGlobalAdmin()),
        entity.getChange(),
        entity.getChangeVersion());
  }
}
