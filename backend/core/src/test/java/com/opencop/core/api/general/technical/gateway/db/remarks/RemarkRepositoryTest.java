// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.remarks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.technical.model.opencop.PersistRemarkRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RemarkRepositoryTest {

  @Mock private RemarkJpaRepository repo;
  @Captor private ArgumentCaptor<RemarkEntity> entityCaptor;

  private RemarkRepository repository;

  @BeforeEach
  void setUp() {
    repository = new RemarkRepository(repo);
  }

  @Test
  void addRemark_persistsSourceScopedRemark() {
    var sourceId = UUID.randomUUID();
    var userId = UUID.randomUUID();
    var request = new PersistRemarkRequest(sourceId, "event-123", "remark body", userId);

    repository.addRemark(request);

    verify(repo).save(entityCaptor.capture());
    var entity = entityCaptor.getValue();
    assertEquals(sourceId, entity.getSourceId());
    assertEquals("event-123", entity.getobjectId());
    assertEquals("remark body", entity.getContent());
    assertEquals(userId, entity.getUserId());
  }

  @Test
  void retrieveRemarks_filtersBySourceAndObjectId() {
    var sourceId = UUID.randomUUID();
    when(repo.findBySourceIdAndObjectIdOrderByCreatedAtDesc(sourceId, "event-123"))
        .thenReturn(List.of());

    var remarks = repository.retrieveRemarks(sourceId, "event-123");

    assertEquals(List.of(), remarks);
    verify(repo).findBySourceIdAndObjectIdOrderByCreatedAtDesc(sourceId, "event-123");
  }
}
