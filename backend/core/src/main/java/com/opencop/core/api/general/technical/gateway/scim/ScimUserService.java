// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.scim;

import static com.opencop.core.api.general.technical.gateway.scim.ScimHelper.normalizePath;
import static com.opencop.core.api.general.technical.gateway.scim.ScimHelper.validatePatch;

import com.opencop.core.api.general.ports.technical.gateway.ScimUserMutationGateway;
import com.opencop.core.api.general.technical.endpoint.scim.ScimExceptions;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.unboundid.scim2.common.messages.PatchOperation;
import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.types.Email;
import com.unboundid.scim2.common.types.UserResource;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;

@Service
public class ScimUserService implements ScimUserMutationGateway {

  private final InternalUserRepository repo;
  private final String issuer;

  public ScimUserService(
      InternalUserRepository repo, @Value("${opencop.idp.issuer}") String issuer) {
    this.repo = repo;
    this.issuer = issuer;
  }

  @Override
  @Transactional
  public CreateMutation create(UserResource request) {
    validateCreate(request);

    var byExternal = repo.findByIdpIssuerAndIdpUserId(issuer, request.getExternalId());
    if (byExternal.isPresent()) {
      var user = byExternal.get();
      var conflictDetail = "User with externalId already exists: " + request.getExternalId();
      if (user.isActive()) {
        throw new ScimExceptions.ScimConflictException(conflictDetail);
      }

      ScimUserMapper.applyCreate(request, user);
      var saved = repo.save(user);
      boolean active = requestedActive(request);
      return new CreateMutation(
          saved.getId(),
          saved.getEmail(),
          active,
          true,
          conflictDetail,
          mutationResponse(saved, active));
    }

    // username check
    var byUsername = repo.findByIdpIssuerAndIdpUsername(issuer, request.getUserName());
    if (byUsername.isPresent()) {
      var user = byUsername.get();
      var conflictDetail = "User with userName already exists: " + request.getUserName();

      if (user.isActive()) {
        throw new ScimExceptions.ScimConflictException(conflictDetail);
      }
      // prevents a different externalId reusing the same username silently
      // With keycloak, deleting a user is not the same as other idps, the user will have to be
      // recreated so a new externalId will be provided, therefore a username cannot be re-used
      // even though it was deleted in keycloak
      if (user.getIdpUserId() != null
          && !user.getIdpUserId().isBlank()
          && request.getExternalId() != null
          && !request.getExternalId().isBlank()
          && !user.getIdpUserId().equals(request.getExternalId())) {
        throw new ScimExceptions.ScimConflictException(
            "Inactive username exists but is bound to a different externalId");
      }

      user.setIdpUserId(request.getExternalId());
      ScimUserMapper.applyCreate(request, user);
      var saved = repo.save(user);
      boolean active = requestedActive(request);
      return new CreateMutation(
          saved.getId(),
          saved.getEmail(),
          active,
          true,
          conflictDetail,
          mutationResponse(saved, active));
    }

    // normal create
    var entity = new InternalUserEntity();
    entity.setIdpIssuer(issuer);

    ScimUserMapper.applyCreate(request, entity);

    try {
      var saved = repo.saveAndFlush(entity);
      boolean active = requestedActive(request);
      return new CreateMutation(
          saved.getId(), saved.getEmail(), active, false, null, mutationResponse(saved, active));
    } catch (DataIntegrityViolationException e) {
      throw new ScimExceptions.ScimConflictException(
          "User already exists (unique constraint) | " + e.getMessage());
    }
  }

  @Transactional(readOnly = true)
  public UserResource getById(UUID id) {
    var entity =
        repo.findById(id)
            .orElseThrow(() -> new ScimExceptions.ScimNotFoundException("User not found: " + id));
    return ScimUserMapper.toScim(entity);
  }

