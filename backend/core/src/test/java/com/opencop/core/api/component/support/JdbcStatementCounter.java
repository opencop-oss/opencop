// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Counts JDBC statement executions at the {@link DataSource} boundary so measurements see every
 * statement uniformly - Hibernate, JdbcTemplate, and raw JDBC alike (Hibernate statistics only
 * observe Hibernate's own statements). Each {@code execute*} call counts as one statement; a JDBC
 * batch counts as one. The counter is JVM-global: reset it immediately before the measured window.
 */
public final class JdbcStatementCounter {

  private static final AtomicLong EXECUTIONS = new AtomicLong();

  private JdbcStatementCounter() {}

  public static long executions() {
    return EXECUTIONS.get();
  }

  public static void reset() {
    EXECUTIONS.set(0);
  }

  /** Registers a wrapper around every {@link DataSource} bean in the test context. */
  @TestConfiguration(proxyBeanMethods = false)
  public static class Config {

    @Bean
    static BeanPostProcessor jdbcStatementCountingWrapper() {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
          if (bean instanceof DataSource dataSource && !Proxy.isProxyClass(bean.getClass())) {
            return wrap(dataSource);
          }
          return bean;
        }
      };
    }
  }

  static DataSource wrap(DataSource delegate) {
    return (DataSource)
        Proxy.newProxyInstance(
            JdbcStatementCounter.class.getClassLoader(),
            new Class<?>[] {DataSource.class},
            (_, method, args) -> {
              Object result = invoke(delegate, method, args);
              if ("getConnection".equals(method.getName()) && result instanceof Connection c) {
                return wrapConnection(c);
              }
              return result;
            });
  }

  private static Connection wrapConnection(Connection delegate) {
    return (Connection)
        Proxy.newProxyInstance(
            JdbcStatementCounter.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (_, method, args) -> {
              Object result = invoke(delegate, method, args);
              return switch (result) {
                case CallableStatement cs -> wrapStatement(cs, CallableStatement.class);
                case PreparedStatement ps -> wrapStatement(ps, PreparedStatement.class);
                case Statement s -> wrapStatement(s, Statement.class);
                case null, default -> result;
              };
            });
  }

  private static <S extends Statement> Statement wrapStatement(S delegate, Class<S> type) {
    return (Statement)
        Proxy.newProxyInstance(
            JdbcStatementCounter.class.getClassLoader(),
            new Class<?>[] {type},
            (_, method, args) -> {
              if (method.getName().startsWith("execute")) {
                EXECUTIONS.incrementAndGet();
              }
              return invoke(delegate, method, args);
            });
  }

  private static Object invoke(Object delegate, java.lang.reflect.Method method, Object[] args)
      throws Throwable {
    try {
      return method.invoke(delegate, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }
}
