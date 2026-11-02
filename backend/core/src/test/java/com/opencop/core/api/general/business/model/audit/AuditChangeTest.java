// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What a described change keeps, and what it declines to say. */
class AuditChangeTest {

  @Test
  void moved_recordsOnlyTheFieldThatActuallyMoved() {
    var change =
        AuditChange.builder().moved("name", "same", "same").moved("color", "red", "blue").build();

    assertThat(change.changed())
        .containsExactly(new AuditChange.FieldChange("color", "red", "blue"));
  }

  @Test
  void field_recordsACreationEvenThoughItHasNoPreviousValue() {
    var change = AuditChange.builder().field("name", null, "Recon North").build();

    assertThat(change.changed())
        .containsExactly(new AuditChange.FieldChange("name", null, "Recon North"));
  }

  /**
   * Ids and enums are stored by their stable text, never by a {@code toString} a refactor moves.
   */
  @Test
  void valuesAreNormalisedToTheirStableText() {
    var id = UUID.randomUUID();

    var change =
        AuditChange.builder()
            .field("mapperId", null, id)
            .detail("operation", Operation.PATCH)
            .build();

    assertThat(change.changed().getFirst().after()).isEqualTo(id.toString());
    assertThat(change.details()).containsEntry("operation", Operation.PATCH.name());
  }

  @Test
  void detail_dropsAnAbsentFact() {
    var change = AuditChange.builder().detail("format", null).detail("subjectCount", 2).build();

    assertThat(change.details()).containsOnlyKeys("subjectCount");
  }

  @Test
  void anEmptyDeltaIsTreatedAsNoDeltaAtAll() {
    var change =
        AuditChange.builder().delta(new AuditDeltas.Delta<>(Map.of(), Set.of(), Map.of())).build();

    assertThat(change.delta()).isNull();
  }

  @Test
  void isEmpty_reportsAChangeThatWouldSayNothing() {
    assertThat(AuditChange.builder().isEmpty()).isTrue();
    assertThat(AuditChange.builder().moved("name", "a", "a").isEmpty()).isTrue();
    assertThat(AuditChange.builder().detail("format", "CSV").isEmpty()).isFalse();
  }

  @Test
  void theRecordIsDefensivelyCopied() {
    var changed = new java.util.ArrayList<AuditChange.FieldChange>();
    changed.add(new AuditChange.FieldChange("name", "a", "b"));

    var change = new AuditChange(changed, null, Map.of());
    changed.clear();

    assertThat(change.changed()).hasSize(1);
  }

  @Test
  void aNullComponentReadsAsEmptyRatherThanThrowing() {
    var change = new AuditChange(null, null, null);

    assertThat(change.changed()).isEmpty();
    assertThat(change.details()).isEmpty();
    assertThat(change.delta()).isNull();
  }

  @Test
  void ofDelta_carriesTheDeltaAndNothingElse() {
    var id = UUID.randomUUID();
    var delta = new AuditDeltas.Delta<>(Map.of(), Set.of(id), Map.<UUID, Object>of());

    var change = AuditChange.ofDelta(delta);

    assertThat(change.changed()).isEmpty();
    assertThat(change.details()).isEmpty();
    assertThat(change.delta().removed()).containsExactly(id);
  }

  @Test
  void listDetailsKeepTheirValuesForTheWriterToBound() {
    var id = UUID.randomUUID();

    var change =
        AuditChange.builder()
            .nameList("fieldsSupplied", List.of("host"))
            .idList("mergedFrom", List.of(id))
            .build();

    assertThat(change.details())
        .containsEntry("fieldsSupplied", new AuditChange.NameList(List.of("host")))
        .containsEntry("mergedFrom", new AuditChange.IdList(List.of(id)));
  }
}
