// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.policy;

import static com.opencop.core.api.event.ports.business.usecase.EventExceptionCodes.INTERNAL_ERROR;

import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.model.iam.source.LayerInfo;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.ports.audit.AuthzAuditReporting;
import com.opencop.core.sdk.ports.audit.AuthzDenialEvent;
import com.opencop.core.sdk.ports.audit.AuthzPermission;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.audit.AuthzResource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.NotFoundException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Event-scoped authorization. Resolves objectId → eventSource via PostGisService, then checks user
 * roles on that * source via server or layer access.
 */
@Component
public class EventAuthorizationPolicy {

  private static final Logger log = LoggerFactory.getLogger(EventAuthorizationPolicy.class);

  private final PostGisService postGisService;
  private final UserSpecificManager userManager;
  private final ResourceLifecycleGuard resourceLifecycleGuard;
  private final AuthzAuditReporting authzAuditReporting;

  private static final List<AuthzPermission> EDITOR_REQUIRED_PERMISSIONS =
      List.of(AuthzPermission.SERVER_ADMIN, AuthzPermission.LAYER_EDITOR);
  private static final List<AuthzPermission> MEMBER_REQUIRED_PERMISSIONS =
      List.of(AuthzPermission.SERVER_VIEWER, AuthzPermission.LAYER_MEMBER);

  public EventAuthorizationPolicy(
      PostGisService postGisService,
      UserSpecificManager userManager,
      ResourceLifecycleGuard resourceLifecycleGuard,
      AuthzAuditReporting authzAuditReporting) {
    this.postGisService = postGisService;
    this.userManager = userManager;
    this.resourceLifecycleGuard = resourceLifecycleGuard;
    this.authzAuditReporting = authzAuditReporting;
  }

  private static final String EDITOR_DENIED_MESSAGE =
      "User is not authorized to modify attachments on this event";
  private static final String MEMBER_DENIED_MESSAGE =
      "User is not authorized to view attachments on this event";
  private static final EventAccessRequirement EDITOR_ACCESS =
      new EventAccessRequirement(
          ServerRole.ADMIN, LayerRole.EDITOR, EDITOR_REQUIRED_PERMISSIONS, EDITOR_DENIED_MESSAGE);
  private static final EventAccessRequirement MEMBER_ACCESS =
      new EventAccessRequirement(
          ServerRole.VIEWER, LayerRole.MEMBER, MEMBER_REQUIRED_PERMISSIONS, MEMBER_DENIED_MESSAGE);

  public void assertEditor(OpenCopPrincipal principal, UUID sourceId, String objectId) {
    requireEventAccess(actor(principal), sourceId, objectId, EDITOR_ACCESS);
  }

  public void assertEditor(UUID userId, UUID sourceId, String objectId) {
    assertEditor(userId, sourceId, objectId, false);
  }

  public void assertEditor(UUID userId, UUID sourceId, String objectId, boolean globalAdmin) {
    requireEventAccess(
        new EventActor(userId, null, globalAdmin), sourceId, objectId, EDITOR_ACCESS);
  }

  public void assertEditorForActiveResource(
      OpenCopPrincipal principal, UUID sourceId, String objectId, String operation) {
    assertEditor(principal, sourceId, objectId);
    resourceLifecycleGuard.assertEventResourceActive(sourceId, operation);
  }

  public void assertEditorForActiveResource(
      UUID userId, UUID sourceId, String objectId, String operation) {
    assertEditor(userId, sourceId, objectId);
    resourceLifecycleGuard.assertEventResourceActive(sourceId, operation);
  }

  public void assertMember(OpenCopPrincipal principal, UUID sourceId, String objectId) {
    requireEventAccess(actor(principal), sourceId, objectId, MEMBER_ACCESS);
  }

  public boolean canMember(OpenCopPrincipal principal, UUID sourceId, String objectId) {
    return decide(
            principal.internalUserId(),
            principal.isGlobalAdmin(),
            sourceId,
            objectId,
            ServerRole.VIEWER,
            LayerRole.MEMBER,
            MEMBER_REQUIRED_PERMISSIONS)
        .allowed();
  }

  public void assertSourceMember(OpenCopPrincipal principal, UUID sourceId) {
    assertSourceMember(principal.internalUserId(), sourceId, principal.isGlobalAdmin());
  }

