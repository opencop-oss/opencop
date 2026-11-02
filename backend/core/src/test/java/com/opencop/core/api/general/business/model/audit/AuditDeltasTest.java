// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AuditDeltasTest {

  @Test
  void ids_keepsTheExactCountButCapsTheListedIds() {
    var many =
        IntStream.range(0, AuditDeltas.MAXIMUM_IDS + 5).mapToObj(_ -> UUID.randomUUID()).toList();

    var entry = AuditDeltas.ids(many);

    assertThat(entry).containsEntry("count", AuditDeltas.MAXIMUM_IDS + 5);
    assertThat(entry).containsEntry("truncated", true);
    assertThat((List<?>) entry.get("ids")).hasSize(AuditDeltas.MAXIMUM_IDS);
  }

  @Test
  void ids_whenEmpty_isAbsentRatherThanAnEmptyObject() {
    assertThat(AuditDeltas.ids(List.of())).isNull();
  }

  @Test
  void names_capsTheListAndEachNameOfClientSuppliedInput() {
    var longName = "x".repeat(AuditDeltas.MAXIMUM_NAME_LENGTH + 10);
    var names = IntStream.range(0, AuditDeltas.MAXIMUM_IDS + 1).mapToObj(_ -> longName).toList();

    var entry = AuditDeltas.names(names);

    assertThat(entry).containsEntry("count", AuditDeltas.MAXIMUM_IDS + 1);
    assertThat(entry).containsEntry("truncated", true);
    assertThat((List<?>) entry.get("names"))
        .hasSize(AuditDeltas.MAXIMUM_IDS)
        .allSatisfy(name -> assertThat((String) name).hasSize(AuditDeltas.MAXIMUM_NAME_LENGTH));
  }

  @Test
  void truncate_neverSplitsASurrogatePair() {
    var emoji = "😀";
    var value = "a" + emoji.repeat(300);

    var truncated = AuditDeltas.truncate(value);

    assertThat(truncated.codePointCount(0, truncated.length()))
        .isEqualTo(AuditDeltas.MAXIMUM_NAME_LENGTH);
    assertThat(Character.isLowSurrogate(truncated.charAt(truncated.length() - 1))).isTrue();
  }
}
