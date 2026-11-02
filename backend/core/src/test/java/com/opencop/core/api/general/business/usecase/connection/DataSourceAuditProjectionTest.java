// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.audit.AuditChange;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataSourceAuditProjectionTest {

  private final DataSourceAuditProjection projection = new DataSourceAuditProjection();

  @Test
  void setupReplaced_recordsFieldNamesOnlyAndFlagsThatValuesAreOmitted() {
    var change =
        projection.setupReplaced(List.of("clientCertificate", "host"), List.of("presence"));

    assertThat(change.changed()).isEmpty();
    assertThat(change.details())
        .containsEntry(
            "fieldsSupplied", new AuditChange.NameList(List.of("clientCertificate", "host")))
        .containsEntry("settingsSupplied", new AuditChange.NameList(List.of("presence")))
        .containsEntry("valuesOmitted", true);
  }

  @Test
  void transportOnly_recordsTheTransportTheSourceRunsOn() {
    var source = mock(KafkaDataSource.class);
    when(source.provider()).thenReturn(TransportKind.KAFKA);

    assertThat(projection.transportOnly(source).details())
        .containsEntry("transportKind", TransportKind.KAFKA.name());
  }

  @Test
  void create_storesTheAccessModeByEnumName() {
    var access = AccessMode.values()[0];

    var change = projection.create("Feed", access);

    assertThat(change.changed())
        .contains(new AuditChange.FieldChange("access", null, access.name()));
  }
}
