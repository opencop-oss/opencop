// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.extension.RecordLimits;
import com.opencop.core.api.general.business.model.connection.extension.RecordScope;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.connection.extension.ConnectionRecordGateway;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.technical.gateway.db.connection.TransactionalConnectionLock;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.extension.ConnectionRecord;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExpectedRevision;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ConfinedRecordStoreTest {

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
  private static final IntegrationConnectionSchemaReference INTEGRATION =
      new IntegrationConnectionSchemaReference(
          UUID.fromString("0f7b8e7c-49b8-4d0e-8f43-4bb1b0f0a1c2"), 1);
  private static final IntegrationConnectionSchemaReference OTHER_INTEGRATION =
      new IntegrationConnectionSchemaReference(
          UUID.fromString("6c1f0f6e-0c38-4a5b-9c0e-3f4d2b8a7e91"), 1);
  private static final RecordScope SCOPE =
      new RecordScope(SOURCE_ID, INTEGRATION.integrationId(), "sync");
  private static final String COLLECTION = "subscriptions";
  private static final String KEY = "alpha";
  private static final Map<String, Object> DOCUMENT = Map.of("role", "subscriber");
  private static final Instant AT = Instant.parse("2026-10-13T00:00:00Z");

  private final ConnectionRecordGateway records = mock(ConnectionRecordGateway.class);
  private final ConnectionMetadataGateway connections = mock(ConnectionMetadataGateway.class);
  private final ConfinedRecordStore store =
      new ConfinedRecordStore(
          SCOPE,
          INTEGRATION,
          USER_ID,
          records,
          new TransactionalConnectionLock(
              connections, new TransactionTemplate(mock(PlatformTransactionManager.class))),
          new RecordLimits(1_000, 2));

  @Test
  void aWriteLocksTheConnectionBeforeReadingAndStoresTheNextRevisionAsTheUser() throws Exception {
    givenConnection(null);
    when(records.find(SCOPE, COLLECTION, KEY)).thenReturn(Optional.of(stored(4)));
    when(records.save(SCOPE, COLLECTION, KEY, DOCUMENT, 5, USER_ID)).thenReturn(stored(5));

    var saved = store.put(COLLECTION, KEY, DOCUMENT, ExpectedRevision.of(4));

    assertThat(saved.revision()).isEqualTo(5);
    var order = inOrder(connections, records);
    order.verify(connections).lockById(SOURCE_ID);
    order.verify(records).find(SCOPE, COLLECTION, KEY);
    order.verify(records).save(SCOPE, COLLECTION, KEY, DOCUMENT, 5, USER_ID);
    verify(records, never()).count(any());
  }

  @Test
  void aNewRecordCountsAgainstTheLimit() {
    givenConnection(null);
    when(records.find(SCOPE, COLLECTION, KEY)).thenReturn(Optional.empty());
    when(records.count(SCOPE)).thenReturn(2L);

    assertThatThrownBy(() -> store.put(COLLECTION, KEY, DOCUMENT, ExpectedRevision.absent()))
        .isInstanceOf(ConnectionExtensionException.class);
    verify(records, never()).save(any(), anyString(), anyString(), any(), anyLong(), any());
  }

  @Test
  void aStaleRevisionWritesNothing() {
    givenConnection(null);
    when(records.find(SCOPE, COLLECTION, KEY)).thenReturn(Optional.of(stored(4)));

    assertThatThrownBy(() -> store.put(COLLECTION, KEY, DOCUMENT, ExpectedRevision.of(3)))
        .isInstanceOf(ConnectionExtensionException.class);
    verify(records, never()).save(any(), anyString(), anyString(), any(), anyLong(), any());
  }

  @Test
  void aConnectionArchivedSinceTheInvocationStartedTakesNoWrite() {
    givenConnection(AT);

    assertThatThrownBy(() -> store.put(COLLECTION, KEY, DOCUMENT, ExpectedRevision.any()))
        .isInstanceOf(GlobalOpenCopException.class);
    verify(records, never()).find(any(), anyString(), anyString());
  }

  /**
   * A connection moved to another integration since the invocation started no longer offers the
   * extension, so its write is refused before anything is read or stored.
   */
  @Test
  void aConnectionMovedToAnotherIntegrationSinceTheInvocationStartedTakesNoWrite() {
    givenConnection(null, OTHER_INTEGRATION);

    assertThatThrownBy(() -> store.put(COLLECTION, KEY, DOCUMENT, ExpectedRevision.any()))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            error -> {
              assertThat(error.getCode())
                  .isEqualTo(ConnectionExceptionCodes.INTEGRATION_UNAVAILABLE.code());
              assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
            });
    verify(records, never()).find(any(), anyString(), anyString());
    verify(records, never()).save(any(), anyString(), anyString(), any(), anyLong(), any());
  }

  @Test
  void aNewerSchemaVersionOfTheSameIntegrationStillTakesWrites() throws Exception {
    givenConnection(null, new IntegrationConnectionSchemaReference(INTEGRATION.integrationId(), 2));
    when(records.find(SCOPE, COLLECTION, KEY)).thenReturn(Optional.empty());
    when(records.save(SCOPE, COLLECTION, KEY, DOCUMENT, 1, USER_ID)).thenReturn(stored(1));

    store.put(COLLECTION, KEY, DOCUMENT, ExpectedRevision.any());

    verify(records).save(SCOPE, COLLECTION, KEY, DOCUMENT, 1, USER_ID);
  }

  @Test
  void deletingAMissingRecordWithoutARevisionDoesNothing() throws Exception {
    givenConnection(null);
    when(records.find(SCOPE, COLLECTION, KEY)).thenReturn(Optional.empty());

    store.delete(COLLECTION, KEY, ExpectedRevision.any());

    verify(records, never()).delete(any(), anyString(), anyString());
  }

  @Test
  void deletingAtTheExpectedRevisionRemovesTheRecord() throws Exception {
    givenConnection(null);
    when(records.find(SCOPE, COLLECTION, KEY)).thenReturn(Optional.of(stored(2)));

    store.delete(COLLECTION, KEY, ExpectedRevision.of(2));

    verify(records).delete(SCOPE, COLLECTION, KEY);
  }

  @Test
  void readsStayWithinTheBoundScope() {
    when(records.find(SCOPE, COLLECTION, KEY)).thenReturn(Optional.of(stored(1)));

    assertThat(store.get(COLLECTION, KEY)).contains(stored(1));
    store.list(COLLECTION);

    verify(records).list(SCOPE, COLLECTION);
  }

  @Test
  void aBlankOrOverlongNameIsAnExtensionBug() {
    assertThatThrownBy(() -> store.get(" ", KEY)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> store.get(COLLECTION, "k".repeat(ConfinedRecordStore.MAX_NAME_LENGTH + 1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void givenConnection(Instant archivedAt) {
    givenConnection(archivedAt, INTEGRATION);
  }

  private void givenConnection(
      Instant archivedAt, IntegrationConnectionSchemaReference integration) {
    when(connections.lockById(SOURCE_ID))
        .thenReturn(
            new ConnectionMetadata(
                SOURCE_ID,
                "connection",
                null,
                TransportKind.TCP,
                AccessMode.READ,
                true,
                archivedAt,
                true,
                integration));
  }

  private static ConnectionRecord stored(long revision) {
    return new ConnectionRecord(COLLECTION, KEY, DOCUMENT, revision, AT, USER_ID, AT, USER_ID);
  }
}
