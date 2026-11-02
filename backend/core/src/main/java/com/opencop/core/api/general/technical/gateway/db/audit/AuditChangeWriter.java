// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.audit;

import com.opencop.core.api.general.business.model.BoundedText;
import com.opencop.core.api.general.business.model.audit.AuditChange;
import com.opencop.core.api.general.business.model.audit.AuditDeltas;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Turns a described change into the JSON the column stores, and keeps it inside the column's
 * bounds.
 *
 * <p>All of this used to live in the four projections, which each held an {@code ObjectMapper} to
 * do it. Writing is a storage concern: the key names, the id caps and the size limit all belong to
 * the schema, so one class beside the store owns them and the projections describe changes instead
 * of serialising them.
 *
 * <p>The size bound cannot be left to the database. The audit write joins the caller's transaction,
 * so a payload the CHECK rejects does not merely lose history — it rolls the user's edit back. A
 * resource holding a very long description would otherwise become permanently unpatchable, because
 * its diff carries both the old value and the new one. Two steps, each giving up only as much as it
 * must: long free text is cut and the payload marked {@code valueTruncated}; if that is still too
 * large the detail is dropped for counts and it is marked {@code payloadTruncated}.
 */
final class AuditChangeWriter {

  private static final Logger log = LoggerFactory.getLogger(AuditChangeWriter.class);

  /** Mirrors V61's {@code audit_history_change_maximum} CHECK, in bytes. */
  private static final int MAXIMUM_BYTES = 65536;

  /** What one free-text value keeps once a payload has to be cut to fit. */
  private static final int VALUE_MAXIMUM_CHARS = 512;

  private final ObjectMapper objectMapper;

  AuditChangeWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * @param nameTruncated whether the entity name was cut to fit its own column, which the row
   *     records so a reader knows the name it shows is not the whole one
   */
  String write(AuditChange change, boolean nameTruncated) {
    var root = toNode(change);
    if (nameTruncated) {
      root.put("nameTruncated", true);
    }
    return bounded(root);
  }

  private ObjectNode toNode(AuditChange change) {
    var root = this.objectMapper.createObjectNode();

    var changed = root.putArray("changed");
    for (var field : change.changed()) {
      var entry = changed.addObject();
      entry.put("field", field.field());
      putScalar(entry, "before", field.before());
      putScalar(entry, "after", field.after());
    }

    var delta = change.delta();
    if (delta != null) {
      putList(root, "added", AuditDeltas.idsWithRoles(delta.added()));
      putList(root, "removed", AuditDeltas.ids(delta.removed()));
      putList(root, "roleChanged", AuditDeltas.idsWithRoles(delta.roleChanged()));
    }

    change.details().forEach((key, value) -> putDetail(root, key, value));
    return root;
  }

  /** A detail is a scalar or a list; anything else would not render for a reader. */
  private void putDetail(ObjectNode root, String key, Object value) {
    switch (value) {
      case null -> {
        // An absent fact reads as absent rather than as null.
      }
      case AuditChange.IdList list -> putList(root, key, AuditDeltas.ids(list.ids()));
      case AuditChange.NameList list -> putList(root, key, AuditDeltas.names(list.names()));
      case Collection<?> items -> putItems(root.putArray(key), cappedList(items));
      default -> putScalar(root, key, value);
    }
  }

  /** A plain list is still capped and its values cut, because its contents are client-supplied. */
  private static List<String> cappedList(Collection<?> items) {
    return items.stream()
        .filter(java.util.Objects::nonNull)
        .limit(AuditDeltas.MAXIMUM_IDS)
        .map(item -> AuditDeltas.truncate(String.valueOf(item)))
        .toList();
  }

  private void putScalar(ObjectNode node, String key, Object value) {
    switch (value) {
      case null -> node.putNull(key);
      case Boolean flag -> node.put(key, flag);
      case Integer number -> node.put(key, number);
      case Long number -> node.put(key, number);
      case UUID id -> node.put(key, id.toString());
      case Enum<?> constant -> node.put(key, constant.name());
      default -> node.put(key, String.valueOf(value));
    }
  }

