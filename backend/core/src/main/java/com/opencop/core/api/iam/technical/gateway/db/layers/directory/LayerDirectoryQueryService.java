// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.layers.directory;

import com.opencop.core.api.iam.ports.gateway.repository.LayerDirectoryReader;
import com.opencop.core.sdk.model.iam.layer.LayerDirectoryEntry;
import com.opencop.core.sdk.model.iam.layer.LayerDirectoryReadFilter;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.gateway.db.Pageables;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LayerDirectoryQueryService implements LayerDirectoryReader {

  private final LayerDirectoryEntryRepository repo;

  LayerDirectoryQueryService(LayerDirectoryEntryRepository repo) {
    this.repo = repo;
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<LayerDirectoryEntry> getPage(
      LayerDirectoryReadFilter filter, Pageable pageable) {
    Pageable ordered =
        Pageables.withStableOrder(
            LayerDirectorySort.translate(pageable),
            Sort.by(Sort.Order.asc("name"), Sort.Order.asc("type"), Sort.Order.asc("id")),
            "type",
            "id");
    Page<LayerDirectoryEntity> page =
        repo.findAll(LayerDirectorySpecifications.from(filter), ordered);
    return new PageResponse<>(
        page.getContent().stream().map(LayerDirectoryQueryService::toModel).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  private static LayerDirectoryEntry toModel(LayerDirectoryEntity entity) {
    return new LayerDirectoryEntry(
        entity.getId(),
        entity.getType(),
        entity.getName(),
        entity.getAccessScope(),
        "ARCHIVED".equals(entity.getLifecycleState()),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getLastEventAt());
  }
}
