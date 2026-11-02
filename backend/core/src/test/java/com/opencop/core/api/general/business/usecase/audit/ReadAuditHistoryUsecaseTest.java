// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.audit.AuditPage;
import com.opencop.core.api.general.business.model.audit.AuditQuery;
import com.opencop.core.api.general.ports.gateway.audit.AuditReader;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What the ledger refuses to answer, and that everything else reaches the reader untouched. */
class ReadAuditHistoryUsecaseTest {

  private AuditReader reader;
  private ReadAuditHistoryUsecase usecase;

  @BeforeEach
  void setUp() {
    reader = mock(AuditReader.class);
    usecase = new ReadAuditHistoryUsecase(reader);
  }

  @Test
  void passesAValidQueryThroughUnchanged() {
    var query = query(2, 50, null, null);
    when(reader.findPage(any())).thenReturn(new AuditPage(List.of(), 0));

    usecase.read(query);

    verify(reader).findPage(query);
  }

  @Test
  void rejectsANegativePage() {
    assertThatThrownBy(() -> usecase.read(query(-1, 25, null, null)))
        .isInstanceOf(GlobalOpenCopException.class)
        .hasMessageContaining("page");
    verifyNoInteractions(reader);
  }

  @Test
  void rejectsASizeOutsideTheBounds() {
    assertThatThrownBy(() -> usecase.read(query(0, 0, null, null)))
        .isInstanceOf(GlobalOpenCopException.class)
        .hasMessageContaining("size");

    assertThatThrownBy(() -> usecase.read(query(0, AuditQuery.MAXIMUM_SIZE + 1, null, null)))
        .isInstanceOf(GlobalOpenCopException.class)
        .hasMessageContaining("size");

    verifyNoInteractions(reader);
  }

  /**
   * Spring Data computes the offset as an int, so without this guard the repository throws and the
   * caller gets a 500 for a query that was simply unanswerable.
   */
  @Test
  void rejectsAPagePastTheOffsetRange() {
    assertThatThrownBy(() -> usecase.read(query(Integer.MAX_VALUE, 2, null, null)))
        .isInstanceOf(GlobalOpenCopException.class)
        .hasMessageContaining("supported range");
    verifyNoInteractions(reader);
  }

  /** The last offset that still fits is answered rather than refused. */
  @Test
  void acceptsTheLargestPageThatStillFitsTheOffsetRange() {
    var query = query(Integer.MAX_VALUE / 100, AuditQuery.MAXIMUM_SIZE, null, null);
    when(reader.findPage(any())).thenReturn(new AuditPage(List.of(), 0));

    assertThat(usecase.read(query)).isNotNull();
  }

  @Test
  void rejectsAnInvertedTimeRange() {
    assertThatThrownBy(
            () ->
                usecase.read(
                    query(
                        0,
                        25,
                        Instant.parse("2026-02-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"))))
        .isInstanceOf(GlobalOpenCopException.class)
        .hasMessageContaining("occurredFrom");
    verifyNoInteractions(reader);
  }

  @Test
  void acceptsEqualBoundsBecauseBothAreInclusive() {
    var moment = Instant.parse("2026-01-01T00:00:00Z");
    when(reader.findPage(any())).thenReturn(new AuditPage(List.of(), 0));

    assertThat(usecase.read(query(0, 25, moment, moment))).isNotNull();
  }

  private static AuditQuery query(int page, int size, Instant from, Instant to) {
    return new AuditQuery(null, null, null, null, from, to, page, size);
  }
}
