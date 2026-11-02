// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.support;

import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.component.redis.RedisComponentSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class AbstractProcessorKafkaDatabaseIT extends AbstractDatabaseIT {

  @DynamicPropertySource
  static void registerRedisProperties(DynamicPropertyRegistry registry) {
    RedisComponentSupport.registerRedisProperties(registry);
  }

  @Autowired private DataSource dataSource;

  protected JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetProcessorDatabase() {
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("TRUNCATE TABLE geo.features CASCADE");
  }
}
