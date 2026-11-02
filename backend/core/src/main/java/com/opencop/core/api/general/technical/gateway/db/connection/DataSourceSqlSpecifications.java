// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection;

import com.opencop.core.sdk.model.datasource.DataSourceReadFilter;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * SQL equivalent of the layer JPA specifications.
 *
 * <p>Data sources still use JDBC because they are mapped into multiple domain record types and have
 * JSONB-backed Kafka metadata filters. Moving this repository to JPA is a larger cleanup outside
 * the lifecycle/archive work, so keep the filtering rules isolated here until that migration is
 * done.
 */
final class DataSourceSqlSpecifications {

  /**
   * Shared projection for every data-source read, so the two call sites cannot drift.
   *
   * <p>{@code last_event_at} is joined from {@code activity.resource_activity} rather than read off
   * the data-source row: a source with no activity yet has no row there, so the join yields null.
   *
   * <p>Predicates below are deliberately unqualified, which is safe today. Note that the projection
   * table also has an {@code updated_at}, so a future predicate or ordering on that column must be
   * written {@code ds.updated_at} or Postgres will reject it as ambiguous.
   */
  static final String SELECT_DATA_SOURCE =
      """
      SELECT ds.*, ra.last_event_at AS last_event_at
        FROM profiles.data_source ds
        LEFT JOIN activity.resource_activity ra
               ON ra.resource_kind = 'DATA_SOURCE'
              AND ra.resource_id = ds.id\
      """;

  private DataSourceSqlSpecifications() {}

  static SelectSql select(DataSourceReadFilter filter) {
    var effectiveFilter =
        filter == null
            ? new DataSourceReadFilter(null, Set.of(), RecordVisibility.ACTIVE, null, null, null)
            : filter;

    var predicates = new ArrayList<String>();
    var params = new HashMap<String, Object>();

    appendIdPredicate(effectiveFilter, predicates, params);
    appendIdsPredicate(effectiveFilter, predicates, params);
    appendRecordVisibilityPredicate(effectiveFilter.recordVisibilityOrDefault(), predicates);
    appendBrokerProviderPredicate(effectiveFilter, predicates, params);
    appendEnabledPredicate(effectiveFilter, predicates, params);
    appendAssignedInstancePredicate(effectiveFilter, predicates, params);

    var sql = new StringBuilder(SELECT_DATA_SOURCE);
    if (!predicates.isEmpty()) {
      sql.append(" WHERE ");
      sql.append(String.join(" AND ", predicates));
    }
    sql.append(" ORDER BY created_at DESC");

    return new SelectSql(sql.toString(), params);
  }

  private static void appendIdPredicate(
      DataSourceReadFilter filter, ArrayList<String> predicates, Map<String, Object> params) {
    if (filter.id() == null) {
      return;
    }
    predicates.add("id = :id");
    params.put("id", filter.id());
  }

  private static void appendIdsPredicate(
      DataSourceReadFilter filter, ArrayList<String> predicates, Map<String, Object> params) {
    if (!filter.hasIds()) {
      return;
    }
    predicates.add("id IN (:ids)");
    params.put("ids", filter.ids());
  }

  private static void appendRecordVisibilityPredicate(
      RecordVisibility recordVisibility, ArrayList<String> predicates) {
    switch (recordVisibility) {
      case ACTIVE -> predicates.add("archived_at IS NULL");
      case ARCHIVED -> predicates.add("archived_at IS NOT NULL");
      case ALL -> {}
    }
  }

  private static void appendBrokerProviderPredicate(
      DataSourceReadFilter filter, ArrayList<String> predicates, Map<String, Object> params) {
    if (filter.brokerProvider() == null) {
      return;
    }
    predicates.add(
        "(provider != 'KAFKA'::profiles.transport_kind OR connection_spec->>'brokerProvider' = :currentProvider)");
    params.put("currentProvider", filter.brokerProvider());
  }

  private static void appendEnabledPredicate(
      DataSourceReadFilter filter, ArrayList<String> predicates, Map<String, Object> params) {
    if (filter.enabled() == null) {
      return;
    }
    predicates.add("enabled = :enabled");
    params.put("enabled", filter.enabled());
  }

  private static void appendAssignedInstancePredicate(
      DataSourceReadFilter filter, ArrayList<String> predicates, Map<String, Object> params) {
    if (filter.assignedInstance() == null) {
      return;
    }
    predicates.add("assigned_instance = :assignedInstance");
    params.put("assignedInstance", filter.assignedInstance());
  }

  record SelectSql(String sql, Map<String, Object> params) {}
}
