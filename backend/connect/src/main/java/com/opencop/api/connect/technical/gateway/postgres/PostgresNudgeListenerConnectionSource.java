// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.postgres;

import com.opencop.api.connect.technical.config.ConnectProperties;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

/**
 * Owns the small, default one-connection pool reserved for Postgres LISTEN/NOTIFY nudges.
 *
 * <p>LISTEN is tied to a physical connection, so the nudge listener holds one connection open for
 * each Connect replica. Keeping it in a separate pool makes that reserved connection explicit
 * instead of silently pinning one from the normal query pool.
 */
public class PostgresNudgeListenerConnectionSource implements AutoCloseable {

  private final HikariDataSource dataSource;

  public PostgresNudgeListenerConnectionSource(
      DataSourceProperties dataSourceProperties,
      ConnectProperties.PostgresListener.DataSource listenerDataSourceProperties) {
    dataSource =
        dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    // LISTEN uses a dedicated long-lived read; unlike the main query pool, it must not use
    // socketTimeout=5s. PostgreSQL JDBC socketTimeout=0 means no socket read timeout.
    dataSource.addDataSourceProperty("socketTimeout", "0");
    var maximumPoolSize = Math.max(1, listenerDataSourceProperties.getMaximumPoolSize());
    dataSource.setMaximumPoolSize(maximumPoolSize);
    dataSource.setMinimumIdle(
        Math.min(maximumPoolSize, Math.max(0, listenerDataSourceProperties.getMinimumIdle())));
    dataSource.setConnectionTimeout(
        positive(listenerDataSourceProperties.getConnectionTimeout(), Duration.ofSeconds(10))
            .toMillis());
    dataSource.setPoolName(listenerDataSourceProperties.getPoolName());
  }

  Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }

  @Override
  public void close() {
    dataSource.close();
  }

  private static Duration positive(Duration value, Duration fallback) {
    return value == null || value.isZero() || value.isNegative() ? fallback : value;
  }
}
