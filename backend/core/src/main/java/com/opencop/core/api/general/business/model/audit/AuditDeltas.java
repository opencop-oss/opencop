// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.audit;

import com.opencop.core.api.general.business.model.BoundedText;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The bounded lists a change payload is built from, and the role-set diff every ledger shares, so
 * the stored shape cannot fork between resources.
 *
 * <p>A membership change can name any number of users, and the whole list in one row would put an
 * unbounded payload in the ledger. Each list is therefore capped while its exact size is kept, so a
 * row still answers "how many" truthfully even when it cannot answer "which ones" in full.
 */
public final class AuditDeltas {

  /** Ids kept per list. Beyond this the count is still exact but the ids are cut. */
  public static final int MAXIMUM_IDS = 100;

  /** Code points kept per client-supplied name. */
  public static final int MAXIMUM_NAME_LENGTH = 255;

  private AuditDeltas() {}

  /**
   * A list as {@code {count, ids, truncated}}, or null when nothing is in it.
   *
   * <p>Returns null rather than an empty object so an absent list reads as absent in the stored
   * JSON instead of as a change that affected nobody.
   */
  public static Map<String, Object> ids(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return null;
    }
    var kept = ids.stream().limit(MAXIMUM_IDS).map(UUID::toString).toList();
    var entry = new LinkedHashMap<String, Object>();
    entry.put("count", ids.size());
    entry.put("ids", kept);
    entry.put("truncated", ids.size() > MAXIMUM_IDS);
    return entry;
  }

  /**
   * A list of id-and-role pairs, bounded the same way.
   *
   * @param roles the affected ids mapped to the role each was given
   */
  public static Map<String, Object> idsWithRoles(Map<UUID, ?> roles) {
    if (roles == null || roles.isEmpty()) {
      return null;
    }
    List<Map<String, Object>> kept =
        roles.entrySet().stream()
            .limit(MAXIMUM_IDS)
            .<Map<String, Object>>map(
                assignment -> {
                  var pair = new LinkedHashMap<String, Object>();
                  pair.put("id", assignment.getKey().toString());
                  pair.put("role", String.valueOf(assignment.getValue()));
                  return pair;
                })
            .toList();
    var entry = new LinkedHashMap<String, Object>();
    entry.put("count", roles.size());
    entry.put("entries", kept);
    entry.put("truncated", roles.size() > MAXIMUM_IDS);
    return entry;
  }

  /**
   * Client-supplied names as {@code {count, names, truncated}}, each name and the list bounded, or
   * null when nothing is in it. Names come from request input, so neither their number nor their
   * length is trusted.
   */
  public static Map<String, Object> names(Collection<String> names) {
    if (names == null || names.isEmpty()) {
      return null;
    }
    var kept = names.stream().limit(MAXIMUM_IDS).map(AuditDeltas::truncate).toList();
    var entry = new LinkedHashMap<String, Object>();
    entry.put("count", names.size());
    entry.put("names", kept);
    entry.put("truncated", names.size() > MAXIMUM_IDS);
    return entry;
  }

  /** Bounds a client-supplied string to {@link #MAXIMUM_NAME_LENGTH} code points. */
  public static String truncate(String value) {
    return BoundedText.truncate(value, MAXIMUM_NAME_LENGTH);
  }

  public record Delta<R>(Map<UUID, R> added, Set<UUID> removed, Map<UUID, R> roleChanged) {

    public boolean isEmpty() {
      return added.isEmpty() && removed.isEmpty() && roleChanged.isEmpty();
    }
  }

  /** Compares two id-to-role snapshots: new ids, vanished ids, and ids whose role differs. */
  public static <R> Delta<R> diff(Map<UUID, R> before, Map<UUID, R> after) {
    var added = new LinkedHashMap<UUID, R>();
    var roleChanged = new LinkedHashMap<UUID, R>();
    after.forEach(
        (id, role) -> {
          if (!before.containsKey(id)) {
            added.put(id, role);
          } else if (!Objects.equals(before.get(id), role)) {
            roleChanged.put(id, role);
          }
        });
    var removed = new LinkedHashSet<UUID>(before.keySet());
    removed.removeAll(after.keySet());
    return new Delta<>(added, removed, roleChanged);
  }
}
