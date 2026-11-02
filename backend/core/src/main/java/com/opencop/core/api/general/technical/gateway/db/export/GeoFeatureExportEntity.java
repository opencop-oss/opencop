// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.export;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "features", schema = "geo")
public class GeoFeatureExportEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  public UUID getId() {
    return id;
  }
}
