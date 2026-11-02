// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.component.support.TestActors;
import com.opencop.core.api.general.business.model.audit.AuditChange;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.sdk.technical.gateway.db.audit.AuditHistoryEntity;
import com.opencop.core.sdk.technical.gateway.db.audit.AuditHistoryJpaRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

/**
 * The stored payload always fits the column, whatever a caller hands over.
 *
 * <p>This is the one bound that cannot be left to the database: the write joins the caller's
 * transaction, so a payload the CHECK rejects takes the user's edit down with it. Without this a
 * resource whose description is already very long could never be patched again, because the diff
 * carries both the old value and the new one.
 */
class JpaAuditHistoryStoreBoundsTest {

  /** Mirrors V61's {@code audit_history_change_maximum} CHECK. */
  private static final int COLUMN_MAXIMUM_BYTES = 65536;

  private static final UUID ENTITY_ID = UUID.fromString("550e8400-e29b-41d4-a716-4466554470a1");
  private static final UUID ACTOR_ID = UUID.fromString("550e8400-e29b-41d4-a716-4466554470a2");

  private AuditHistoryJpaRepository repository;
  private JpaAuditHistoryStore store;

  @BeforeEach
  void setUp() {
    repository = mock(AuditHistoryJpaRepository.class);
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    store = new JpaAuditHistoryStore(repository, new ObjectMapper());
  }

  @Test
  void aPayloadInsideTheBoundIsStoredUntouched() {
    store.record(event(moved("name", "a", "b")));

    assertThat(storedChange())
        .isEqualTo("{\"changed\":[{\"field\":\"name\",\"before\":\"a\",\"after\":\"b\"}]}");
  }

  /** The patch that used to be impossible: a very long description replaced by another. */
  @Test
  void aPatchBetweenTwoHugeValuesStillFitsTheColumn() {
    var change = moved("description", "x".repeat(40_000), "y".repeat(40_000));

    store.record(event(change));

    var stored = storedChange();
    assertThat(utf8Bytes(stored)).isLessThanOrEqualTo(COLUMN_MAXIMUM_BYTES);
    assertThat(stored).contains("\"valueTruncated\":true").contains("\"field\":\"description\"");
  }

  /** Multi-byte characters count against the byte cap, not the character count. */
  @Test
  void aPayloadOfMultiByteCharactersIsBoundedByBytes() {
    store.record(event(moved("name", "", "字".repeat(30_000))));

    assertThat(utf8Bytes(storedChange())).isLessThanOrEqualTo(COLUMN_MAXIMUM_BYTES);
  }

  /**
   * When cutting the values is not enough — here a change listing hundreds of fields — the payload
   * gives up its detail and keeps the counts, which is the part it can still answer honestly.
   */
  @Test
  void aPayloadThatIsStillTooLargeAfterCuttingKeepsOnlyCounts() {
    var change = AuditChange.builder();
    for (int i = 0; i < 200; i++) {
      change.field("f" + i, "a".repeat(600), "b".repeat(600));
    }

    store.record(event(change.build()));

    var stored = storedChange();
    assertThat(utf8Bytes(stored)).isLessThanOrEqualTo(COLUMN_MAXIMUM_BYTES);
    assertThat(stored).contains("\"payloadTruncated\":true").contains("\"changedCount\":200");
  }

  /** A detail is free text too, so one oversized value is cut like any other. */
  @Test
  void anOversizedDetailValueIsCut() {
    var change = AuditChange.builder().detail("filename", "z".repeat(70_000)).build();

    store.record(event(change));

    assertThat(utf8Bytes(storedChange())).isLessThanOrEqualTo(COLUMN_MAXIMUM_BYTES);
    assertThat(storedChange()).contains("\"valueTruncated\":true");
  }

  /** The bounded list shapes a reader depends on, written exactly once from the typed change. */
  @Test
  void listDetailsKeepTheirCountAndTruncationMarker() {
    var names = java.util.stream.IntStream.range(0, 120).mapToObj("f%d"::formatted).toList();
    var change =
        AuditChange.builder()
            .nameList("fieldsSupplied", names)
            .idList("mergedFrom", List.of(ENTITY_ID))
            .detail("valuesOmitted", true)
            .build();

    store.record(event(change));

    assertThat(storedChange())
        .contains("\"fieldsSupplied\":{\"count\":120,")
        .contains("\"truncated\":true")
        .contains(
            "\"mergedFrom\":{\"count\":1,\"ids\":[\"" + ENTITY_ID + "\"],\"truncated\":false}")
        .contains("\"valuesOmitted\":true");
  }

  /**
   * The CHECK measures `octet_length(change::text)` on a jsonb column, and jsonb's text form adds a
   * space after every `:` and `,`. This description is 65,463 characters: 65,534 bytes compact,
   * which the old compact-only budget accepted, and 65,540 as jsonb text, which the CHECK then
   * rejected — taking the caller's edit down with it.
   */
  @Test
  void aPayloadThatOnlyFitsWhenCompactIsStillTruncated() {
    var change = moved("description", "head-insert", "x".repeat(65_463));

    store.record(event(change));

    var stored = storedChange();
    assertThat(utf8Bytes(stored)).isLessThan(COLUMN_MAXIMUM_BYTES);
    assertThat(jsonbTextBytes(stored)).isLessThanOrEqualTo(COLUMN_MAXIMUM_BYTES);
    assertThat(stored).contains("\"valueTruncated\":true");
  }

  /** A value cut to fit must not end in half of a surrogate pair. */
  @Test
  void anAbbreviatedValueKeepsItsLastCharacterWhole() {
    // U+20000 is two UTF-16 units, so a cut at 512 units would split it.
    var change = moved("description", "", "a".repeat(511) + "\uD840\uDC00" + "x".repeat(70_000));

    store.record(event(change));

    var stored = storedChange();
    assertThat(stored).contains("\uD840\uDC00");
    // codePoints() joins a valid pair, so anything left in the surrogate range is a lone half.
    assertThat(stored.codePoints().noneMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF)).isTrue();
    assertThat(jsonbTextBytes(stored)).isLessThanOrEqualTo(COLUMN_MAXIMUM_BYTES);
  }

  /**
   * What PostgreSQL will measure, derived independently of the writer: the payloads here contain no
   * `:` or `,` inside their string values, so a textual substitution is a faithful stand-in for
   * jsonb's canonical spacing.
   */
  private static int jsonbTextBytes(String compactJson) {
    return utf8Bytes(compactJson.replace("\":", "\": ").replace(",", ", "));
  }

  private static AuditChange moved(String field, String before, String after) {
    return AuditChange.builder().field(field, before, after).build();
  }

  private AuditEvent event(AuditChange change) {
    return new AuditEvent(
        EntityType.LAYER,
        ENTITY_ID,
        "A layer",
        Operation.PATCH,
        TestActors.of(ACTOR_ID),
        change,
        AuditEvent.CURRENT_CHANGE_VERSION);
  }

  private String storedChange() {
    var captor = ArgumentCaptor.forClass(AuditHistoryEntity.class);
    verify(repository).saveAndFlush(captor.capture());
    return captor.getValue().getChange();
  }

  private static int utf8Bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }
}