  @Transactional(readOnly = true)
  public Page<UserResource> list(String filter, int startIndex, int count) {
    int safeStart = Math.max(startIndex, 1);
    int safeCount = count <= 0 ? 100 : Math.min(count, 200);
    int pageIndex = (safeStart - 1) / safeCount;

    Pageable pageable = PageRequest.of(pageIndex, safeCount, Sort.by("idpUsername").ascending());

    // No filter => list all
    if (filter == null || filter.isBlank()) {
      return repo.findAll(pageable).map(ScimUserMapper::toScim);
    }

    ScimHelper.MultiEqFilter eqFilter =
        ScimHelper.parseFilterSafe(filter)
            .orElseThrow(() -> new ScimExceptions.ScimInvalidFilterException(filter));

    Page<InternalUserEntity> page =
        switch (eqFilter.field().toLowerCase()) {
          case "username" -> {
            if (eqFilter.values().size() == 1) {
              yield repo.findByIdpIssuerAndIdpUsername(
                  issuer, eqFilter.values().getFirst(), pageable);
            }
            yield repo.findByIdpIssuerAndIdpUsernameIn(issuer, eqFilter.values(), pageable);
          }
          case "externalid" -> {
            if (eqFilter.values().size() == 1) {
              yield repo.findByIdpIssuerAndIdpUserId(
                  issuer, eqFilter.values().getFirst(), pageable);
            }
            yield repo.findByIdpIssuerAndIdpUserIdIn(issuer, eqFilter.values(), pageable);
          }
          case "id" -> {
            if (eqFilter.values().size() == 1) {
              UUID id;
              try {
                id = UUID.fromString(eqFilter.values().getFirst());
              } catch (IllegalArgumentException e) {
                throw new ScimExceptions.ScimInvalidFilterException(filter);
              }
              var entity = repo.findById(id);
              List<InternalUserEntity> list = entity.map(List::of).orElseGet(List::of);
              yield new PageImpl<>(list, pageable, list.size());
            }

            // Validate UUIDs
            List<UUID> ids = new ArrayList<>(eqFilter.values().size());
            try {
              for (String v : eqFilter.values()) {
                ids.add(UUID.fromString(v));
              }
            } catch (IllegalArgumentException e) {
              throw new ScimExceptions.ScimInvalidFilterException(filter);
            }

            yield repo.findByIdpIssuerAndIdIn(issuer, ids, pageable);
          }
          default -> throw new ScimExceptions.ScimInvalidFilterException(filter);
        };

    return page.map(ScimUserMapper::toScim);
  }

  @Override
  @Transactional
  public ActiveMutation replace(String internalId, UserResource request) {
    validateUpdate(request);

    // Find existing internal user by internal id
    var entity =
        repo.findByIdForScimMutation(UUID.fromString(internalId))
            .orElseThrow(
                () -> new ScimExceptions.ScimNotFoundException("User not found: " + internalId));

    // Dont let scim update them
    if (request.getExternalId() == null || request.getExternalId().isBlank()) {
      // some clients might not send it, so reassign the current one
      request.setExternalId(entity.getIdpUserId());
    } else if (!request.getExternalId().equals(entity.getIdpUserId())) {
      throw new ScimExceptions.ScimConflictException("externalId cannot be changed once linked");
    }

    // Make sure the username is unique
    repo.findByIdpIssuerAndIdpUsername(issuer, request.getUserName())
        .filter(u -> !u.getId().equals(entity.getId()))
        .ifPresent(
            _ -> {
              throw new ScimExceptions.ScimConflictException(
                  "userName already exists: " + request.getUserName());
            });

    // Make sure emails are unique
    if (request.getEmails() != null) {
      request.getEmails().stream()
          .filter(e -> e.getValue() != null && !e.getValue().isBlank())
          .findFirst()
          .map(Email::getValue)
          .ifPresent(
              primaryEmail ->
                  repo.findByIdpIssuerAndEmail(issuer, primaryEmail)
                      .filter(user -> !user.getId().equals(entity.getId()))
                      .ifPresent(
                          _ -> {
                            throw new ScimExceptions.ScimConflictException(
                                "email already exists: " + primaryEmail);
                          }));
    }

    boolean active = requestedActive(request);
    ScimUserMapper.applyReplace(request, entity);

    entity.setUpdatedAt(Instant.now());
    var saved = repo.save(entity);
    return new ActiveMutation(saved.getId(), active, mutationResponse(saved, active));
  }

  private void validateUpdate(UserResource request) {
    if (request == null) {
      throw new ScimExceptions.ScimBadRequestException("Request body is required");
    }

    if (request.getSchemaUrns() == null
        || !request.getSchemaUrns().contains("urn:ietf:params:scim:schemas:core:2.0:User")) {
      throw new ScimExceptions.ScimBadRequestException("Missing required SCIM core User schema");
    }

    // userName is required for PUT
    if (request.getUserName() == null || request.getUserName().isBlank()) {
      throw new ScimExceptions.ScimBadRequestException("userName is required");
    }

    // For externalId, allow missing but not blank if present
    if (request.getExternalId() != null && request.getExternalId().isBlank()) {
      throw new ScimExceptions.ScimBadRequestException("externalId must not be blank if provided");
    }
  }

