// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.profile;

import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import java.time.Instant;
import java.util.UUID;

/**
 * A mapper revision as the catalog lists it.
 *
 * @param integrationId the integration contributing this mapper from code; {@code null} for a
 *     mapper authored as data. It is read from the mapper's identity, and storing a profile never
 *     sets it: only plugin mapper registration records which integration owns a mapper.
 */
public record MapperProfile(
    UUID id,
    String name,
    String description,
    Boolean editable,
    UUID integrationId,
    MapperQualifier qualifier,
    Integer version,
    DynamicMapper dynamicMapper,
    Boolean archived,
    Instant createdAt,
    UUID createdBy,
    Instant updatedAt,
    UUID updatedBy,
    Instant lastEventAt) {

  /**
   * Every mapper published before mappers had a direction decodes inbound payloads, so a profile
   * built without a qualifier is an ingress mapper, matching the migration that introduced it.
   */
  public static final MapperQualifier DEFAULT_QUALIFIER = MapperQualifier.INGRESS;

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static final class Builder {

    private UUID id;
    private String name;
    private String description;
    private Boolean editable;
    private UUID integrationId;
    private MapperQualifier qualifier = DEFAULT_QUALIFIER;
    private Integer version;
    private DynamicMapper dynamicMapper;
    private Boolean archived;
    private Instant createdAt;
    private UUID createdBy;
    private Instant updatedAt;
    private UUID updatedBy;
    private Instant lastEventAt;

    private Builder() {}

    private Builder(MapperProfile profile) {
      this.id = profile.id;
      this.name = profile.name;
      this.description = profile.description;
      this.editable = profile.editable;
      this.integrationId = profile.integrationId;
      this.qualifier = profile.qualifier;
      this.version = profile.version;
      this.dynamicMapper = profile.dynamicMapper;
      this.archived = profile.archived;
      this.createdAt = profile.createdAt;
      this.createdBy = profile.createdBy;
      this.updatedAt = profile.updatedAt;
      this.updatedBy = profile.updatedBy;
      this.lastEventAt = profile.lastEventAt;
    }

    public Builder withId(UUID id) {
      this.id = id;
      return this;
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withDescription(String description) {
      this.description = description;
      return this;
    }

    public Builder withEditable(Boolean editable) {
      this.editable = editable;
      return this;
    }

    public Builder withIntegrationId(UUID integrationId) {
      this.integrationId = integrationId;
      return this;
    }

    public Builder withQualifier(MapperQualifier qualifier) {
      this.qualifier = qualifier;
      return this;
    }

    public Builder withVersion(Integer version) {
      this.version = version;
      return this;
    }

    public Builder withDynamicMapper(DynamicMapper dynamicMapper) {
      this.dynamicMapper = dynamicMapper;
      return this;
    }

    public Builder withArchived(Boolean archived) {
      this.archived = archived;
      return this;
    }

    public Builder withCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder withCreatedBy(UUID createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder withUpdatedAt(Instant updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    public Builder withUpdatedBy(UUID updatedBy) {
      this.updatedBy = updatedBy;
      return this;
    }

    public Builder withLastEventAt(Instant lastEventAt) {
      this.lastEventAt = lastEventAt;
      return this;
    }

    public MapperProfile build() {
      return new MapperProfile(
          id,
          name,
          description,
          editable,
          integrationId,
          qualifier,
          version,
          dynamicMapper,
          archived,
          createdAt,
          createdBy,
          updatedAt,
          updatedBy,
          lastEventAt);
    }
  }
}
