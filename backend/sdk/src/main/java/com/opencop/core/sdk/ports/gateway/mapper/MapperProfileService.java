// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.mapper;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stores and reads mapper profiles: their revisions, sample payloads and archiving, and the latest
 * revision of a mapper by its own identifier or by a source's.
 */
public interface MapperProfileService {

  /** The latest revision of the mapper. */
  Optional<MapperProfile> findLatest(UUID mapperId);

  /**
   * The latest revision of the ingress mapper the source decodes with. Empty when the source is
   * unknown or has no ingress mapper, which is the case for a source that only writes.
   */
  Optional<MapperProfile> findLatestForSource(UUID sourceId);

  /**
   * Whether the source exists and only writes. Tells a source that has no ingress mapper by design
   * apart from an unknown source or a missing mapper, which {@link #findLatestForSource} cannot.
   */
  boolean onlyWrites(UUID sourceId);

  /**
   * Stores a new mapper at the profile's version, with its sample payload.
   *
   * @return the mapper's identifier, generated when the profile has none
   */
  UUID createMapperProfile(
      MapperProfile mapper, byte[] samplePayload, String filename, UUID createdBy);

  /**
   * Locks the mapper's identity row and returns its head version, read past the mapper cache.
   *
   * <p>Must be called inside a transaction. The paired lock and uncached read are one method
   * because a save's version guard is only sound when both hold: {@link
   * #getLatestMapperProfileMeta} serves a cache that may lag the database.
   *
   * @return the head version, or null when no such mapper exists
   */
  MapperProfile lockAndGetLatestMapperProfileMeta(UUID mapperId);

  /** The latest revision of the mapper, or null; shares {@link #findLatest}'s cached read. */
  MapperProfile getLatestMapperProfileMeta(UUID mapperId);

  /** The latest revision of the mapper with its sample payload, or null when it is unknown. */
  MapperProfileFullMeta getLatestMapperProfileFull(UUID mapperId);

  /** Stores the profile as the mapper's next revision, keeping its creator and creation time. */
  MapperProfileFullMeta createNewVersion(MapperProfileFullMeta profileFullMeta, UUID updatedBy);

  /** The latest revision of every unarchived mapper working in one of the directions. */
  List<MapperProfile> getAllMapperProfiles(Set<MapperQualifier> qualifiers);

  /** The latest revision of every mapper working in one of the directions, archived included. */
  List<MapperProfile> getAllMapperProfilesAdmin(Set<MapperQualifier> qualifiers);

  /** Every revision of the mapper, newest first. */
  List<MapperProfile> getAllMapperVersions(UUID mapperId);

  /**
   * Archives every revision of the mapper unless something still uses it.
   *
   * @return the number of revisions archived, zero when the mapper is in use or already archived
   */
  int archiveMapperProfile(UUID mapperId, UUID archivedBy);

  /** Whether any revision of the mapper is stored. */
  boolean mapperProfileExists(UUID mapperId);

  /** Whether any revision of any mapper carries the name. */
  boolean mapperProfileExistsByName(String name);
}
