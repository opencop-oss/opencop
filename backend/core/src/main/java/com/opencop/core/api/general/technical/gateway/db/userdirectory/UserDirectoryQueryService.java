// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.userdirectory;

import com.opencop.core.api.general.business.model.userdirectory.UserDirectoryEntry;
import com.opencop.core.api.general.ports.technical.gateway.UserDirectoryReader;
import com.opencop.core.sdk.model.iam.userdirectory.UserDirectoryReadFilter;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.gateway.db.Pageables;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDirectoryQueryService implements UserDirectoryReader {

  private final UserDirectoryEntryRepository repository;

  UserDirectoryQueryService(UserDirectoryEntryRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<UserDirectoryEntry> getPage(
      UserDirectoryReadFilter filter, Pageable pageable) {
    Pageable ordered =
        Pageables.withStableOrder(
            UserDirectorySort.validate(pageable),
            Sort.by(Sort.Order.asc("name"), Sort.Order.asc("type"), Sort.Order.asc("id")),
            "type",
            "id");
    Page<UserDirectoryEntryEntity> page =
        repository.findAll(UserDirectorySpecifications.from(filter), ordered);
    return new PageResponse<>(
        page.getContent().stream().map(UserDirectoryQueryService::toModel).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  private static UserDirectoryEntry toModel(UserDirectoryEntryEntity entity) {
    return new UserDirectoryEntry(
        entity.getId(),
        entity.getType(),
        entity.getName(),
        entity.getFirstName(),
        entity.getLastName(),
        entity.getEmail(),
        entity.getDisplayName(),
        entity.getAdmin(),
        entity.isActive(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
