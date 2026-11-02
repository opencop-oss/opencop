// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.gateway.db;

import com.opencop.core.api.watchbox.business.model.WatchboxConfig;
import com.opencop.core.api.watchbox.business.model.WatchboxConfigAuditEntry;
import com.opencop.core.api.watchbox.business.model.WatchboxConfigAuditPage;
import com.opencop.core.api.watchbox.business.model.WatchboxConfigAuditQuery;
import com.opencop.core.api.watchbox.business.model.WatchboxConfigAuditSnapshot;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxConfigAuditReader;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxConfigAuditRecorder;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.sdk.model.watchbox.WatchboxResetReason;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxConfigAuditEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxConfigAuditJpaRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JpaWatchboxConfigAuditStore
    implements WatchboxConfigAuditRecorder, WatchboxConfigAuditReader {

  private static final Logger log = LoggerFactory.getLogger(JpaWatchboxConfigAuditStore.class);

  private final WatchboxConfigAuditJpaRepository repository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public JpaWatchboxConfigAuditStore(
      WatchboxConfigAuditJpaRepository repository, ObjectMapper objectMapper, Clock clock) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordCreate(WatchboxConfig created, WatchboxCaller actor) {
    var afterSnapshot = WatchboxConfigAuditSnapshot.from(created);
    persist(
        WatchboxConfigAuditEntity.create(
            created.id(),
            writeJson(afterSnapshot),
            created.configEpoch(),
            clock.instant(),
            actor.userId(),
            actor.fullName(),
            actor.email(),
            actor.globalAdmin()),
        WatchboxConfigAuditEntry.Operation.CREATE,
        created.id());
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordPatch(
      WatchboxConfig before,
      WatchboxConfig after,
      List<WatchboxResetReason> resetReasons,
      WatchboxCaller actor) {
    var beforeSnapshot = WatchboxConfigAuditSnapshot.from(before);
    var afterSnapshot = WatchboxConfigAuditSnapshot.from(after);
    persist(
        WatchboxConfigAuditEntity.patch(
            after.id(),
            writeJson(beforeSnapshot),
            writeJson(afterSnapshot),
            writeJson(resetReasons),
            before.configEpoch(),
            after.configEpoch(),
            clock.instant(),
            actor.userId(),
            actor.fullName(),
            actor.email(),
            actor.globalAdmin()),
        WatchboxConfigAuditEntry.Operation.PATCH,
        after.id());
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordArchive(WatchboxConfig before, WatchboxCaller actor) {
    var beforeSnapshot = WatchboxConfigAuditSnapshot.from(before);
    persist(
        WatchboxConfigAuditEntity.archive(
            before.id(),
            writeJson(beforeSnapshot),
            before.configEpoch(),
            clock.instant(),
            actor.userId(),
            actor.fullName(),
            actor.email(),
            actor.globalAdmin()),
        WatchboxConfigAuditEntry.Operation.ARCHIVE,
        before.id());
  }

  @Override
  @Transactional(readOnly = true)
  public WatchboxConfigAuditPage findPage(WatchboxConfigAuditQuery query) {
    var pageable =
        PageRequest.of(
            query.page(),
            query.size(),
            Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
    var result = repository.findAll(matches(query), pageable);
    return new WatchboxConfigAuditPage(
        result.getContent().stream().map(this::toEntry).toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  private static Specification<WatchboxConfigAuditEntity> matches(WatchboxConfigAuditQuery query) {
    return (root, _, criteriaBuilder) -> {
      var predicates = new ArrayList<Predicate>(5);
      if (query.watchboxId() != null) {
        predicates.add(criteriaBuilder.equal(root.get("watchboxId"), query.watchboxId()));
      }
      if (query.operation() != null) {
        predicates.add(criteriaBuilder.equal(root.get("operation"), query.operation().name()));
      }
      if (query.actorUserId() != null) {
        predicates.add(criteriaBuilder.equal(root.get("actorUserId"), query.actorUserId()));
      }
      if (query.occurredFrom() != null) {
        predicates.add(
            criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), query.occurredFrom()));
      }
      if (query.occurredTo() != null) {
        predicates.add(
            criteriaBuilder.lessThanOrEqualTo(root.get("occurredAt"), query.occurredTo()));
      }
      return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    };
  }

  private WatchboxConfigAuditEntry toEntry(WatchboxConfigAuditEntity entity) {
    return new WatchboxConfigAuditEntry(
        entity.getId(),
        entity.getWatchboxId(),
        WatchboxConfigAuditEntry.Operation.valueOf(entity.getOperation()),
        readSnapshot(entity.getBeforeSnapshot()),
        readSnapshot(entity.getAfterSnapshot()),
        List.of(objectMapper.readValue(entity.getResetReasons(), WatchboxResetReason[].class)),
        entity.getConfigEpochBefore(),
        entity.getConfigEpochAfter(),
        entity.getOccurredAt(),
        new WatchboxConfigAuditEntry.Actor(
            entity.getActorUserId(),
            entity.getActorFullName(),
            entity.getActorEmail(),
            entity.isActorGlobalAdmin()));
  }

  private WatchboxConfigAuditSnapshot readSnapshot(String json) {
    return json == null ? null : objectMapper.readValue(json, WatchboxConfigAuditSnapshot.class);
  }

  private String writeJson(Object value) {
    return objectMapper.writeValueAsString(value);
  }

  private void persist(
      WatchboxConfigAuditEntity entity,
      WatchboxConfigAuditEntry.Operation operation,
      UUID watchboxId) {
    try {
      repository.saveAndFlush(entity);
    } catch (RuntimeException exception) {
      log.error(
          "WATCHBOX_CONFIG_AUDIT_WRITE_FAILED operation={} watchbox_id={} exception_class={}",
          operation,
          watchboxId,
          exception.getClass().getSimpleName());
      throw exception;
    }
  }
}
