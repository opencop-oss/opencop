// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.audit;

import com.opencop.core.api.general.business.model.Caller;
import java.time.Instant;
import java.util.UUID;

/**
 * One recorded user action, as the reader serves it.
 *
 * <p>Every fact here is as it read when the action happened. Nothing is joined to live state, so a
 * later rename, role change or deletion cannot rewrite what the history says.
 *
 * @param change the allowlisted projection, as a JSON object string
 */
public record AuditEntry(
    long id,
    EntityType entityType,
    UUID entityId,
    String entityName,
    Operation operation,
    Instant occurredAt,
    Caller actor,
    String change,
    int changeVersion) {

  /**
   * The resources this ledger covers. Watchboxes and runtime features keep their own tables.
   *
   * <p>{@code name()} is the stored and wire value: append new constants, never rename one.
   */
  public enum EntityType {
    DATA_SOURCE,
    LAYER,
    LAYER_GROUP,
    MAPPER_PROFILE
  }

  /**
   * The shared operation vocabulary. One name per kind of change across every resource, so the same
   * act is not called three things; which subset each resource may use is enforced by its own
   * database constraint.
   *
   * <p>{@code name()} is the stored and wire value, and V61's CHECK constraints list the same
   * names: append new constants, never rename one.
   */
  public enum Operation {
    /** A resource was created. */
    CREATE,
    /** One or more of a resource's own fields changed. */
    PATCH,
    /** A resource was archived. */
    ARCHIVE,
    /** A layer group was permanently deleted. */
    DELETE,
    /** A data source's setup was replaced; only the names of the fields supplied are kept. */
    SETUP_REPLACED,
    /** A data source was started. */
    CONNECT,
    /** A data source was stopped. */
    DISCONNECT,
    /** Members were added to, removed from, or re-roled on a layer, layer group or data source. */
    MEMBERS_CHANGED,
    /** User groups were assigned to or removed from a layer group. */
    USER_GROUPS_CHANGED,
    /** Layers were linked to, unlinked from, or re-roled on a layer group or data source. */
    LAYERS_CHANGED,
    /** Data sources were linked to, unlinked from, or re-roled on a layer. */
    DATA_SOURCES_CHANGED,
    /** The mappers attached to a data source were attached, swapped or detached. */
    MAPPER_CHANGED,
    /** Written on the original layer when it was cloned. */
    CLONED_TO,
    /** Written on the new layer when it was created by cloning. */
    CREATED_FROM_CLONE,
    /** Written on the target layer when a merge into it was requested. */
    MERGED_FROM,
    /** Written on each source layer when a merge of it into another was requested. */
    MERGED_INTO,
    /** An export was requested. */
    EXPORTED,
    /** A data import into a layer was accepted, not necessarily finished. */
    IMPORT_REQUESTED,
    /** A layer was restored because evicting its cache failed right after it was archived. */
    UNARCHIVE
  }
}
