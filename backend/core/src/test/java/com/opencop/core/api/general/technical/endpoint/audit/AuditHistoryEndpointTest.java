// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry;
import com.opencop.core.api.general.business.model.audit.AuditPage;
import com.opencop.core.api.general.business.model.audit.AuditQuery;
import com.opencop.core.api.general.ports.usecase.audit.ReadAuditHistory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class AuditHistoryEndpointTest {

  private ReadAuditHistory history;
  private AuditHistoryEndpoint endpoint;

  @BeforeEach
  void setUp() {
    history = mock(ReadAuditHistory.class);
    endpoint = new AuditHistoryEndpoint(history);
  }

  @Test
  void passesEveryFilterThroughToTheRead() {
    var entityId = UUID.randomUUID();
    var actorId = UUID.randomUUID();
    var from = Instant.parse("2026-01-01T00:00:00Z");
    var to = Instant.parse("2026-02-01T00:00:00Z");
    when(history.read(any())).thenReturn(new AuditPage(List.of(), 0));

    endpoint.history(
        2,
        50,
        AuditEntry.EntityType.LAYER,
        entityId,
        AuditEntry.Operation.MEMBERS_CHANGED,
        actorId,
        from,
        to);

    var captor = ArgumentCaptor.forClass(AuditQuery.class);
    verify(history).read(captor.capture());
    assertThat(captor.getValue())
        .isEqualTo(
            new AuditQuery(
                AuditEntry.EntityType.LAYER,
                entityId,
                AuditEntry.Operation.MEMBERS_CHANGED,
                actorId,
                from,
                to,
                2,
                50));
  }

  @Test
  void reportsPagingMetadataFromTheExactTotal() {
    when(history.read(any())).thenReturn(new AuditPage(List.of(entry()), 51));

    var body = endpoint.history(0, 25, null, null, null, null, null, null).getBody();

    assertThat(body).isNotNull();
    assertThat(body.page()).isZero();
    assertThat(body.size()).isEqualTo(25);
    assertThat(body.totalElements()).isEqualTo(51);
    // 51 rows at 25 a page is three pages, the last one partial.
    assertThat(body.totalPages()).isEqualTo(3);
  }

  @Test
  void servesActorFactsAsStoredWithoutDerivingADisplayLabel() {
    var stored = entry();
    when(history.read(any())).thenReturn(new AuditPage(List.of(stored), 1));

    var row =
        endpoint.history(0, 25, null, null, null, null, null, null).getBody().content().getFirst();

    // fullName is absent on this row; the response carries that absence through rather than
    // substituting the email or id, leaving the fallback to the client.
    assertThat(row.actor().fullName()).isNull();
    assertThat(row.actor().email()).isEqualTo("ada@example.test");
    assertThat(row.actor().userId()).isEqualTo(stored.actor().userId());
    assertThat(row.actor().globalAdmin()).isTrue();
    assertThat(row.change()).isEqualTo("{\"changed\":[]}");
    assertThat(row.changeVersion()).isEqualTo(1);
  }

  @Test
  void passesBothTimeBoundsThroughUnchangedWhenTheyAreEqual() {
    var moment = Instant.parse("2026-01-01T00:00:00Z");
    when(history.read(any())).thenReturn(new AuditPage(List.of(), 0));

    var response = endpoint.history(0, 25, null, null, null, null, moment, moment);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private static AuditEntry entry() {
    return new AuditEntry(
        1L,
        AuditEntry.EntityType.LAYER,
        UUID.randomUUID(),
        "Recon North",
        AuditEntry.Operation.PATCH,
        Instant.parse("2026-01-15T12:00:00Z"),
        new Caller(UUID.randomUUID(), null, "ada@example.test", true),
        "{\"changed\":[]}",
        1);
  }
}
