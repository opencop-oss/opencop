// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support;

import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Delegates to the real store, then optionally fails once the row is already written. */
public final class FailAfterPersistRecorder implements AuditRecorder {

  private final AuditRecorder delegate;
  private final JdbcTemplate jdbcTemplate;
  private final AtomicBoolean transactionActiveAtInvocation = new AtomicBoolean();
  private final AtomicInteger completedDelegateCalls = new AtomicInteger();
  private final AtomicInteger rowsVisibleBeforeFailure = new AtomicInteger(-1);
  private final AtomicReference<Operation> selectedFailure = new AtomicReference<>();
  private final AtomicReference<Operation> selectedCheckedFailure = new AtomicReference<>();

  public FailAfterPersistRecorder(AuditRecorder delegate, JdbcTemplate jdbcTemplate) {
    this.delegate = delegate;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void record(AuditEvent event) {
    transactionActiveAtInvocation.set(
        TransactionSynchronizationManager.isActualTransactionActive());
    delegate.record(event);
    completedDelegateCalls.incrementAndGet();
    rowsVisibleBeforeFailure.set(
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit.audit_history WHERE entity_id = ? AND operation = ?",
            Integer.class,
            event.entityId(),
            event.operation().name()));
    if (selectedFailure.compareAndSet(event.operation(), null)) {
      throw new IllegalStateException("induced failure after audit persistence");
    }
    if (selectedCheckedFailure.compareAndSet(event.operation(), null)) {
      sneakyThrow(new PersistenceException("induced checked failure after audit persistence"));
    }
  }

  public void failNext(Operation operation) {
    selectedFailure.set(operation);
  }

  /**
   * Fails with a <em>checked</em> exception instead.
   *
   * <p>Spring rolls back unchecked exceptions by default, so an unchecked failure proves nothing
   * about a method that declares a checked one: the rollback would happen with or without its
   * {@code rollbackFor}. Only a checked exception escaping tells the two apart.
   *
   * <p>{@link AuditRecorder#record} cannot declare it, so it is thrown past the compiler. The
   * transaction interceptor sees the exception that was actually thrown, which is what is under
   * test here.
   */
  public void failNextWithCheckedException(Operation operation) {
    selectedCheckedFailure.set(operation);
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void sneakyThrow(Throwable thrown) throws E {
    throw (E) thrown;
  }

  public void resetObservations() {
    transactionActiveAtInvocation.set(false);
    completedDelegateCalls.set(0);
    rowsVisibleBeforeFailure.set(-1);
    selectedFailure.set(null);
    selectedCheckedFailure.set(null);
  }

  public int completedDelegateCalls() {
    return completedDelegateCalls.get();
  }

  public boolean transactionActiveAtInvocation() {
    return transactionActiveAtInvocation.get();
  }

  public int rowsVisibleBeforeFailure() {
    return rowsVisibleBeforeFailure.get();
  }
}
