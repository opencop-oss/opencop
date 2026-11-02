// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.testing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** Assertions for production bootstraps that stop before application sources are loaded. */
public final class SpringApplicationBootstrapAssertions {

  private SpringApplicationBootstrapAssertions() {}

  public static void assertConfiguredApplication(
      SpringApplication application,
      BeanNameGenerator expectedGenerator,
      Object... expectedSources) {
    assertThat(application.getAllSources()).containsExactlyInAnyOrder(expectedSources);

    var observedGenerator = new AtomicReference<>();
    application.setBannerMode(Banner.Mode.OFF);
    application.setLogStartupInfo(false);
    application.setRegisterShutdownHook(false);
    application.addInitializers(
        context -> {
          observedGenerator.set(
              context
                  .getBeanFactory()
                  .getSingleton(AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR));
          throw new BootstrapInspectionComplete();
        });

    assertThatThrownBy(
            () -> application.run("--logging.level.org.springframework.boot.SpringApplication=OFF"))
        .isExactlyInstanceOf(BootstrapInspectionComplete.class);
    assertThat(observedGenerator).hasValue(expectedGenerator);
  }

  /** Parses the real application before any ordinary singleton can perform external I/O. */
  public static void assertScannedRedisConfiguration(
      SpringApplication application, String mode, boolean websocket) {
    assertScannedRedisConfiguration(
        application, mode, mode.equals("CLUSTER") ? "CLUSTER" : "STANDALONE", websocket);
  }

  /** Also accepts raw mode values whose parsed topology differs from the literal property. */
  public static void assertScannedRedisConfiguration(
      SpringApplication application, String mode, String expectedMode, boolean websocket) {
    var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    var properties = new LinkedHashMap<String, Object>();
    properties.put("spring.config.name", "redis-scan-proof");
    properties.put("spring.main.web-application-type", "none");
    properties.put("spring.main.banner-mode", "off");
    properties.put("logging.level.org.springframework.boot.SpringApplication", "OFF");
    properties.put("redis.host", "127.0.0.1");
    properties.put("redis.port", "1");
    properties.put("redis.ssl", "false");
    if (websocket) properties.put("opencop.geo.directory-repair.cron", "-");
    if (!mode.equals("ABSENT")) properties.put("redis.mode", mode);
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("syntheticRedisScan", properties));
    assertThat(environment.containsProperty("redis.mode")).isEqualTo(!mode.equals("ABSENT"));
    // These direct production bootstraps must not discover @Configuration fixtures from
    // test output or the SDK test jar, neither of which exists on a deployed classpath.
    var productionResources =
        new ClassLoader(SpringApplicationBootstrapAssertions.class.getClassLoader()) {
          private boolean isProduction(URL resource) {
            var location = resource.toExternalForm();
            return !location.contains("/target/test-classes/") && !location.contains("-tests.jar!");
          }

          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            return Collections.enumeration(
                Collections.list(super.getResources(name)).stream()
                    .filter(this::isProduction)
                    .toList());
          }

          @Override
          public URL getResource(String name) {
            var resource = super.getResource(name);
            return resource != null && isProduction(resource) ? resource : null;
          }
        };
    application.setResourceLoader(new DefaultResourceLoader(productionResources));
    application.setEnvironment(environment);
    application.setLogStartupInfo(false);
    application.setRegisterShutdownHook(false);
    var observed = new AtomicBoolean();
    application.addInitializers(
        context ->
            context.addBeanFactoryPostProcessor(
                factory -> {
                  assertThat(context.getEnvironment().containsProperty("redis.mode"))
                      .isEqualTo(!mode.equals("ABSENT"));
                  if (expectedMode.equals("CLUSTER")) {
                    assertThat(context.getEnvironment().getProperty("redis.mode"))
                        .isEqualTo("CLUSTER");
                  } else if (!mode.equals("ABSENT")) {
                    assertThat(context.getEnvironment().getProperty("redis.mode")).isEqualTo(mode);
                  }
                  var layout = factory.getBean(EventGeoCacheKeyLayout.class);
                  assertThat(layout.name()).isEqualTo(expectedMode);
                  var historicalControlLayout =
                      factory.getBean(HistoricalJobControlKeyLayout.class);
                  assertThat(historicalControlLayout.name()).isEqualTo(expectedMode);
                  assertThat(factory.getBeanNamesForType(RedisConnectionFactory.class, true, false))
                      .hasSize(1);
                  assertThat(
                          factory.getBeanNamesForType(
                              RedisEventGeoSourceDirectory.class, true, false))
                      .hasSize(1);
                  assertThat(
                          Arrays.stream(factory.getBeanDefinitionNames())
                              .map(factory::getBeanDefinition)
                              .anyMatch(
                                  definition ->
                                      "geoDirectoryRepairJob"
                                          .equals(definition.getFactoryMethodName())))
                      .isEqualTo(websocket && expectedMode.equals("CLUSTER"));
                  observed.set(true);
                  throw new BootstrapInspectionComplete();
                }));
    assertThatThrownBy(application::run).isExactlyInstanceOf(BootstrapInspectionComplete.class);
    assertThat(observed).isTrue();
  }

  private static final class BootstrapInspectionComplete extends RuntimeException {

    private BootstrapInspectionComplete() {
      super(null, null, false, false);
    }
  }
}
