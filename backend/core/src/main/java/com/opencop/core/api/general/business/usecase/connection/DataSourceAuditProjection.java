// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection;

import com.opencop.core.api.general.business.model.audit.AuditChange;
import com.opencop.core.api.general.business.model.audit.AuditDeltas;
import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.ValidatedConnectionMetadataPatch;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.datasource.DataSource;
import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The safe projection of a data source change.
 *
 * <p>A data source holds the most dangerous material in the product: broker credentials, client
 * certificates, keystores and Vault references. None of it reaches this ledger. A field whose value
 * must not be stored is recorded <em>by name only</em>, so history still says that a credential was
 * rotated without saying what it was rotated to.
 */
@Component
public class DataSourceAuditProjection {

  /** Creation, recording only the metadata a reader needs to recognise the source. */
  public AuditChange create(String sourceName, Enum<?> accessMode) {
    return AuditChange.builder()
        .field("name", null, sourceName)
        .field("access", null, accessMode)
        .build();
  }

  /** The metadata fields a patch actually moved; the caller has already discarded no-ops. */
  public AuditChange patch(ConnectionMetadata before, ValidatedConnectionMetadataPatch effective) {
    var change = AuditChange.builder();
    if (effective.name() != null) {
      change.moved("name", before.name(), effective.name());
    }
    if (effective.defaultVisible() != null) {
      change.moved("defaultVisible", before.defaultVisible(), effective.defaultVisible());
    }
    if (effective.access() != null) {
      change.moved("access", before.access(), effective.access());
    }
    if (effective.integration() != null) {
      change.moved(
          "integration",
          integrationText(before.integration()),
          integrationText(effective.integration()));
    }
    return change.build();
  }

  /**
   * The integration reference as stable text, so the row does not depend on a record's {@code
   * toString}.
   */
  private static String integrationText(IntegrationConnectionSchemaReference reference) {
    return reference == null ? null : reference.integrationId() + " v" + reference.schemaVersion();
  }

  /**
   * A replaced setup, by field name only.
   *
   * <p>Extractor inputs, certificates and keystores are declared secret by the integration and must
   * never be echoed back or logged, so the names of the fields are all this records. Settings carry
   * no sensitivity marker at all and their schema comes from plugins, which are not trusted for
   * this purpose — so they are recorded by name too rather than by value.
   *
   * <p>These are the names the request <em>supplied</em>, not the ones whose values moved. A setup
   * replace rewrites the whole setup, so every supplied field was written; and a secret cannot be
   * compared against anything, because its old value was never stored here to compare with.
   */
  public AuditChange setupReplaced(
      Collection<String> suppliedFields, Collection<String> settingNames) {
    return AuditChange.builder()
        .nameList("fieldsSupplied", suppliedFields)
        .nameList("settingsSupplied", settingNames)
        .detail("valuesOmitted", true)
        .build();
  }

  /**
   * The mappers attached to, swapped on, or detached from this source.
   *
   * <p>One row for the request, not one per direction: changing what a connection reads and what it
   * writes in the same call is one thing the administrator did. A direction that did not move is
   * left out.
   */
  public AuditChange mapperChanged(
      UUID beforeIngress, UUID afterIngress, UUID beforeEgress, UUID afterEgress) {
    return AuditChange.builder()
        .moved("ingressMapperId", beforeIngress, afterIngress)
        .moved("egressMapperId", beforeEgress, afterEgress)
        .build();
  }

  /**
   * A change that moves no field, so the row carries only the transport the source runs on. Used
   * for start, stop and archive; the operation on the row says which.
   */
  public AuditChange transportOnly(DataSource dataSource) {
    return AuditChange.builder().detail("transportKind", dataSource.provider()).build();
  }

  /**
   * Permission changes, as they actually applied.
   *
   * <p>The access services skip ids they cannot resolve, so the caller passes what was applied
   * rather than what was asked for.
   */
  public AuditChange roleSetChanged(AuditDeltas.Delta<?> delta) {
    return AuditChange.ofDelta(delta);
  }

  /** An export, recorded when it is requested rather than when the file is fetched. */
  public AuditChange exported(String format, UUID exportId, int subjectCount) {
    return AuditChange.exported(format, exportId, subjectCount);
  }
}