  @Override
  @Transactional
  public PatchMutation patch(UUID id, PatchRequest patch) {
    var entity =
        repo.findByIdForScimMutation(id)
            .orElseThrow(() -> new ScimExceptions.ScimNotFoundException("User not found: " + id));

    validatePatch(patch);

    List<Boolean> requestedActiveStates = new ArrayList<>();
    for (PatchOperation op : patch.getOperations()) {
      applyOperation(entity, op, requestedActiveStates);
    }

    entity.setUpdatedAt(Instant.now());
    var saved = repo.save(entity);
    var response = ScimUserMapper.toScim(saved);
    if (!requestedActiveStates.isEmpty()) {
      response.setActive(requestedActiveStates.getLast());
    }
    return new PatchMutation(saved.getId(), requestedActiveStates, response);
  }

  private void applyOperation(
      InternalUserEntity entity, PatchOperation op, List<Boolean> requestedActiveStates) {
    switch (op.getOpType()) {
      case ADD, REPLACE -> applyAddOrReplace(entity, op, requestedActiveStates);
      case REMOVE -> applyRemove(entity, op);
      default ->
          throw new ScimExceptions.ScimBadRequestException(
              "Unsupported operation type: " + op.getOpType());
    }
  }

  private void applyAddOrReplace(
      InternalUserEntity entity, PatchOperation operation, List<Boolean> requestedActiveStates) {
    String path = normalizePath(operation.getPath());

    if (path == null) {
      throw new ScimExceptions.ScimBadRequestException("Patch without path not supported.");
    }
    try {
      switch (path) {
        case "userName" -> entity.setIdpUsername(operation.getValue(String.class));
        case "displayName" -> entity.setDisplayName(operation.getValue(String.class));
        case "name.givenName" -> entity.setFirstName(operation.getValue(String.class));
        case "name.familyName" -> entity.setLastName(operation.getValue(String.class));
        case "active" -> requestedActiveStates.add(operation.getValue(Boolean.class));
        case "emails" -> entity.setEmail(extractPrimaryEmail(operation.getValue(List.class)));
        case "emails.value" -> entity.setEmail(operation.getValue(String.class));
        default -> {
          // Nothing to do, we dont handle the field
        }
      }
    } catch (JacksonException _) {
      throw new ScimExceptions.ScimBadRequestException("Could not deserialize path: " + path);
    }
  }

  private void applyRemove(InternalUserEntity entity, PatchOperation operation) {
    String path = normalizePath(operation.getPath());

    if (path == null) {
      throw new ScimExceptions.ScimBadRequestException("REMOVE requires a path.");
    }

    switch (path) {
      case "displayName" -> entity.setDisplayName(null);
      case "name.givenName" -> entity.setFirstName(null);
      case "name.familyName" -> entity.setLastName(null);
      case "emails", "emails.value" -> entity.setEmail(null);
      default ->
          throw new ScimExceptions.ScimBadRequestException("Unsupported remove path: " + path);
    }
  }

  private String extractPrimaryEmail(Object value) {
    if (value == null) {
      return null;
    }

    if (value instanceof List<?> list && !list.isEmpty()) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> itemMap) {
          Object primary = itemMap.get("primary");
          Object email = itemMap.get("value");
          if (Boolean.TRUE.equals(primary) && email != null) {
            return String.valueOf(email);
          }
        }
      }
      Object first = list.getFirst();
      if (first instanceof Map<?, ?> itemMap) {
        Object email = itemMap.get("value");
        return email != null ? String.valueOf(email) : null;
      }
    }

    return null;
  }

  @Override
  @Transactional(readOnly = true)
  public UUID requireExisting(UUID internalId) {
    InternalUserEntity user =
        repo.findById(internalId)
            .orElseThrow(() -> new EntityNotFoundException("SCIM user not found: " + internalId));

    return user.getId();
  }

  private static boolean requestedActive(UserResource request) {
    return request.getActive() == null || request.getActive();
  }

  private static UserResource mutationResponse(InternalUserEntity entity, boolean active) {
    var response = ScimUserMapper.toScim(entity);
    response.setActive(active);
    return response;
  }

  private void validateCreate(UserResource request) {
    if (request == null) {
      throw new ScimExceptions.ScimBadRequestException("Missing body.");
    }
    if (request.getUserName() == null || request.getUserName().isBlank()) {
      throw new ScimExceptions.ScimBadRequestException("userName is required.");
    }
    if (request.getExternalId() == null || request.getExternalId().isBlank()) {
      throw new ScimExceptions.ScimBadRequestException("externalId is required.");
    }
  }
}
