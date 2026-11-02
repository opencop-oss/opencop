// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup;

import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "layer_data_source_group",
    schema = "iam",
    indexes = {
      @Index(name = "idx_layer_data_source_group_layer_id", columnList = "layer_id"),
      @Index(name = "idx_layer_data_source_group_group_id", columnList = "data_source_group_id")
    })
public class LayerDataSourceGroupEntity {

  @EmbeddedId private LayerDataSourceGroupIdEntity id;

  @MapsId("layerId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "layer_id", nullable = false)
  private LayerEntity layer;

  @MapsId("dataSourceGroupId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "data_source_group_id", nullable = false)
  private DataSourceGroupEntity dataSourceGroup;

  @CreationTimestamp
  @Column(name = "assigned_at", nullable = false, updatable = false)
  private Instant assignedAt;

  public LayerDataSourceGroupEntity() {}

  public LayerDataSourceGroupEntity(LayerEntity layer, DataSourceGroupEntity dataSourceGroup) {
    this.layer = layer;
    this.dataSourceGroup = dataSourceGroup;
    this.id = new LayerDataSourceGroupIdEntity(layer.getId(), dataSourceGroup.getId());
  }

  public LayerDataSourceGroupIdEntity getId() {
    return id;
  }

  public LayerEntity getLayer() {
    return layer;
  }

  public DataSourceGroupEntity getDataSourceGroup() {
    return dataSourceGroup;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }
}
