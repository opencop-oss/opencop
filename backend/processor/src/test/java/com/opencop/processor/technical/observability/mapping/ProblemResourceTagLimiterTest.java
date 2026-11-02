// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.observability.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.processor.business.model.mapping.MappingResourceKind;
import com.opencop.processor.business.model.mapping.MappingResourceRef;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ProblemResourceTagLimiterTest {

  @Test
  void admitsPermanentSharedCapAndRoutesLaterResourcesToOther() {
    var limiter = new ProblemResourceTagLimiter(100);
    var resources = IntStream.range(0, 101).mapToObj(_ -> resource(UUID.randomUUID())).toList();

    resources.subList(0, 100).forEach(ref -> assertThat(limiter.admit(ref).overflow()).isFalse());
    var overflow = limiter.admit(resources.get(100));

    assertThat(overflow.resourceId()).isEqualTo("other");
    assertThat(overflow.overflow()).isTrue();
    assertThat(limiter.admit(resources.getFirst()).resourceId())
        .isEqualTo(resources.getFirst().id().toString());
    assertThat(limiter.admittedCount()).isEqualTo(100);
  }

  @Test
  void concurrentAdmissionNeverExceedsCap() {
    var limiter = new ProblemResourceTagLimiter(10);
    var admitted = new ConcurrentLinkedQueue<String>();

    IntStream.range(0, 1_000)
        .parallel()
        .mapToObj(_ -> limiter.admit(resource(UUID.randomUUID())))
        .filter(tag -> !tag.overflow())
        .map(ProblemResourceTagLimiter.AdmittedResourceTag::resourceId)
        .forEach(admitted::add);

    assertThat(admitted).hasSize(10);
    assertThat(limiter.admittedCount()).isEqualTo(10);
  }

  private static MappingResourceRef resource(UUID id) {
    return new MappingResourceRef(MappingResourceKind.DATA_SOURCE, id);
  }
}
