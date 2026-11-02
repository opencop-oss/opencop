// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.userdirectory;

import com.opencop.core.api.general.business.model.userdirectory.UserDirectoryEntry;
import com.opencop.core.api.general.ports.technical.gateway.UserDirectoryReader;
import com.opencop.core.api.general.technical.endpoint.filter.FilterParams;
import com.opencop.core.sdk.model.iam.userdirectory.UserDirectoryFilterField;
import com.opencop.core.sdk.model.iam.userdirectory.UserDirectoryReadFilter;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the admin Users grid: a filtered, sorted, paginated view of the combined user/group
 * directory. Resolves paging/filtering/sorting server-side so the grid loads a bounded page rather
 * than the whole user directory.
 */
@RestController
public class UserDirectoryEndpoint {

  private final UserDirectoryReader userDirectoryReader;

  UserDirectoryEndpoint(UserDirectoryReader userDirectoryReader) {
    this.userDirectoryReader = userDirectoryReader;
  }

  /**
   * @param timezone IANA zone the grid renders timestamps in, used to resolve the day boundaries of
   *     date-only criteria. Optional; omitting it resolves them in UTC.
   * @param request source of the repeated {@code filter} params, read directly rather than bound --
   *     see {@link FilterParams#from}
   */
  @PreAuthorize("hasRole('admin')")
  @GetMapping("/v1/admin/user-directory")
  public ResponseEntity<PageResponse<UserDirectoryEntry>> getUserDirectory(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String timezone,
      @PageableDefault(size = 25) Pageable pageable,
      HttpServletRequest request) {
    return ResponseEntity.ok(
        userDirectoryReader.getPage(
            new UserDirectoryReadFilter(
                search,
                FilterParams.from(request, UserDirectoryFilterField::fromWire),
                FilterParams.zoneOf(timezone)),
            pageable));
  }
}
