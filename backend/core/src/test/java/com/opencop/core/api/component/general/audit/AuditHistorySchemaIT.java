// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.FullyQualifiedConfigurationContextInitializer;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.sdk.component.ComponentWebIT;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

/**
 * The Java enums and V61's CHECK constraints have to name the same values.
 *
 * <p>They are two copies of one vocabulary with nothing linking them. A value the database accepts
 * but the enum does not is the worse direction: the reader calls {@code valueOf} on what it reads,
 * so a single unknown string fails the whole page with a 500 rather than one row. The reverse — an
 * enum value the CHECK rejects — turns a recordable action into a failed one, because the audit
 * write joins the caller's transaction.
 */
@ComponentWebIT(classes = AuditWebTestApplication.class)
@ContextConfiguration(initializers = FullyQualifiedConfigurationContextInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditHistorySchemaIT extends AbstractWebIT {

  /** The quoted literals a {@code ... IN (...)} CHECK is made of. */
  private static final Pattern LITERAL = Pattern.compile("'([A-Z_]+)'");

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void theEntityTypeCheckNamesExactlyTheEntityTypeEnum() {
    assertThat(literalsOf("audit_history_entity_type"))
        .containsExactlyInAnyOrderElementsOf(names(EntityType.values()));
  }

  @Test
  void theOperationCheckNamesExactlyTheOperationEnum() {
    assertThat(literalsOf("audit_history_operation"))
        .containsExactlyInAnyOrderElementsOf(names(Operation.values()));
  }

  /**
   * Each per-entity vocabulary is a subset of the global one, so a typo in one of them cannot admit
   * an operation the reader has no enum constant for.
   */
  @Test
  void everyPerEntityCheckIsASubsetOfTheOperationEnum() {
    var operations = names(Operation.values());
    for (var constraint :
        new String[] {
          "audit_history_data_source_operations",
          "audit_history_layer_operations",
          "audit_history_layer_group_operations",
          "audit_history_mapper_profile_operations"
        }) {
      // The entity type itself appears in the `entity_type <> '...'` guard, so it is excluded.
      var listed = literalsOf(constraint);
      listed.removeAll(names(EntityType.values()));
      assertThat(listed).as(constraint).isNotEmpty().isSubsetOf(operations);
    }
  }

  private Set<String> literalsOf(String constraintName) {
    var definition =
        jdbcTemplate.queryForObject(
            """
            SELECT pg_get_constraintdef(c.oid)
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = 'audit' AND t.relname = 'audit_history' AND c.conname = ?
            """,
            String.class,
            constraintName);
    assertThat(definition).as("constraint %s exists", constraintName).isNotNull();

    var literals = new LinkedHashSet<String>();
    var matcher = LITERAL.matcher(definition);
    while (matcher.find()) {
      literals.add(matcher.group(1));
    }
    return literals;
  }

  private static Set<String> names(Enum<?>[] values) {
    return Arrays.stream(values)
        .map(Enum::name)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
