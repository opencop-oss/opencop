// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.user;

import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.iam.ports.usecase.identity.UserManager;
import com.opencop.core.sdk.model.iam.user.UserAdminPatchRequest;
import com.opencop.core.sdk.model.iam.user.UserReadFilter;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsersEndpoint {

  private final UserManager<InternalUser> userManager;

  UsersEndpoint(final UserManager<InternalUser> service) {
    this.userManager = service;
  }

  @GetMapping("/v1/users/{userId}")
  public ResponseEntity<UserResponse> getUser(@PathVariable UUID userId) {
    return ResponseEntity.ok(UserResponse.from(this.userManager.get(userId)));
  }

  /** Paged, searchable users for the drawer assign pickers — minimal picker projection (R-01). */
  @GetMapping("/v1/users/search")
  public ResponseEntity<PageResponse<UserSearchResult>> searchUsers(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) Boolean admin,
      @PageableDefault(size = 25) Pageable pageable) {
    PageResponse<InternalUser> page =
        this.userManager.listUsers(new UserReadFilter(search, active, admin), pageable);
    return ResponseEntity.ok(
        new PageResponse<>(
            page.content().stream().map(UserSearchResult::from).toList(),
            page.page(),
            page.size(),
            page.totalElements(),
            page.totalPages()));
  }

  @PreAuthorize("hasRole('admin')")
  @PatchMapping("/v1/admin/users/{userId}")
  public ResponseEntity<Void> setAdmin(
      @PathVariable UUID userId, @RequestBody UserAdminPatchRequest request) {
    this.userManager.updateAdminRole(userId, request.isAdmin());
    return ResponseEntity.ok().build();
  }
}
