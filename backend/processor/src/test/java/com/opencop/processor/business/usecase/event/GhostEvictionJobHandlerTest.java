// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.opencop.core.sdk.model.job.GhostEvictionJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GhostEvictionJobHandlerTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440201");
  private static final String OBJECT_ID = "obj-abc";
  private static final long DELETED_AT_MS = 12345L;

  @Test
  void handle_delegatesToCacheGatewayWithJobParameters() {
    GeoCacheGateway cacheGateway = mock();
    var handler = new GhostEvictionJobHandler(cacheGateway);
    var job =
        new GhostEvictionJobSubmission(
            UUID.randomUUID(),
            JobIssuer.system("ghost-eviction"),
            SOURCE_ID,
            OBJECT_ID,
            DELETED_AT_MS);

    handler.handle(job);

    verify(cacheGateway).evictObject(SOURCE_ID, OBJECT_ID, DELETED_AT_MS);
  }

  @Test
  void jobType_returnsGhostEvictionJobSubmission() {
    GeoCacheGateway cacheGateway = mock();
    var handler = new GhostEvictionJobHandler(cacheGateway);

    assertThat(handler.jobType()).isEqualTo(GhostEvictionJobSubmission.class);
  }
}
