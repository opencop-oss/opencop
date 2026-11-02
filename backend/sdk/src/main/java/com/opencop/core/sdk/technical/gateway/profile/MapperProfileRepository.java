// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.profile;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
public class MapperProfileRepository implements MapperProfileService {

  private static final String LATEST_BY_MAPPER_ID =
      """
      SELECT m.id, m.name, m.description, m.editable, mi.integration_id, mi.qualifier,
             m.version, m.mapper, m.archived,
             m.created_at, m.created_by, m.updated_at, m.updated_by,
             ra.last_event_at AS last_event_at
      FROM profiles.data_mapper m
      JOIN profiles.mapper mi ON mi.id = m.id
      LEFT JOIN activity.resource_activity ra
             ON ra.resource_kind = 'MAPPER'
            AND ra.resource_id = m.id
      WHERE m.id = :id
      ORDER BY m.version DESC
      LIMIT 1
      """;

  // A source that only writes has no ingress mapper, so it finds none.
  private static final String LATEST_BY_SOURCE_ID =
      """
      SELECT m.id, m.name, m.description, m.editable, mi.integration_id, mi.qualifier,
             m.version, m.mapper, m.archived,
             m.created_at, m.created_by, m.updated_at, m.updated_by,
             ra.last_event_at AS last_event_at
      FROM profiles.data_source ds
      JOIN LATERAL (
             SELECT *
               FROM profiles.data_mapper latest
              WHERE latest.id = ds.ingress_mapper_id
              ORDER BY latest.version DESC
              LIMIT 1
           ) m ON true
      JOIN profiles.mapper mi ON mi.id = m.id
      LEFT JOIN activity.resource_activity ra
             ON ra.resource_kind = 'MAPPER'
            AND ra.resource_id = m.id
      WHERE ds.id = :sourceId
      """;

  private static final String SOURCE_ONLY_WRITES =
      """
      SELECT EXISTS (
        SELECT 1 FROM profiles.data_source WHERE id = :sourceId AND access = 'W'
      )
      """;

  // One identity row per mapper, whatever its number of revisions, so one lock covers them all.
  private static final String LOCK_MAPPER_IDENTITY =
      "SELECT id FROM profiles.mapper WHERE id = :id FOR UPDATE";

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final MapperProfileRowMapper rowMapper;
  private final RowMapper<MapperProfileFullMeta> mapperProfileFullRowMapper;

  MapperProfileRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.rowMapper = new MapperProfileRowMapper(objectMapper);
    this.mapperProfileFullRowMapper =
        (rs, i) -> {
          var createdOdt = rs.getObject("created_at", OffsetDateTime.class);
          var updatedOdt = rs.getObject("updated_at", OffsetDateTime.class);
          Instant createdAt = createdOdt != null ? createdOdt.toInstant() : null;
          Instant updatedAt = updatedOdt != null ? updatedOdt.toInstant() : null;
          var lastEventOdt = rs.getObject("last_event_at", OffsetDateTime.class);
          Instant lastEventAt = lastEventOdt != null ? lastEventOdt.toInstant() : null;
          return new MapperProfileFullMeta(
              MapperProfile.builder()
                  .withId(rs.getObject("id", UUID.class))
                  .withName(rs.getString("name"))
                  .withDescription(rs.getString("description"))
                  .withEditable(rs.getBoolean("editable"))
                  .withIntegrationId(rs.getObject("integration_id", UUID.class))
                  .withQualifier(MapperQualifier.valueOf(rs.getString("qualifier")))
                  .withVersion(rs.getInt("version"))
                  .withDynamicMapper(
                      this.objectMapper.readValue(rs.getString("mapper"), DynamicMapper.class))
                  .withArchived(rs.getBoolean("archived"))
                  .withCreatedAt(createdAt)
                  .withCreatedBy(rs.getObject("created_by", UUID.class))
                  .withUpdatedAt(updatedAt)
                  .withUpdatedBy(rs.getObject("updated_by", UUID.class))
                  .withLastEventAt(lastEventAt)
                  .build(),
              rs.getString("sample_payload_filename"),
              rs.getBytes("sample_payload"));
        };
  }

  @Override
  @Cacheable(cacheNames = MapperProfileCaches.BY_MAPPER_ID, key = "#mapperId")
  public Optional<MapperProfile> findLatest(UUID mapperId) {
    return jdbc.query(LATEST_BY_MAPPER_ID, Map.of("id", mapperId), rowMapper).stream().findFirst();
  }

  @Override
  @Cacheable(cacheNames = MapperProfileCaches.BY_SOURCE_ID, key = "#sourceId")
  public Optional<MapperProfile> findLatestForSource(UUID sourceId) {
    return jdbc.query(LATEST_BY_SOURCE_ID, Map.of("sourceId", sourceId), rowMapper).stream()
        .findFirst();
  }

  @Override
  @Cacheable(cacheNames = MapperProfileCaches.SOURCE_ONLY_WRITES, key = "#sourceId")
  public boolean onlyWrites(UUID sourceId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(SOURCE_ONLY_WRITES, Map.of("sourceId", sourceId), Boolean.class));
  }

  @Override
  @Transactional
  public UUID createMapperProfile(
      MapperProfile mapper, byte[] samplePayload, String filename, UUID createdBy) {
    UUID id = mapper.id() == null ? UUID.randomUUID() : mapper.id();

    jdbc.update(
        """
        INSERT INTO profiles.mapper (id, qualifier)
        VALUES (:id, CAST(:qualifier AS profiles.mapper_qualifier))
        """,
        Map.of("id", id, "qualifier", qualifierOf(mapper).name()));

    String sql =
        """
                  INSERT INTO profiles.data_mapper (
                    id, version, name, description, editable, mapper, sample_payload, sample_payload_filename, created_by, created_at
                  )
                  VALUES (
                    :id, :version, :name, :description, :editable, CAST(:mapper AS jsonb), :payload, :filename, :createdBy, now()
                  )
                """;

    var params =
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("version", mapper.version())
            .addValue("name", mapper.name())
            .addValue("description", mapper.description())
            .addValue("editable", mapper.editable() == null || mapper.editable())
            .addValue("mapper", objectMapper.writeValueAsString(mapper.dynamicMapper()))
            .addValue("payload", samplePayload)
            .addValue("filename", filename)
            .addValue("createdBy", createdBy);

    jdbc.update(sql, params);
    return id;
  }

  /**
   * Fills and reads the same cache entry as {@link #findLatest}: the cache holds the profile, not
   * the {@link Optional}, so either read serves the other.
   */
  @Override
  @Cacheable(cacheNames = MapperProfileCaches.BY_MAPPER_ID, key = "#mapperId")
  public MapperProfile getLatestMapperProfileMeta(UUID mapperId) {
    return findLatest(mapperId).orElse(null);
  }

  @Override
  public MapperProfileFullMeta getLatestMapperProfileFull(UUID mapperId) {
    String sql =
        """
                  SELECT m.id, m.name, m.description, m.editable, mi.integration_id, mi.qualifier,
                         m.version, m.mapper, m.archived,
                         m.created_at, m.created_by, m.updated_at, m.updated_by,
                         ra.last_event_at AS last_event_at,
                         m.sample_payload, m.sample_payload_filename
                  FROM profiles.data_mapper m
                  JOIN profiles.mapper mi ON mi.id = m.id
                  LEFT JOIN activity.resource_activity ra
                         ON ra.resource_kind = 'MAPPER'
                        AND ra.resource_id = m.id
                  WHERE m.id = :id
                  ORDER BY m.version DESC
                  LIMIT 1
                """;

    try {
      return jdbc.queryForObject(sql, Map.of("id", mapperId), mapperProfileFullRowMapper);
    } catch (EmptyResultDataAccessException _) {
      return null;
    }
  }

  @Override
  @Transactional
  public MapperProfileFullMeta createNewVersion(
      MapperProfileFullMeta profileFullMeta, UUID updatedBy) {
    // The insert is wrapped in a data-modifying CTE only so activity can be joined on the way out:
    // RETURNING cannot join, and the response feeds the mapper editor, where a null "last event"
    // straight after an edit would read as the edit having reset it. Activity is keyed by mapper
    // id, so a new version inherits it rather than starting over.
    String sql =
        """
                  WITH inserted AS (
                    INSERT INTO profiles.data_mapper (
                      id, version, name, description, editable, mapper,
                      sample_payload, sample_payload_filename,
                      created_at, created_by, updated_at, updated_by
                    )
                    SELECT
                      :id,
                      COALESCE(MAX(version), 0) + 1,
                      :name,
                      :description,
                      :editable,
                      CAST(:mapper AS jsonb),
                      :payload,
                      :filename,
                      MIN(created_at),
                      (SELECT created_by FROM profiles.data_mapper WHERE id = :id ORDER BY version LIMIT 1),
                      now(),
                      :updatedBy
                    FROM profiles.data_mapper
                    WHERE id = :id
                    RETURNING id, name, description, version, editable, mapper,
                              archived,
                              created_at, created_by, updated_at, updated_by,
                              sample_payload, sample_payload_filename
                  )
                  SELECT i.id, i.name, i.description, i.version, i.editable, mi.integration_id,
                         mi.qualifier, i.mapper, i.archived,
                         i.created_at, i.created_by, i.updated_at, i.updated_by,
                         ra.last_event_at AS last_event_at,
                         i.sample_payload, i.sample_payload_filename
                  FROM inserted i
                  JOIN profiles.mapper mi ON mi.id = i.id
                  LEFT JOIN activity.resource_activity ra
                         ON ra.resource_kind = 'MAPPER'
                        AND ra.resource_id = i.id
                """;

    var params =
        new MapSqlParameterSource()
            .addValue("id", profileFullMeta.profile().id())
            .addValue("name", profileFullMeta.profile().name())
            .addValue("description", profileFullMeta.profile().description())
            .addValue(
                "editable",
                profileFullMeta.profile().editable() == null
                    || profileFullMeta.profile().editable())
            .addValue(
                "mapper",
                objectMapper.writeValueAsString(profileFullMeta.profile().dynamicMapper()))
            .addValue("payload", profileFullMeta.payload())
            .addValue("filename", profileFullMeta.filename())
            .addValue("updatedBy", updatedBy);

    // Connections decode with the latest revision of their own mapper, so they pick up the new
    // rules within the next Caffeine TTL window (~1 min). Postgres holds the authoritative version;
    // Core and Processor caches converge on it within their TTL.
    return jdbc.queryForObject(sql, params, mapperProfileFullRowMapper);
  }

  @Override
  public List<MapperProfile> getAllMapperProfiles(Set<MapperQualifier> qualifiers) {
    String sql =
        """
                        SELECT DISTINCT ON (m.id)
                          m.id, m.name, m.description, m.editable, mi.integration_id, mi.qualifier,
                          m.version, m.mapper, m.archived,
                          m.created_at, m.created_by, m.updated_at, m.updated_by,
                          ra.last_event_at AS last_event_at
                        FROM profiles.data_mapper m
                        JOIN profiles.mapper mi ON mi.id = m.id
                        LEFT JOIN activity.resource_activity ra
                               ON ra.resource_kind = 'MAPPER'
                              AND ra.resource_id = m.id
                        WHERE m.archived = false
                          AND CAST(mi.qualifier AS text) IN (:qualifiers)
                        ORDER BY m.id, m.version DESC
                        """;
    if (qualifiers.isEmpty()) {
      return List.of();
    }
    return jdbc.query(sql, Map.of("qualifiers", names(qualifiers)), rowMapper);
  }

  @Override
  public List<MapperProfile> getAllMapperVersions(UUID mapperId) {
    String sql =
        """
                        SELECT m.id, m.name, m.description, m.editable, mi.integration_id,
                               mi.qualifier, m.version, m.mapper, m.archived, m.created_at,
                               m.created_by, m.updated_at, m.updated_by,
                               ra.last_event_at AS last_event_at
                        FROM profiles.data_mapper m
                        JOIN profiles.mapper mi ON mi.id = m.id
                        LEFT JOIN activity.resource_activity ra
                               ON ra.resource_kind = 'MAPPER'
                              AND ra.resource_id = m.id
                        WHERE m.id = :id
                        ORDER BY m.version DESC
                        """;
    return jdbc.query(sql, Map.of("id", mapperId), rowMapper);
  }

  // TODO: migrate to JPA
  @Override
  public List<MapperProfile> getAllMapperProfilesAdmin(Set<MapperQualifier> qualifiers) {
    String sql =
        """
                        SELECT DISTINCT ON (m.id)
                          m.id, m.name, m.description, m.editable, mi.integration_id, mi.qualifier,
                          m.version, m.mapper, m.archived,
                          m.created_at, m.created_by, m.updated_at, m.updated_by,
                          ra.last_event_at AS last_event_at
                        FROM profiles.data_mapper m
                        JOIN profiles.mapper mi ON mi.id = m.id
                        LEFT JOIN activity.resource_activity ra
                               ON ra.resource_kind = 'MAPPER'
                              AND ra.resource_id = m.id
                        WHERE CAST(mi.qualifier AS text) IN (:qualifiers)
                        ORDER BY m.id, m.version DESC
                        """;
    if (qualifiers.isEmpty()) {
      return List.of();
    }
    return jdbc.query(sql, Map.of("qualifiers", names(qualifiers)), rowMapper);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public MapperProfile lockAndGetLatestMapperProfileMeta(UUID mapperId) {
    // Same identity-row lock archiveMapperProfile takes, so a save and an archive of one mapper
    // serialise against each other rather than racing on separate version rows.
    jdbc.queryForList(LOCK_MAPPER_IDENTITY, Map.of("id", mapperId), UUID.class);

    return jdbc.query(LATEST_BY_MAPPER_ID, Map.of("id", mapperId), rowMapper).stream()
        .findFirst()
        .orElse(null);
  }

  // TODO: migrate to JPA
  @Override
  @Transactional
  public int archiveMapperProfile(UUID mapperId, UUID archivedBy) {
    // Waits for any connection being created with this mapper, which holds its identity row until
    // it commits; the update below, its own statement, then sees that connection and archives
    // nothing. A creation that comes after waits instead, and finds the mapper archived.
    jdbc.queryForList(LOCK_MAPPER_IDENTITY, Map.of("id", mapperId), UUID.class);

    // archived=true applies to every version so the unarchived list (which filters before
    // picking the highest version) cannot resurrect an older version as active. The audit stamp,
    // however, only moves on the head version, so versions 1..n-1 keep their own editors' history
    // instead of every version reporting whoever archived the mapper.
    String sql =
        """
                        WITH head AS (
                          SELECT MAX(version) AS version
                          FROM profiles.data_mapper
                          WHERE id = :id
                            AND archived = false
                        )
                        UPDATE profiles.data_mapper AS mapper
                        SET archived = true,
                            updated_at = CASE
                              WHEN mapper.version = head.version THEN now()
                              ELSE mapper.updated_at
                            END,
                            updated_by = CASE
                              WHEN mapper.version = head.version THEN :archivedBy
                              ELSE mapper.updated_by
                            END
                        FROM head
                        WHERE mapper.id = :id
                          AND mapper.archived = false
                          AND NOT EXISTS (
                            SELECT 1
                              FROM profiles.data_source
                             WHERE archived_at IS NULL
                               AND (ingress_mapper_id = :id OR egress_mapper_id = :id)
                          )
                        """;

    var params = new HashMap<String, Object>();
    params.put("id", mapperId);
    params.put("archivedBy", archivedBy);

    return jdbc.update(sql, params);
  }

  @Override
  public boolean mapperProfileExists(UUID mapperId) {
    String sql =
        """
                        SELECT EXISTS (
                            SELECT 1
                            FROM profiles.data_mapper
                            WHERE id = :id
                        )
                        """;

    return Boolean.TRUE.equals(jdbc.queryForObject(sql, Map.of("id", mapperId), Boolean.class));
  }

  @Override
  public boolean mapperProfileExistsByName(String name) {
    String sql =
        """
                        SELECT EXISTS (
                            SELECT 1
                            FROM profiles.data_mapper
                            WHERE name = :name
                        )
                        """;

    return Boolean.TRUE.equals(jdbc.queryForObject(sql, Map.of("name", name), Boolean.class));
  }

  private static List<String> names(Set<MapperQualifier> qualifiers) {
    return qualifiers.stream().map(MapperQualifier::name).sorted().toList();
  }

  private static MapperQualifier qualifierOf(MapperProfile mapper) {
    return mapper.qualifier() == null ? MapperProfile.DEFAULT_QUALIFIER : mapper.qualifier();
  }
}
