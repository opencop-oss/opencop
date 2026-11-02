// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection;

import com.opencop.core.api.general.business.model.connection.setup.SetupPrefill;
import com.opencop.core.api.general.business.model.connection.setup.StoredConnectionSetup;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceConstraintViolationException;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceListenPortConflictException;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceNameConflictException;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.transport.TransportConfig;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.DataSourceReadFilter;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcDataSourceRepository implements DataSourceRepository {

  private static final Logger log = LoggerFactory.getLogger(JdbcDataSourceRepository.class);

  private static final String ACTIVE_NAME_CONSTRAINT = "uk_data_source_name_active";
  private static final String ACTIVE_LISTEN_PORT_CONSTRAINT = "uk_data_source_listen_port_active";

  /** UdpConfig.listenPort() over the stored spec; the expression the listen-port index keys on. */
  private static final String SPEC_LISTEN_PORT =
      "COALESCE(connection_spec #>> '{transport,localPort}',"
          + " connection_spec #>> '{transport,port}')::integer";

  private final NamedParameterJdbcTemplate jdbc;
  private final DataSourceRowMapper mapper;
  private final ObjectMapper objectMapper;

  public JdbcDataSourceRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.mapper = new DataSourceRowMapper(objectMapper);
  }

  @Override
  public Optional<DataSource> findById(UUID id) {
    // Deliberately not routed through DataSourceSqlSpecifications.select(): that applies a default
    // RecordVisibility of ACTIVE, and findById must keep finding archived sources.
    String sql = DataSourceSqlSpecifications.SELECT_DATA_SOURCE + " WHERE ds.id = :id";
    var rows = jdbc.query(sql, Map.of("id", id), mapper);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
  }

  @Override
  public Optional<DataSource> findOne(DataSourceReadFilter filter) {
    var sqlAndParams = DataSourceSqlSpecifications.select(filter);
    var rows = jdbc.query(sqlAndParams.sql(), sqlAndParams.params(), mapper);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
  }

  @Override
  public List<DataSource> findAll(DataSourceReadFilter filter) {
    var sqlAndParams = DataSourceSqlSpecifications.select(filter);
    return jdbc.query(sqlAndParams.sql(), sqlAndParams.params(), mapper);
  }

  @Override
  public List<DataSource> findAll(String currentBrokerProvider, RecordVisibility recordVisibility) {
    return findAll(
        new DataSourceReadFilter(
            null, Set.of(), recordVisibility, currentBrokerProvider, null, null));
  }

  @Override
  public int countExcludedKafkaSources(String currentBrokerProvider) {
    String sql =
        """
            SELECT count(*) FROM profiles.data_source
            WHERE provider = 'KAFKA'::profiles.transport_kind
              AND archived_at IS NULL
              AND (connection_spec->>'brokerProvider' IS NULL
                   OR connection_spec->>'brokerProvider' != :currentProvider)
            """;
    return jdbc.queryForObject(
        sql, Map.of("currentProvider", currentBrokerProvider), Integer.class);
  }

  @Override
  public Optional<UUID> findActiveByListenPort(TransportKind provider, int listenPort) {
    String sql =
        """
            SELECT id FROM profiles.data_source
             WHERE provider=:provider::profiles.transport_kind AND %s=:port
               AND archived_at IS NULL
             ORDER BY updated_at DESC LIMIT 1
            """
            .formatted(SPEC_LISTEN_PORT);
    var list =
        jdbc.queryForList(sql, Map.of("provider", provider.name(), "port", listenPort), UUID.class);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
  }

  @Override
  public Instant persist(DataSource dataSource) {
    // RETURNING captures the DB-assigned updated_at atomically — avoids race with concurrent
    // updates
    try {
      return switch (dataSource) {
        case SocketDataSource socket -> persistSocket(socket);
        case KafkaDataSource kafka -> persistKafka(kafka);
      };
    } catch (DataIntegrityViolationException e) {
      throw attribute(dataSource, e);
    }
  }

  /**
   * Maps a rejected insert onto the constraint that rejected it, so callers can tell a name
   * collision from a listen-port collision. Violations that cannot be attributed still leave the
   * port typed: they surface as {@link DataSourceConstraintViolationException} rather than a
   * framework exception, so a caller cleaning up after a failed write does not have to reach past
   * the port to recognise one.
   */
  private static RuntimeException attribute(
      DataSource dataSource, DataIntegrityViolationException e) {
    var constraint = violatedConstraint(e);
    if (ACTIVE_NAME_CONSTRAINT.equals(constraint)) {
      return new DataSourceNameConflictException(dataSource.name(), e);
    }
    var transport =
        dataSource instanceof SocketDataSource socket ? socket.spec().transport() : null;
    return attributeToTransport(dataSource.id(), dataSource.name(), transport, constraint, e);
  }

  /** Maps a rejected setup update as an insert is mapped; the name it keeps cannot collide. */
  private static RuntimeException attributeSetup(
      UUID sourceId, StoredConnectionSetup setup, DataIntegrityViolationException e) {
    return attributeToTransport(
        sourceId, sourceId.toString(), setup.spec().transport(), violatedConstraint(e), e);
  }

  /**
   * Maps a violation the source's transport can explain, and logs any other as unmodelled.
   *
   * @param identifier how the violation names the source to its caller
   * @param transport the source's transport, or null for one without a socket
   * @param constraint the violated constraint, or null when Postgres did not name one
   */
  private static RuntimeException attributeToTransport(
      UUID sourceId,
      String identifier,
      @Nullable TransportConfig transport,
      @Nullable String constraint,
      DataIntegrityViolationException e) {
    if (constraint == null) {
      return new DataSourceConstraintViolationException(identifier, e);
    }
    if (ACTIVE_LISTEN_PORT_CONSTRAINT.equals(constraint) && transport instanceof UdpConfig udp) {
      return new DataSourceListenPortConflictException(udp.listenPort(), e);
    }
    log.warn("Data source {} rejected by unmodelled constraint {}", sourceId, constraint);
    return new DataSourceConstraintViolationException(identifier, e);
  }

  /** The constraint Postgres reports as violated, or null when the cause names none. */
  private static @Nullable String violatedConstraint(DataIntegrityViolationException e) {
    var cause = NestedExceptionUtils.getMostSpecificCause(e);
    if (!(cause instanceof PSQLException psql) || psql.getServerErrorMessage() == null) {
      return null;
    }
    return psql.getServerErrorMessage().getConstraint();
  }

  private Instant persistSocket(SocketDataSource ds) {
    String connectionSpec = serializeSpec(ds.spec());

    String sql =
        """
            INSERT INTO profiles.data_source(
              id, name, tenant, provider, access,
              ingress_mapper_id, egress_mapper_id,
              integration_id, connection_schema_version,
              connection_spec, default_visible, created_by
            )
            VALUES(
              :id, :name, :tenant, :provider::profiles.transport_kind, :access::profiles.access_mode,
              :ingressMapperId, :egressMapperId,
              :integrationId, :connectionSchemaVersion,
              :connection_spec::jsonb, :defaultVisible, :createdBy
            )
            ON CONFLICT (id) DO UPDATE SET
              name            = EXCLUDED.name,
              tenant          = EXCLUDED.tenant,
              provider        = EXCLUDED.provider,
              access          = EXCLUDED.access,
              ingress_mapper_id = EXCLUDED.ingress_mapper_id,
              egress_mapper_id = EXCLUDED.egress_mapper_id,
              integration_id = EXCLUDED.integration_id,
              connection_schema_version = EXCLUDED.connection_schema_version,
              connection_spec = EXCLUDED.connection_spec,
              updated_at      = now(),
              updated_by      = :updatedBy
            RETURNING updated_at
            """;

    var params =
        new MapSqlParameterSource()
            .addValue("id", ds.id())
            .addValue("name", ds.name())
            .addValue("tenant", ds.tenant())
            .addValue("provider", ds.provider().name())
            .addValue("access", ds.access().getAccess())
            .addValue("ingressMapperId", ds.ingressMapperId())
            .addValue("egressMapperId", ds.egressMapperId())
            .addValue(
                "integrationId", ds.integration() == null ? null : ds.integration().integrationId())
            .addValue(
                "connectionSchemaVersion",
                ds.integration() == null ? null : ds.integration().schemaVersion())
            .addValue("connection_spec", connectionSpec)
            .addValue("defaultVisible", ds.defaultVisible())
            .addValue("createdBy", ds.createdBy())
            .addValue("updatedBy", ds.updatedBy());

    return jdbc.queryForObject(sql, params, OffsetDateTime.class).toInstant();
  }

  private Instant persistKafka(KafkaDataSource ds) {
    // KAFKA sources store only non-sensitive metadata in connection_spec JSONB.
    // Password is NOT stored — only in Kafka SCRAM metadata (hashed) and the one-time response.
    if (ds.brokerProvider() == null) {
      throw new IllegalArgumentException(
          "brokerProvider must not be null when persisting KafkaDataSource: " + ds.id());
    }
    var specMap =
        new HashMap<>(
            Map.of(
                "topicName", ds.topicName(),
                "username", ds.username(),
                "brokerProvider", ds.brokerProvider()));
    var kafkaSpec = objectMapper.writeValueAsString(specMap);

    String sql =
        """
            INSERT INTO profiles.data_source(
              id, name, tenant, provider, access,
              ingress_mapper_id, integration_id, connection_schema_version,
              connection_spec, default_visible, created_by
            )
            VALUES(
              :id, :name, :tenant, :provider::profiles.transport_kind, :access::profiles.access_mode,
              :ingressMapperId, :integrationId, :connectionSchemaVersion,
              :connection_spec::jsonb, :defaultVisible, :createdBy
            )
            RETURNING updated_at
            """;

    var params =
        new MapSqlParameterSource()
            .addValue("id", ds.id())
            .addValue("name", ds.name())
            .addValue("tenant", ds.tenant())
            .addValue("provider", TransportKind.KAFKA.name())
            .addValue("ingressMapperId", ds.ingressMapperId())
            .addValue(
                "integrationId", ds.integration() == null ? null : ds.integration().integrationId())
            .addValue(
                "connectionSchemaVersion",
                ds.integration() == null ? null : ds.integration().schemaVersion())
            .addValue("connection_spec", kafkaSpec)
            .addValue("access", ds.access().getAccess())
            .addValue("defaultVisible", ds.defaultVisible())
            .addValue("createdBy", ds.createdBy());

    return jdbc.queryForObject(sql, params, OffsetDateTime.class).toInstant();
  }

  @Override
  public Instant setEnabled(UUID sourceId, boolean enabled, UUID actorId) {
    // RETURNING captures the DB-assigned updated_at atomically — avoids race with concurrent
    // updates. Caller guarantees row exists (loaded via findById); throws if concurrent delete.
    var params = new HashMap<String, Object>();
    params.put("id", sourceId);
    params.put("enabled", enabled);
    params.put("actorId", actorId);

    return jdbc.queryForObject(
            """
            UPDATE profiles.data_source
               SET enabled=:enabled, updated_at=now(), updated_by=:actorId
             WHERE id=:id
            RETURNING updated_at
            """,
            params,
            OffsetDateTime.class)
        .toInstant();
  }

  @Override
  @Transactional
  public void archive(UUID sourceId, UUID archivedBy) {
    var archivedCount =
        jdbc.update(
            """
                UPDATE profiles.data_source
                   SET archived_at=now(),
                       archived_by=:archivedBy,
                       enabled=false,
                       assigned_instance=NULL,
                       updated_at=now(),
                       updated_by=:archivedBy
                 WHERE id=:id
                   AND archived_at IS NULL
                """,
            Map.of("id", sourceId, "archivedBy", archivedBy));
    if (archivedCount != 1) {
      throw new GlobalOpenCopException("Data source not found", HttpStatus.NOT_FOUND);
    }

    jdbc.update(
        """
        DELETE FROM profiles.source_runtime_observation WHERE source_id = :id
        """,
        Map.of("id", sourceId));
    jdbc.update(
        """
        DELETE FROM profiles.socket_source_restart WHERE source_id = :id
        """,
        Map.of("id", sourceId));
  }

  @Override
  public Instant setAssignedInstance(UUID sourceId, String instanceId, UUID actorId) {
    // updated_by is written, not left alone: this statement moves updated_at, and a stale actor
    // beside a fresh timestamp would attribute an automated reassignment to the last human editor.
    var params = new HashMap<String, Object>();
    params.put("id", sourceId);
    params.put("instanceId", instanceId);
    params.put("actorId", actorId);

    return jdbc.queryForObject(
            """
            UPDATE profiles.data_source
               SET assigned_instance=:instanceId, updated_at=now(), updated_by=:actorId
             WHERE id=:id
            RETURNING updated_at
            """,
            params,
            OffsetDateTime.class)
        .toInstant();
  }

  @Override
  public void clearAssignedInstance(UUID sourceId, UUID actorId) {
    var params = new HashMap<String, Object>();
    params.put("id", sourceId);
    params.put("actorId", actorId);

    jdbc.update(
        """
            UPDATE profiles.data_source
               SET assigned_instance=NULL, updated_at=now(), updated_by=:actorId
             WHERE id=:id
            """,
        params);
  }

  // JDBC like the rest of this repository: no entity maps setup_prefill or connection_spec, which
  // this class writes and reads with the application's ObjectMapper. TODO: move the setup
  // operations to JPA once an entity maps those JSONB columns with that same serialization.
  @Override
  public void setSetupPrefill(UUID sourceId, SetupPrefill prefill) {
    jdbc.update(
        "UPDATE profiles.data_source SET setup_prefill = :prefill::jsonb WHERE id = :id",
        Map.of("id", sourceId, "prefill", serializePrefill(prefill)));
  }

  @Override
  public Optional<SetupPrefill> findSetupPrefill(UUID sourceId) {
    return findSetupPrefills(List.of(sourceId)).values().stream().findFirst();
  }

  @Override
  public Map<UUID, SetupPrefill> findSetupPrefills(Collection<UUID> sourceIds) {
    if (sourceIds.isEmpty()) {
      return Map.of();
    }
    var found = new HashMap<UUID, SetupPrefill>();
    jdbc.query(
        """
        SELECT id, setup_prefill::text AS setup_prefill
          FROM profiles.data_source
         WHERE id IN (:ids) AND setup_prefill IS NOT NULL
        """,
        Map.of("ids", sourceIds),
        row -> {
          found.put(
              row.getObject("id", UUID.class),
              objectMapper
                  .readValue(row.getString("setup_prefill"), SetupPrefillDocument.class)
                  .toPrefill());
        });
    return found;
  }

  private String serializePrefill(SetupPrefill prefill) {
    return objectMapper.writeValueAsString(SetupPrefillDocument.of(prefill));
  }

  // JDBC for the same reason as the prefill, and so a listen-port violation is attributed as the
  // insert's is. TODO: move to JPA with the prefill operations.
  @Override
  public void updateSetup(UUID sourceId, StoredConnectionSetup setup, UUID updatedBy) {
    var access = setup.access();
    var params =
        new MapSqlParameterSource()
            .addValue("id", sourceId)
            .addValue("spec", serializeSpec(setup.spec()))
            .addValue("integrationId", setup.integration().integrationId())
            .addValue("connectionSchemaVersion", setup.integration().schemaVersion())
            .addValue("access", access.mode().getAccess())
            .addValue("ingressMapperId", access.ingressMapperId().orElse(null))
            .addValue("egressMapperId", access.egressMapperId().orElse(null))
            .addValue("prefill", serializePrefill(setup.prefill()))
            .addValue("updatedBy", updatedBy);
    int updated;
    try {
      updated =
          jdbc.update(
              """
              UPDATE profiles.data_source
                 SET connection_spec = :spec::jsonb,
                     integration_id = :integrationId,
                     connection_schema_version = :connectionSchemaVersion,
                     access = :access::profiles.access_mode,
                     ingress_mapper_id = :ingressMapperId,
                     egress_mapper_id = :egressMapperId,
                     setup_prefill = :prefill::jsonb,
                     updated_at = now(),
                     updated_by = :updatedBy
               WHERE id = :id
              """,
              params);
    } catch (DataIntegrityViolationException e) {
      throw attributeSetup(sourceId, setup, e);
    }
    if (updated != 1) {
      throw new IllegalStateException("Locked data source disappeared: " + sourceId);
    }
  }

  private String serializeSpec(ConnectionSpec spec) {
    return objectMapper.writeValueAsString(spec);
  }
}
