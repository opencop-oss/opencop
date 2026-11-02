// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.layers.directory;

import com.opencop.core.sdk.model.iam.layer.LayerDirectoryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

/**
 * Read-only entity mapping the combined layer/layer-group directory shown in the admin Layers grid.
 * Backed by a Hibernate {@link Subselect} (a {@code UNION ALL} over {@code iam.layer} and {@code
 * iam.layer_group}) rather than a physical table/view, so no schema migration is required. The
 * per-type Postgres enum columns are cast to {@code text} so the two branches union: {@code
 * access_scope} keeps its label, and {@code lifecycle_state} is projected as {@code 'ACTIVE'} for
 * groups (which have no lifecycle). Activity is joined from {@code activity.resource_activity}
 * rather than read off the layer row; a layer with no activity yet has no row there, so the join
 * yields null, and groups have no activity at all. {@link Synchronize} lists the underlying tables
 * so Hibernate flushes pending changes before querying.
 */
@Entity
@Immutable
@Subselect(
    """
    SELECT l.id           AS id,
           'LAYER'        AS entry_type,
           l.name         AS name,
           l.access_scope::text AS access_scope,
           l.lifecycle_state::text AS lifecycle_state,
           l.created_at   AS created_at,
           l.updated_at   AS updated_at,
           ra.last_event_at AS last_event_at
    FROM iam.layer l
    LEFT JOIN activity.resource_activity ra
           ON ra.resource_kind = 'LAYER'
          AND ra.resource_id = l.id
    UNION ALL
    SELECT g.id              AS id,
           'LAYER_GROUP'     AS entry_type,
           g.name            AS name,
           g.access_scope::text AS access_scope,
           'ACTIVE'          AS lifecycle_state,
           g.created_at      AS created_at,
           NULL::timestamptz AS updated_at,
           NULL::timestamptz AS last_event_at
    FROM iam.layer_group g
    """)
@Synchronize({"iam.layer", "iam.layer_group", "activity.resource_activity"})
public class LayerDirectoryEntity {

  @Id
  @Column(name = "id")
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type")
  private LayerDirectoryType type;

  @Column(name = "name")
  private String name;

  @Column(name = "access_scope")
  private String accessScope;

  @Column(name = "lifecycle_state")
  private String lifecycleState;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "last_event_at")
  private Instant lastEventAt;

  public UUID getId() {
    return id;
  }

  public LayerDirectoryType getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public String getAccessScope() {
    return accessScope;
  }

  public String getLifecycleState() {
    return lifecycleState;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getLastEventAt() {
    return lastEventAt;
  }
}
