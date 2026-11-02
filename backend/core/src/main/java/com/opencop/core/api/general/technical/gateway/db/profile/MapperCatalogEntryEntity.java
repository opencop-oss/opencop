// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Formula;

@Entity(name = "MapperCatalogEntry")
@IdClass(MapperCatalogEntryEntity.Key.class)
@Table(name = "data_mapper", schema = "profiles")
public class MapperCatalogEntryEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID mapperId;

  @Id
  @Column(nullable = false)
  private int version;

  @Column(nullable = false)
  private String name;

  @Column private String description;

  @Column(nullable = false)
  private boolean editable;

  @Formula("jsonb_typeof(mapper) <> 'null'")
  private boolean mappingDefinitionPresent;

  @Column(nullable = false)
  private boolean archived;

  protected MapperCatalogEntryEntity() {}

  UUID mapperId() {
    return mapperId;
  }

  int version() {
    return version;
  }

  String name() {
    return name;
  }

  String description() {
    return description;
  }

  boolean editable() {
    return editable;
  }

  boolean mappingDefinitionPresent() {
    return mappingDefinitionPresent;
  }

  boolean archived() {
    return archived;
  }

  public static final class Key implements Serializable {

    private UUID mapperId;
    private int version;

    public Key() {}

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof Key key)) {
        return false;
      }
      return version == key.version && Objects.equals(mapperId, key.mapperId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(mapperId, version);
    }
  }
}
