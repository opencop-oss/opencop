// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support.fixture;

import com.opencop.core.sdk.model.datasource.AccessMode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The egress mapper a writable test connection is stored with. A writable connection must name an
 * egress mapper, and these tests never encode through it, so one shared identity serves them all.
 */
final class TestEgressMapper {

  static final UUID ID = UUID.fromString("7b1f0c55-4f7e-4a7a-9d8e-2f4e5a6b7c81");

  private TestEgressMapper() {}

  /** The egress mapper a connection with the access holds, registered on first use. */
  static UUID forAccess(JdbcTemplate jdbc, AccessMode access) {
    if (!access.canWrite()) {
      return null;
    }
    jdbc.update(
        """
        INSERT INTO profiles.mapper (id, qualifier)
        VALUES (?, 'EGRESS'::profiles.mapper_qualifier)
        ON CONFLICT (id) DO NOTHING
        """,
        ID);
    return ID;
  }
}
