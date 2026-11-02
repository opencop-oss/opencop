// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.runtimefeature;

import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureActor;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureConsumerClass;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureHistoryActor;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureHistoryEntry;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureHistoryPage;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureHistoryQuery;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureRegistrationResult;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureState;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureWriteResult;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureBusyException;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureHistoryReader;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureMutationGateway;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureStateReader;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

public class JpaRuntimeFeatureStore
    implements RuntimeFeatureStateReader,
        RuntimeFeatureMutationGateway,
        RuntimeFeatureHistoryReader {

  private final RuntimeFeatureJpaRepository featureRepository;
  private final RuntimeFeatureAudienceUserGroupJpaRepository audienceUserGroupRepository;
  private final RuntimeFeatureHistoryJpaRepository historyRepository;
  private final RuntimeFeatureAudienceJsonMapper audienceJsonMapper;
  private final Clock clock;

  public JpaRuntimeFeatureStore(
      RuntimeFeatureJpaRepository featureRepository,
      RuntimeFeatureAudienceUserGroupJpaRepository audienceUserGroupRepository,
      RuntimeFeatureHistoryJpaRepository historyRepository,
      ObjectMapper objectMapper,
      Clock clock) {
    this.featureRepository = featureRepository;
    this.audienceUserGroupRepository = audienceUserGroupRepository;
    this.historyRepository = historyRepository;
    this.audienceJsonMapper = new RuntimeFeatureAudienceJsonMapper(objectMapper);
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public List<RuntimeFeatureState> findAll() {
    var rowsByFeature = new LinkedHashMap<String, List<RuntimeFeatureAudienceRow>>();
    featureRepository
        .findAllAudienceRows()
        .forEach(
            row ->
                rowsByFeature
                    .computeIfAbsent(row.getFeatureName(), _ -> new ArrayList<>())
                    .add(row));
    return rowsByFeature.values().stream().map(JpaRuntimeFeatureStore::toState).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RuntimeFeatureState> findByName(String name) {
    requireName(name);
    var rows = featureRepository.findAudienceRowsByName(name);
    return rows.isEmpty() ? Optional.empty() : Optional.of(toState(rows));
  }

  @Override
  @Transactional
  public RuntimeFeatureWriteResult replaceAudience(
      String name,
      RuntimeFeatureAudience audience,
      OptionalLong expectedRevision,
      RuntimeFeatureActor actor) {
    requireName(name);
    Objects.requireNonNull(audience, "audience is required");
    Objects.requireNonNull(expectedRevision, "expectedRevision is required");
    Objects.requireNonNull(actor, "actor is required");
    if (expectedRevision.isPresent() && expectedRevision.orElseThrow() < 0) {
      throw new IllegalArgumentException("expectedRevision must not be negative");
    }

    var feature = findByNameForUpdate(name);
    if (feature.isEmpty()) {
      return new RuntimeFeatureWriteResult.Unknown(name);
    }

    var entity = feature.orElseThrow();
    var previousAudience = currentAudienceForLockedWrite(entity);
    var previousState = toState(entity, previousAudience);
    if (expectedRevision.isPresent() && expectedRevision.orElseThrow() != entity.getRevision()) {
      return new RuntimeFeatureWriteResult.Stale(previousState);
    }
    var occurredAt = clock.instant();
    if (previousAudience.equals(audience)) {
      persistPatchHistory(
          entity,
          previousAudience,
          audience,
          entity.getRevision(),
          entity.getRevision(),
          occurredAt,
          actor);
      return new RuntimeFeatureWriteResult.Idempotent(previousState);
    }

    var revisionBefore = entity.getRevision();
    replaceAudienceTargets(name, audience);
    entity.setAudienceMode(toEntityMode(audience));

    entity.setRevision(entity.getRevision() + 1);
    entity.setUpdatedAt(occurredAt);
    featureRepository.saveAndFlush(entity);

    persistPatchHistory(
        entity,
        previousAudience,
        audience,
        revisionBefore,
        entity.getRevision(),
        occurredAt,
        actor);

    return new RuntimeFeatureWriteResult.Applied(toState(entity, audience), previousAudience);
  }

  private Optional<RuntimeFeatureEntity> findByNameForUpdate(String name) {
    try {
      return featureRepository.findByNameForUpdate(name);
    } catch (PessimisticLockingFailureException exception) {
      throw new RuntimeFeatureBusyException(exception);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public RuntimeFeatureHistoryPage findPage(RuntimeFeatureHistoryQuery query) {
    Objects.requireNonNull(query, "query is required");
    var pageable =
        PageRequest.of(
            query.page(),
            query.size(),
            Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
    var result = historyRepository.findAll(matches(query), pageable);
    return new RuntimeFeatureHistoryPage(
        result.getContent().stream().map(this::toHistoryEntry).toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Override
  @Transactional
  public RuntimeFeatureRegistrationResult register(String name, RuntimeFeatureActor actor) {
    requireName(name);
    Objects.requireNonNull(actor, "actor is required");

    var occurredAt = clock.instant();
    var inserted = featureRepository.insertFrontendOnlyIfAbsent(name, occurredAt);

    // Safe on both branches: the conflict-free insert never fails the transaction, so this read
    // is valid whether this caller won the race or lost it. No audience targets can exist yet on
    // a row we just created, and a pre-existing row is returned exactly as stored.
    var rows = featureRepository.findAudienceRowsByName(name);
    if (rows.isEmpty()) {
      throw new IllegalStateException(
          "Runtime feature disappeared immediately after insert: " + name);
    }
    var state = toState(rows);

    if (inserted == 1) {
      historyRepository.saveAndFlush(
          RuntimeFeatureHistoryEntity.create(
              name,
              audienceJsonMapper.write(state.audience()),
              occurredAt,
              actor.userId(),
              actor.fullName().orElse(null),
              actor.email().orElse(null),
              actor.globalAdmin()));
      return new RuntimeFeatureRegistrationResult.Created(state);
    }
    return new RuntimeFeatureRegistrationResult.AlreadyExists(state);
  }

  private RuntimeFeatureAudience currentAudienceForLockedWrite(RuntimeFeatureEntity entity) {
    return switch (entity.getAudienceMode()) {
      case NONE -> new RuntimeFeatureAudience.None();
      case ALL -> new RuntimeFeatureAudience.All();
      case USER_GROUPS -> {
        // replaceAudience already holds the feature row's write lock. Every store mutation takes
        // that lock before replacing targets, so this mutation-only target query is stable.
        var userGroupIds = new TreeSet<UUID>();
        audienceUserGroupRepository
            .findAllByIdFeatureName(entity.getName())
            .forEach(target -> userGroupIds.add(target.getId().getUserGroupId()));
        yield new RuntimeFeatureAudience.UserGroups(userGroupIds);
      }
    };
  }

  private static RuntimeFeatureState toState(List<RuntimeFeatureAudienceRow> rows) {
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("At least one runtime feature audience row is required");
    }
    var feature = rows.getFirst();
    var userGroupIds = new TreeSet<UUID>();
    rows.stream()
        .map(RuntimeFeatureAudienceRow::getUserGroupId)
        .filter(Objects::nonNull)
        .forEach(userGroupIds::add);
    var audience =
        switch (feature.getAudienceMode()) {
          case NONE -> {
            requireNoTargets(feature, userGroupIds);
            yield new RuntimeFeatureAudience.None();
          }
          case ALL -> {
            requireNoTargets(feature, userGroupIds);
            yield new RuntimeFeatureAudience.All();
          }
          case USER_GROUPS -> new RuntimeFeatureAudience.UserGroups(userGroupIds);
        };
    return new RuntimeFeatureState(
        feature.getFeatureName(),
        RuntimeFeatureConsumerClass.valueOf(feature.getConsumerClass().name()),
        audience,
        feature.getRevision(),
        feature.getUpdatedAt());
  }

  private static void requireNoTargets(
      RuntimeFeatureAudienceRow feature, TreeSet<UUID> userGroupIds) {
    if (!userGroupIds.isEmpty()) {
      throw new IllegalStateException(
          "Runtime feature "
              + feature.getFeatureName()
              + " has targets for "
              + feature.getAudienceMode());
    }
  }

  private void replaceAudienceTargets(String featureName, RuntimeFeatureAudience audience) {
    // This bulk delete executes before Hibernate queues overlapping replacement inserts. The
    // repository also clears target entities loaded during hydration so a retained A from
    // {A,B} -> {A,C} is actually reinserted rather than mistaken for an existing managed row.
    audienceUserGroupRepository.deleteAllByIdFeatureName(featureName);
    if (audience instanceof RuntimeFeatureAudience.UserGroups(var userGroupIds)) {
      audienceUserGroupRepository.saveAll(
          userGroupIds.stream()
              .map(id -> new RuntimeFeatureAudienceUserGroupEntity(featureName, id))
              .toList());
    }
  }

  private static RuntimeFeatureState toState(
      RuntimeFeatureEntity entity, RuntimeFeatureAudience audience) {
    return new RuntimeFeatureState(
        entity.getName(),
        RuntimeFeatureConsumerClass.valueOf(entity.getConsumerClass().name()),
        audience,
        entity.getRevision(),
        entity.getUpdatedAt());
  }

  private RuntimeFeatureHistoryEntry toHistoryEntry(RuntimeFeatureHistoryEntity entity) {
    return new RuntimeFeatureHistoryEntry(
        entity.getId(),
        entity.getFeatureName(),
        RuntimeFeatureConsumerClass.valueOf(entity.getConsumerClass().name()),
        entity.getOperation(),
        readAudience(entity.getBeforeAudience()),
        audienceJsonMapper.read(entity.getAfterAudience()),
        entity.getRevisionBefore(),
        entity.getRevisionAfter(),
        entity.getOccurredAt(),
        new RuntimeFeatureHistoryActor(
            entity.getActorUserId(),
            entity.getActorFullName(),
            entity.getActorEmail(),
            entity.isActorGlobalAdmin()));
  }

  private RuntimeFeatureAudience readAudience(String json) {
    return json == null ? null : audienceJsonMapper.read(json);
  }

  private void persistPatchHistory(
      RuntimeFeatureEntity entity,
      RuntimeFeatureAudience beforeAudience,
      RuntimeFeatureAudience afterAudience,
      long revisionBefore,
      long revisionAfter,
      Instant occurredAt,
      RuntimeFeatureActor actor) {
    historyRepository.saveAndFlush(
        RuntimeFeatureHistoryEntity.patch(
            entity.getName(),
            entity.getConsumerClass(),
            audienceJsonMapper.write(beforeAudience),
            audienceJsonMapper.write(afterAudience),
            revisionBefore,
            revisionAfter,
            occurredAt,
            actor.userId(),
            actor.fullName().orElse(null),
            actor.email().orElse(null),
            actor.globalAdmin()));
  }

  private static Specification<RuntimeFeatureHistoryEntity> matches(
      RuntimeFeatureHistoryQuery query) {
    return (root, _, criteriaBuilder) -> {
      var predicates = new ArrayList<Predicate>(5);
      if (query.featureName() != null) {
        predicates.add(criteriaBuilder.equal(root.get("featureName"), query.featureName()));
      }
      if (query.operation() != null) {
        predicates.add(criteriaBuilder.equal(root.get("operation"), query.operation()));
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

  private static RuntimeFeatureAudienceModeEntity toEntityMode(RuntimeFeatureAudience audience) {
    return switch (audience) {
      case RuntimeFeatureAudience.None _ -> RuntimeFeatureAudienceModeEntity.NONE;
      case RuntimeFeatureAudience.All _ -> RuntimeFeatureAudienceModeEntity.ALL;
      case RuntimeFeatureAudience.UserGroups _ -> RuntimeFeatureAudienceModeEntity.USER_GROUPS;
    };
  }

  private static void requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
  }
}
