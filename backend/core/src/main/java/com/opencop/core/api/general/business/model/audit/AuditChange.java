// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.audit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * What one recorded action changed, as a value rather than as JSON text.
 *
 * <p>A projection describes the change; the store decides how it is written, bounds it and adds its
 * {@code nameTruncated} marker. Keeping those apart is what stops a business class needing an
 * {@code ObjectMapper}, and stops the store re-parsing a string it was handed in order to add one
 * field to it.
 *
 * <p>The shape is fixed because readers depend on it: the fields that moved, an optional delta of
 * ids, and whatever facts the operation records about itself. Only {@code details} varies between
 * operations, which is why a new operation needs no new type here.
 *
 * @param changed the fields this action moved, empty when it moved none
 * @param delta the ids it granted, revoked or re-roled, null when it touched no id set
 * @param details the operation's own facts — scalars and name lists only, never nested structures,
 *     because a reader renders them generically and cannot guess at a shape it does not know
 */
public record AuditChange(
    List<FieldChange> changed, AuditDeltas.Delta<?> delta, Map<String, Object> details) {

  public AuditChange {
    changed = changed == null ? List.of() : List.copyOf(changed);
    details = details == null ? Map.of() : Map.copyOf(details);
  }

  /** One field's movement, with each value already reduced to the text that is stored. */
  public record FieldChange(String field, Object before, Object after) {
    public FieldChange {
      Objects.requireNonNull(field, "field");
    }
  }

  /**
   * A detail that names ids, kept as a list so the writer can cap it while keeping its exact size.
   */
  public record IdList(Collection<UUID> ids) {}

  /** The same for client-supplied names, whose number and length are both untrusted. */
  public record NameList(Collection<String> names) {}

  /**
   * An export, recorded when it is requested rather than when the file is fetched.
   *
   * <p>Shared by both ledgers an export can touch: the same act is recorded against layers and data
   * sources alike, and a reader should not meet two shapes for it.
   */
  public static AuditChange exported(String format, UUID exportId, int subjectCount) {
    return builder()
        .detail("format", format)
        .detail("exportId", exportId)
        .detail("subjectCount", subjectCount)
        .build();
  }

  /** An action that moved a set of ids and their roles, and nothing else. */
  public static AuditChange ofDelta(AuditDeltas.Delta<?> delta) {
    return new AuditChange(List.of(), delta, Map.of());
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Collects a change without the caller having to decide which components are absent. */
  public static final class Builder {

    private final List<FieldChange> changed = new ArrayList<>();
    private final Map<String, Object> details = new LinkedHashMap<>();
    private AuditDeltas.Delta<?> delta;

    private Builder() {}

    /** Records a field only when it actually moved. */
    public Builder moved(String field, Object before, Object after) {
      if (!Objects.equals(before, after)) {
        changed.add(new FieldChange(field, scalar(before), scalar(after)));
      }
      return this;
    }

    /** Records a field unconditionally, for a creation where there is no previous value. */
    public Builder field(String field, Object before, Object after) {
      changed.add(new FieldChange(field, scalar(before), scalar(after)));
      return this;
    }

    /** Records one of the operation's own facts, dropping it when absent. */
    public Builder detail(String key, Object value) {
      if (value != null) {
        details.put(key, scalar(value));
      }
      return this;
    }

    /** Records a detail that names ids, so the writer caps it but keeps the exact count. */
    public Builder idList(String key, Collection<UUID> ids) {
      return detail(key, ids == null ? null : new IdList(ids));
    }

    /** The same for client-supplied names. */
    public Builder nameList(String key, Collection<String> names) {
      return detail(key, names == null ? null : new NameList(names));
    }

    public Builder delta(AuditDeltas.Delta<?> delta) {
      this.delta = delta == null || delta.isEmpty() ? null : delta;
      return this;
    }

    /** Whether this change would say nothing, so the caller can decline to record it. */
    public boolean isEmpty() {
      return changed.isEmpty() && details.isEmpty() && delta == null;
    }

    public AuditChange build() {
      return new AuditChange(changed, delta, details);
    }
  }

  /**
   * Enums and ids are stored by their stable text, never by a {@code toString} that a refactor
   * could quietly change under rows already written.
   */
  private static Object scalar(Object value) {
    return switch (value) {
      case null -> null;
      case UUID id -> id.toString();
      case Enum<?> constant -> constant.name();
      default -> value;
    };
  }
}
