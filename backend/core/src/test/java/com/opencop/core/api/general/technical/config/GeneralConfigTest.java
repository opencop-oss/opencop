// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.technical.gateway.api.PostgresConnectWorkerHealthRepository;
import com.opencop.core.sdk.technical.gateway.api.PostgresSourceRuntimeObservationRepository;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class GeneralConfigTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock private NamedParameterJdbcTemplate jdbc;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private EventGeoCacheKeyLayout layout;
  @Mock private RedisEventGeoSourceDirectory sourceDirectory;
  @Mock private ZSetOperations<String, String> zSetOperations;

  @Test
  void sharedBeanFactories_whenCreated_retainExplicitNamesAndImplementations() throws Exception {
    var config = new GeneralConfig();

    assertBeanName("connectInstanceResolver", "connectInstanceResolver");
    assertBeanName("connectWorkerHealthMaintenance", "connectWorkerHealthMaintenance");
    assertBeanName("sourceRuntimeObservationReader", "sourceRuntimeObservationReader");
    assertBeanName("sourceRuntimeObservationMaintenance", "sourceRuntimeObservationMaintenance");

    assertThat(config.connectInstanceResolver(jdbc))
        .isInstanceOf(PostgresConnectWorkerHealthRepository.class);
    assertThat(config.connectWorkerHealthMaintenance(jdbc))
        .isInstanceOf(PostgresConnectWorkerHealthRepository.class);
    assertThat(config.sourceRuntimeObservationReader(jdbc))
        .isInstanceOf(PostgresSourceRuntimeObservationRepository.class);
    assertThat(config.sourceRuntimeObservationMaintenance(jdbc))
        .isInstanceOf(PostgresSourceRuntimeObservationRepository.class);
  }

  @Test
  void eventGeoCacheEvictionGateway_forwardsSelectedLayoutAndDirectory() {
    var sourceKeys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(SOURCE_ID);
    when(layout.sourceKeys(SOURCE_ID)).thenReturn(sourceKeys);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.range(sourceKeys.updatedAtKey(), 0, -1)).thenReturn(Set.of());
    when(zSetOperations.range(sourceKeys.staleAtKey(), 0, -1)).thenReturn(Set.of());
    when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(List.of());

    var gateway =
        new GeneralConfig().eventGeoCacheEvictionGateway(redisTemplate, layout, sourceDirectory);
    gateway.evictGeoSource(SOURCE_ID);

    verify(layout).sourceKeys(SOURCE_ID);
    verify(sourceDirectory).unregisterIfEmpty(sourceKeys);
  }

  private static void assertBeanName(String methodName, String expectedName) throws Exception {
    var bean = GeneralConfig.class.getDeclaredMethod(methodName, NamedParameterJdbcTemplate.class);
    assertThat(bean.getAnnotation(Bean.class).value()).containsExactly(expectedName);
  }
}