  /**
   * Side-effect-free source membership for callers that degrade a response instead of denying it:
   * no existence probe and no authorization-denied audit event. Use assertMember/canMember when the
   * request itself must be refused.
   */
  public boolean canViewSource(UUID userId, boolean globalAdmin, UUID sourceId) {
    return hasSourceRole(userId, sourceId, ServerRole.VIEWER, LayerRole.MEMBER, globalAdmin);
  }

  public void assertMember(UUID userId, UUID sourceId, String objectId) {
    requireEventAccess(new EventActor(userId, null, false), sourceId, objectId, MEMBER_ACCESS);
  }

  /**
   * Single deny path: every event-scoped denial reports a rich audit event — no per-feature opt-in
   * needed. The action (request line) is derived by the reporter at emission; this policy supplies
   * what HTTP cannot say: reason codes, permission detail, and actor identity.
   */
  private void requireEventAccess(
      EventActor actor, UUID sourceId, String objectId, EventAccessRequirement requirement) {
    var decision =
        decide(
            actor.userId(),
            actor.globalAdmin(),
            sourceId,
            objectId,
            requirement.serverRole(),
            requirement.layerRole(),
            requirement.requiredPermissions());
    if (decision.allowed()) {
      return;
    }
    log.debug(
        "Event auth denied grants: userId={}, sourceId={}, required={}/{}, "
            + "userServers={}, userLayers={}",
        actor.userId(),
        sourceId,
        requirement.serverRole(),
        requirement.layerRole(),
        decision.userServerIds(),
        decision.userLayerIds());
    authzAuditReporting.reportDenied(
        AuthzDenialEvent.rich(
            actor.userId(),
            actor.email(),
            AuthzResource.event(sourceId, objectId),
            null,
            decision.denialReason(),
            decision.effectivePermissions(),
            decision.requiredPermissions()));
    throw new GlobalOpenCopException(requirement.deniedMessage(), HttpStatus.FORBIDDEN);
  }

  private void assertSourceMember(UUID userId, UUID sourceId, boolean globalAdmin) {
    if (!authorizedForSource(userId, sourceId, ServerRole.VIEWER, LayerRole.MEMBER, globalAdmin)) {
      throw new GlobalOpenCopException(
          "User is not authorized to view data for this source", HttpStatus.FORBIDDEN);
    }
  }

