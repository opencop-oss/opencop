// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.scim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScimHelperTest {

  @Test
  void parse_singleEq_doubleQuotes() {
    var f = ScimHelper.parseFilterSafe("displayName eq \"Admins\"").orElseThrow();
    assertEquals("displayName", f.field());
    assertEquals(List.of("Admins"), f.values());
  }

  @Test
  void parse_singleEq_singleQuotes() {
    var f = ScimHelper.parseFilterSafe("displayName eq 'Admins'").orElseThrow();
    assertEquals(List.of("Admins"), f.values());
  }

  @Test
  void parse_singleEq_unquoted() {
    var f = ScimHelper.parseFilterSafe("displayName eq Admins").orElseThrow();
    assertEquals(List.of("Admins"), f.values());
  }

  @Test
  void parse_orChain_sameField() {
    var f =
        ScimHelper.parseFilterSafe("displayName eq \"Admins\" or displayName eq \"Ops\"")
            .orElseThrow();
    assertEquals("displayName", f.field());
    assertEquals(List.of("Admins", "Ops"), f.values());
  }

  @Test
  void parse_orChain_mixedFields_rejected() {
    assertTrue(
        ScimHelper.parseFilterSafe("displayName eq \"Admins\" or id eq \"kc-ops\"").isEmpty());
  }

  @Test
  void parse_unsupportedOperator_rejected() {
    assertTrue(ScimHelper.parseFilterSafe("displayName co \"Adm\"").isEmpty());
  }

  @Test
  void parse_parentheses_rejected() {
    assertTrue(ScimHelper.parseFilterSafe("(displayName eq \"Admins\")").isEmpty());
  }

  @Test
  void parse_and_rejected() {
    assertTrue(
        ScimHelper.parseFilterSafe("displayName eq \"Admins\" and displayName eq \"Ops\"")
            .isEmpty());
  }
}