  /** A bounded list as {@code {count, ids|entries|names, truncated}}; absent lists are dropped. */
  private void putList(ObjectNode root, String key, Map<String, Object> list) {
    if (list == null) {
      return;
    }
    var node = root.putObject(key);
    list.forEach(
        (name, value) -> {
          switch (value) {
            case Integer count -> node.put(name, count);
            case Boolean flag -> node.put(name, flag);
            case Collection<?> items -> putItems(node.putArray(name), items);
            default -> node.put(name, String.valueOf(value));
          }
        });
  }

  private void putItems(ArrayNode array, Collection<?> items) {
    for (var item : items) {
      if (item instanceof Map<?, ?> pair) {
        var entry = array.addObject();
        pair.forEach((key, value) -> entry.put(String.valueOf(key), String.valueOf(value)));
      } else {
        array.add(String.valueOf(item));
      }
    }
  }

  private String bounded(ObjectNode root) {
    var json = this.objectMapper.writeValueAsString(root);
    if (storedBytes(json, root) <= MAXIMUM_BYTES) {
      return json;
    }

    cutLongText(root);
    root.put("valueTruncated", true);
    var cut = this.objectMapper.writeValueAsString(root);
    if (storedBytes(cut, root) <= MAXIMUM_BYTES) {
      log.warn("audit change exceeded {} bytes; long values were cut", MAXIMUM_BYTES);
      return cut;
    }

    log.warn("audit change exceeded {} bytes after cutting; kept counts", MAXIMUM_BYTES);
    var reduced = countsOnly(root);
    return this.objectMapper.writeValueAsString(reduced);
  }

  /**
   * The size the column's CHECK will see, which is not the size of what we write.
   *
   * <p>The CHECK is {@code octet_length(change::text)} on a {@code jsonb} column, and jsonb's text
   * form re-inserts one space after every {@code :} and every {@code ,}. Compact JSON is therefore
   * always smaller than the value measured, so budgeting against the compact bytes let a payload
   * through that the CHECK then rejected — which rolls the caller's edit back with it, the exact
   * failure moving this bound into the writer was meant to prevent.
   *
   * <p>Counted from the tree rather than the text so that a {@code :} or {@code ,} inside a string
   * value is not mistaken for a separator. Key de-duplication can only shrink jsonb further, so
   * this is an upper bound.
   */
  private static int storedBytes(String json, JsonNode root) {
    return utf8Bytes(json) + separatorSpaces(root);
  }

  private static int separatorSpaces(JsonNode node) {
    var spaces = 0;
    if (node.isObject()) {
      // One space after each `:`, and one after each `,` between entries.
      spaces += node.size() + Math.max(0, node.size() - 1);
    } else if (node.isArray()) {
      spaces += Math.max(0, node.size() - 1);
    }
    for (var child : node) {
      spaces += separatorSpaces(child);
    }
    return spaces;
  }

  private static void cutLongText(ObjectNode node) {
    node.properties()
        .forEach(
            property -> {
              var value = property.getValue();
              if (value.isString()) {
                node.put(property.getKey(), cut(value.stringValue()));
              } else if (value.isArray()) {
                value.forEach(
                    element -> {
                      if (element instanceof ObjectNode object) {
                        cutLongText(object);
                      }
                    });
              } else if (value instanceof ObjectNode object) {
                cutLongText(object);
              }
            });
  }

  /** Cut by code point, so an abbreviated value never ends in half of a surrogate pair. */
  private static String cut(String value) {
    return BoundedText.truncate(value, VALUE_MAXIMUM_CHARS);
  }

  /** What the payload can still answer honestly once it cannot hold the detail: how many. */
  private ObjectNode countsOnly(ObjectNode node) {
    var reduced = this.objectMapper.createObjectNode();
    node.properties()
        .forEach(
            property -> {
              var value = property.getValue();
              if (value.isArray()) {
                reduced.put(property.getKey() + "Count", value.size());
              } else if (value instanceof ObjectNode object && object.has("count")) {
                reduced.put(property.getKey() + "Count", object.get("count").asInt());
              } else if (!value.isString() && !value.isObject()) {
                reduced.set(property.getKey(), value);
              }
            });
    reduced.put("payloadTruncated", true);
    return reduced;
  }

  private static int utf8Bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }
}