  private void validateEventExists(UUID sourceId, String objectId) {
    try {
      postGisService.getLatestDetails(sourceId, objectId);
    } catch (NotFoundException _) {
      log.warn("Event not found for objectId={}", objectId);
      throw new GlobalOpenCopException("Event not found", HttpStatus.NOT_FOUND);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(
          INTERNAL_ERROR.code(), e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Single authorization predicate for event-scoped checks; every caller routes here so the live
   * decision can never drift between sibling routes of the same resource family.
   */
  private EventAuthorizationDecision decide(
      UUID userId,
      boolean globalAdmin,
      UUID sourceId,
      String objectId,
      ServerRole serverRole,
      LayerRole layerRole,
      List<AuthzPermission> requiredPermissions) {
    validateEventExists(sourceId, objectId);

    if (globalAdmin) {
      return EventAuthorizationDecision.allow(requiredPermissions);
    }

    var serverRoles = userManager.getUserServers(userId);
    var layerInfos = userManager.getUserLayers(userId);

    var matchingServerRoles =
        serverRoles.stream().filter(role -> role.serverId().equals(sourceId)).toList();
    var matchingLayerInfos =
        layerInfos.stream().filter(info -> info.layerId().equals(sourceId)).toList();

    boolean hasServerRole =
        matchingServerRoles.stream().anyMatch(role -> !role.role().isLowerThan(serverRole));
    boolean hasLayerRole =
        matchingLayerInfos.stream().anyMatch(info -> !info.role().isLowerThan(layerRole));

    if (hasServerRole || hasLayerRole) {
      return EventAuthorizationDecision.allow(requiredPermissions);
    }

    var userServerIds = serverRoles.stream().map(SourceRole::serverId).toList();
    var userLayerIds = layerInfos.stream().map(LayerInfo::layerId).toList();

    if (matchingServerRoles.isEmpty() && matchingLayerInfos.isEmpty()) {
      return new EventAuthorizationDecision(
          false,
          AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
          List.of(AuthzPermission.NONE),
          requiredPermissions,
          userServerIds,
          userLayerIds);
    }

    return new EventAuthorizationDecision(
        false,
        AuthzReasonCode.INSUFFICIENT_SOURCE_OR_LAYER_ROLE,
        effectivePermissions(matchingServerRoles, matchingLayerInfos),
        requiredPermissions,
        userServerIds,
        userLayerIds);
  }

  private static List<AuthzPermission> effectivePermissions(
      List<SourceRole> serverRoles, List<LayerInfo> layerInfos) {
    var serverPermissions =
        serverRoles.stream().map(SourceRole::role).map(EventAuthorizationPolicy::fromServerRole);
    var layerPermissions =
        layerInfos.stream().map(LayerInfo::role).map(EventAuthorizationPolicy::fromLayerRole);
    return Stream.concat(serverPermissions, layerPermissions)
        .distinct()
        .sorted(Comparator.comparingInt(AuthzPermission::ordinal))
        .toList();
  }

  // Permission values are audit vocabulary labels, not user or route-derived text.
  private static AuthzPermission fromServerRole(ServerRole role) {
    return switch (role) {
      case OWNER -> AuthzPermission.SERVER_OWNER;
      case ADMIN -> AuthzPermission.SERVER_ADMIN;
      case VIEWER -> AuthzPermission.SERVER_VIEWER;
    };
  }

  private static AuthzPermission fromLayerRole(LayerRole role) {
    return switch (role) {
      case OWNER -> AuthzPermission.LAYER_OWNER;
      case ADMIN -> AuthzPermission.LAYER_ADMIN;
      case EDITOR -> AuthzPermission.LAYER_EDITOR;
      case MEMBER -> AuthzPermission.LAYER_MEMBER;
    };
  }

  private static String actorEmail(OpenCopPrincipal principal) {
    return principal.email();
  }

  private static EventActor actor(OpenCopPrincipal principal) {
    return new EventActor(
        principal.internalUserId(), actorEmail(principal), principal.isGlobalAdmin());
  }

  private boolean authorizedForSource(
      UUID userId, UUID sourceId, ServerRole serverRole, LayerRole layerRole, boolean globalAdmin) {
    var decision = resolveSourceRole(userId, sourceId, serverRole, layerRole, globalAdmin);
    if (!decision.allowed()) {
      log.warn(
          "Source auth denied: userId={}, sourceId={}, required={}/{}, userServers={}, userLayers={}",
          userId,
          sourceId,
          serverRole,
          layerRole,
          decision.userServerIds(),
          decision.userLayerIds());
    }
    return decision.allowed();
  }

  private boolean hasSourceRole(
      UUID userId, UUID sourceId, ServerRole serverRole, LayerRole layerRole, boolean globalAdmin) {
    return resolveSourceRole(userId, sourceId, serverRole, layerRole, globalAdmin).allowed();
  }

  /** One role read serves both the silent predicate and the logged guard. */
  private SourceRoleDecision resolveSourceRole(
      UUID userId, UUID sourceId, ServerRole serverRole, LayerRole layerRole, boolean globalAdmin) {
    if (globalAdmin) {
      return new SourceRoleDecision(true, List.of(), List.of());
    }
    var serverRoles = userManager.getUserServers(userId);
    var layerInfos = userManager.getUserLayers(userId);
    boolean hasServerRole =
        serverRoles.stream()
            .anyMatch(
                role -> role.serverId().equals(sourceId) && !role.role().isLowerThan(serverRole));
    boolean hasLayerRole =
        layerInfos.stream()
            .anyMatch(
                info -> info.layerId().equals(sourceId) && !info.role().isLowerThan(layerRole));
    return new SourceRoleDecision(
        hasServerRole || hasLayerRole,
        serverRoles.stream().map(SourceRole::serverId).toList(),
        layerInfos.stream().map(LayerInfo::layerId).toList());
  }

  private record SourceRoleDecision(
      boolean allowed, List<UUID> userServerIds, List<UUID> userLayerIds) {}

  private record EventActor(UUID userId, String email, boolean globalAdmin) {}

  private record EventAccessRequirement(
      ServerRole serverRole,
      LayerRole layerRole,
      List<AuthzPermission> requiredPermissions,
      String deniedMessage) {}

  private record EventAuthorizationDecision(
      boolean allowed,
      AuthzReasonCode denialReason,
      List<AuthzPermission> effectivePermissions,
      List<AuthzPermission> requiredPermissions,
      List<UUID> userServerIds,
      List<UUID> userLayerIds) {

    static EventAuthorizationDecision allow(List<AuthzPermission> requiredPermissions) {
      return new EventAuthorizationDecision(
          true, null, List.of(), requiredPermissions, List.of(), List.of());
    }
  }
}
