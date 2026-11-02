// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.map;

import com.opencop.core.api.general.business.usecase.map.MapTileGrantUsecase;
import com.opencop.core.api.general.business.usecase.map.MapTileManagementUsecase;
import com.opencop.core.sdk.business.model.map.tile.MapTile;
import com.opencop.core.sdk.business.model.map.tile.access.MapTileMember;
import com.opencop.core.sdk.business.model.map.tile.access.MapTileUserGroupGrant;
import com.opencop.core.sdk.business.model.map.tile.create.MapTileCreate;
import com.opencop.core.sdk.business.model.map.tile.patch.MapTilePatch;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MapTileEndpoint {

  private final MapTileManagementUsecase usecase;
  private final MapTileGrantUsecase grants;

  public MapTileEndpoint(MapTileManagementUsecase usecase, MapTileGrantUsecase grants) {
    this.usecase = usecase;
    this.grants = grants;
  }

  @GetMapping("/v1/map/tiles/primary")
  public MapTile getPrimary(@AuthenticationPrincipal OpenCopPrincipal authentication) {
    return usecase.getPrimary(authentication);
  }

  /**
   * Requires VIEWER because the response carries the tile's decrypted token. An ungated read would
   * let anyone bypass access control by guessing an id.
   */
  @GetMapping("/v1/map/tiles/{id}")
  public MapTile getOne(
      @PathVariable UUID id, @AuthenticationPrincipal OpenCopPrincipal authentication) {
    return usecase.getOne(id, authentication);
  }

  @GetMapping("/v1/map/tiles")
  public List<MapTile> getAll(@AuthenticationPrincipal OpenCopPrincipal authentication) {
    return usecase.getAll(authentication);
  }

  /**
   * The full catalog, including disabled and restricted tiles. Separate from {@code GET
   * /v1/map/tiles} so the admin grid is never narrowed by that route's filtering.
   */
  @PreAuthorize("hasRole('admin')")
  @GetMapping("/v1/admin/map/tiles")
  public List<MapTile> getAllForAdmin() {
    return usecase.getAllForAdmin();
  }

  @PreAuthorize("hasRole('admin')")
  @PostMapping("/v1/admin/map/tiles")
  public ResponseEntity<MapTile> create(
      @RequestBody MapTileCreate body, @AuthenticationPrincipal OpenCopPrincipal authentication) {
    var created = usecase.create(body, authentication);
    return ResponseEntity.created(URI.create("/v1/admin/map/tiles/" + created.getId()))
        .body(created);
  }

  /**
   * Resource-scoped, so it lives under /v1/map/tiles rather than /v1/admin. Everything under
   * /v1/admin/** is gated on ROLE_admin by the filter chain before any controller runs, which would
   * make the tile ADMIN role unreachable. The use case authorizes per tile instead.
   */
  @PatchMapping("/v1/map/tiles/{id}")
  public MapTile patch(
      @PathVariable UUID id,
      @RequestBody MapTilePatch body,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    return usecase.patch(id, body, authentication);
  }

  /** Resource-scoped for the same reason as {@link #patch}: DELETE requires the tile OWNER role. */
  @DeleteMapping("/v1/map/tiles/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @AuthenticationPrincipal OpenCopPrincipal authentication) {
    usecase.delete(id, authentication);
    return ResponseEntity.noContent().build();
  }

  /** Sets which tile is primary. */
  @PreAuthorize("hasRole('admin')")
  @PutMapping("/v1/admin/map/tiles/{id}/primary")
  public ResponseEntity<Void> setPrimary(@PathVariable UUID id) {
    usecase.setPrimary(id);
    return ResponseEntity.noContent().build();
  }

  // ---------------------------------------------------------------------------
  // Grants
  //
  // Reading needs VIEWER: anyone who can use the tile may see who else can.
  // Mutating needs ADMIN, except anything involving OWNER, which needs OWNER.
  // ---------------------------------------------------------------------------

  @GetMapping("/v1/map/tiles/{id}/members")
  public List<MapTileMember> getMembers(
      @PathVariable UUID id, @AuthenticationPrincipal OpenCopPrincipal authentication) {
    return grants.getMembers(id, authentication);
  }

  @PostMapping("/v1/map/tiles/{id}/members")
  public ResponseEntity<Void> assignMembers(
      @PathVariable UUID id,
      @RequestBody Set<UUID> request,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    grants.assignMembers(id, request, authentication);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/v1/map/tiles/{id}/members")
  public ResponseEntity<Void> revokeMembers(
      @PathVariable UUID id,
      @RequestBody Set<UUID> userIds,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    grants.revokeMembers(id, userIds, authentication);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/v1/map/tiles/{id}/user-groups")
  public List<MapTileUserGroupGrant> getUserGroups(
      @PathVariable UUID id, @AuthenticationPrincipal OpenCopPrincipal authentication) {
    return grants.getUserGroups(id, authentication);
  }

  @PostMapping("/v1/map/tiles/{id}/user-groups")
  public ResponseEntity<Void> assignUserGroups(
      @PathVariable UUID id,
      @RequestBody Set<UUID> request,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    grants.assignUserGroups(id, request, authentication);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/v1/map/tiles/{id}/user-groups")
  public ResponseEntity<Void> revokeUserGroups(
      @PathVariable UUID id,
      @RequestBody Set<UUID> userGroupIds,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    grants.revokeUserGroups(id, userGroupIds, authentication);
    return ResponseEntity.noContent().build();
  }
}
