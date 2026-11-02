// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.technical.gateway;

import com.opencop.core.api.general.business.model.userdirectory.UserDirectoryEntry;
import com.opencop.core.sdk.model.iam.userdirectory.UserDirectoryReadFilter;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import org.springframework.data.domain.Pageable;

/** Reads a filtered, sorted page of the combined user/group directory, resolved server-side. */
public interface UserDirectoryReader {

  PageResponse<UserDirectoryEntry> getPage(UserDirectoryReadFilter filter, Pageable pageable);
}
