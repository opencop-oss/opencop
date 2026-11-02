// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry.Outcome;
import com.opencop.core.sdk.observability.history.HistoricalTraceContext;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalReadContext;
import com.opencop.processor.business.model.history.HistoricalReadCursor;
import com.opencop.processor.business.model.history.HistoricalReadPage;
import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkReader;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateReader;
import com.opencop.processor.technical.gateway.db.PostGisHistoricalActiveSnapshotReader.ActiveSnapshotReadPath;
import com.opencop.processor.technical.gateway.db.PostGisHistoricalActiveSnapshotReader.ActiveSnapshotReadResult;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.observability.history.HistoricalObservability.ActiveSnapshotReadOutcome;
import com.opencop.processor.technical.observability.history.HistoricalObservability.ActiveSnapshotRowKind;
import com.opencop.processor.technical.observability.history.HistoricalObservability.ReadPath;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class HistoricalPostGisChunkReadGateway
    implements HistoricalChunkReader, HistoricalChunkStateReader {

  private static final int DEFAULT_PAGE_SIZE = 5000;
  private static final String ACTIVE_SNAPSHOT_READ_SPAN = "history.active_snapshot.read";

  private final TransactionTemplate repeatableReadTransaction;
  private final PostGisHistoricalActiveSnapshotReader activeSnapshotReader;
  private final PostGisHistoricalDeltaReader deltaReader;
  private final PostGisHistoricalCheckpointReader checkpointReader;
  private final HistoricalObservability historicalObservability;
  private final Tracer tracer;
  private final int activeSnapshotQueryTimeoutSeconds;

  public HistoricalPostGisChunkReadGateway(
      NamedParameterJdbcTemplate jdbc,
      PlatformTransactionManager transactionManager,
      HistoricalObservability historicalObservability,
      HistoricalExecutionProperties properties,
      Tracer tracer) {
    this.activeSnapshotReader =
        new PostGisHistoricalActiveSnapshotReader(jdbc, properties.getActiveSnapshotQueryTimeout());
    this.deltaReader =
        new PostGisHistoricalDeltaReader(jdbc, properties.getActiveSnapshotQueryTimeout());
    this.checkpointReader =
        new PostGisHistoricalCheckpointReader(
            jdbc, deltaReader, properties.getActiveSnapshotQueryTimeout());
    this.historicalObservability = historicalObservability;
    this.tracer = tracer;
    this.activeSnapshotQueryTimeoutSeconds =
        JdbcQueryTimeout.seconds(properties.getActiveSnapshotQueryTimeout());
    this.repeatableReadTransaction = new TransactionTemplate(transactionManager);
    this.repeatableReadTransaction.setReadOnly(true);
    this.repeatableReadTransaction.setIsolationLevelName("ISOLATION_REPEATABLE_READ");
  }

  public HistoricalReadContext buildContext(HistoricalExecutionContext executionContext) {
    return HistoricalReadContext.forSourceIds(executionContext.sourceIds());
  }

  @Override
  public void readChunk(
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      Consumer<HistoricalSourceEvent> rowConsumer) {
    HistoricalReadContext context = buildContext(executionContext);
    HistoricalReadCursor cursor = null;
    HistoricalReadPage page;
    do {
      page = readActiveSnapshot(context, executionContext, chunk, cursor, DEFAULT_PAGE_SIZE);
      page.rows().forEach(rowConsumer);
      cursor = page.nextCursor();
    } while (page.hasMore());
  }

  public HistoricalReadPage readActiveSnapshot(
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      HistoricalReadCursor cursor,
      int pageSize) {
    return readActiveSnapshot(
        buildContext(executionContext), executionContext, chunk, cursor, pageSize);
  }

  public HistoricalReadPage readActiveSnapshot(
      HistoricalReadContext context,
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      HistoricalReadCursor cursor,
      int pageSize) {
    return repeatableReadTransaction.execute(
        _ -> {
          HistoricalReadContext readContext =
              context == null ? buildContext(executionContext) : context;
          if (readContext.isEmpty()) {
            return new HistoricalReadPage(List.of(), null, false);
          }

          int requestedPageSize = Math.max(1, pageSize);
          return observeActiveSnapshotRead(
                  candidateReadPath(readContext),
                  readContext,
                  executionContext,
                  chunk,
                  requestedPageSize,
                  () -> {
                    if (readContext.usesCheckpoint()) {
                      HistoricalReadPage checkpointPage =
                          checkpointReader.read(readContext, chunk, requestedPageSize, cursor);
                      if (!checkpointPage.rows().isEmpty()) {
                        historicalObservability.recordHistoricalRead(
                            ReadPath.CHECKPOINT, 1, checkpointPage.rows().size());
                        return new ObservedReadPage(ReadPath.CHECKPOINT, checkpointPage);
                      }
                    }
                    ActiveSnapshotReadResult activeResult =
                        activeSnapshotReader.readWithPath(
                            readContext, chunk, cursor, requestedPageSize);
                    HistoricalReadPage activePage = activeResult.page();
                    ReadPath readPath = toReadPath(activeResult.path());
                    historicalObservability.recordHistoricalRead(
                        readPath, 1, activePage.rows().size());
                    return new ObservedReadPage(readPath, activePage);
                  })
              .page();
        });
  }

  public HistoricalReadPage readDelta(
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      Instant previousChunkEndExclusive,
      HistoricalReadCursor cursor,
      int pageSize) {
    return readDelta(
        buildContext(executionContext),
        executionContext,
        chunk,
        previousChunkEndExclusive,
        cursor,
        pageSize);
  }

  public HistoricalReadPage readDelta(
      HistoricalReadContext context,
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      Instant previousChunkEndExclusive,
      HistoricalReadCursor cursor,
      int pageSize) {
    return repeatableReadTransaction.execute(
        _ -> {
          HistoricalReadContext readContext =
              context == null ? buildContext(executionContext) : context;
          if (readContext.isEmpty()) {
            return new HistoricalReadPage(List.of(), null, false);
          }

          int requestedPageSize = Math.max(1, pageSize);
          HistoricalReadPage deltaPage =
              deltaReader.read(
                  readContext, chunk, previousChunkEndExclusive, cursor, requestedPageSize);
          historicalObservability.recordHistoricalRead(
              ReadPath.DELTA_EVENT, 1, deltaPage.rows().size());
          return deltaPage;
        });
  }

  @Override
  public void readStateDelta(
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      Instant previousChunkEndExclusive,
      Consumer<HistoricalSourceEvent> rowConsumer) {
    streamLatestStateDelta(
        buildContext(executionContext),
        executionContext,
        chunk,
        previousChunkEndExclusive,
        DEFAULT_PAGE_SIZE,
        rowConsumer);
  }

  public void streamLatestStateDelta(
      HistoricalReadContext context,
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      Instant previousChunkEndExclusive,
      int pageSize,
      Consumer<HistoricalSourceEvent> rowConsumer) {
    repeatableReadTransaction.execute(
        _ -> {
          HistoricalReadContext readContext =
              context == null ? buildContext(executionContext) : context;
          if (readContext.isEmpty()) {
            return null;
          }

          AtomicInteger rowCounter = new AtomicInteger();
          deltaReader.streamLatestState(
              stateDeltaReadPath(readContext, executionContext),
              readContext,
              chunk,
              previousChunkEndExclusive,
              row -> {
                rowConsumer.accept(row);
                rowCounter.incrementAndGet();
              },
              Math.max(1, pageSize));
          historicalObservability.recordHistoricalRead(ReadPath.DELTA_STATE, 1, rowCounter.get());
          return null;
        });
  }

  static PostGisHistoricalDeltaReader.StateDeltaReadPath stateDeltaReadPath(
      HistoricalReadContext readContext, HistoricalExecutionContext executionContext) {
    if (readContext.isSingleSource()) {
      if (executionContext.resolution() == HistoricalResolution.DAY_1) {
        return PostGisHistoricalDeltaReader.StateDeltaReadPath.HELPER_DOMAIN;
      }
      return PostGisHistoricalDeltaReader.StateDeltaReadPath.CHANGED_OBJECT;
    }

    // Normal historical processing is lane-local, so multi-source state-delta reads are defensive
    // compatibility for direct callers and should be removed once those callers are ruled out.
    return PostGisHistoricalDeltaReader.StateDeltaReadPath.CHANGED_OBJECT;
  }

  public void streamDelta(
      HistoricalReadContext context,
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      Instant previousChunkEndExclusive,
      int pageSize,
      Consumer<HistoricalSourceEvent> rowConsumer) {
    repeatableReadTransaction.execute(
        _ -> {
          HistoricalReadContext readContext =
              context == null ? buildContext(executionContext) : context;
          if (readContext.isEmpty()) {
            return null;
          }

          AtomicInteger rowCounter = new AtomicInteger();
          deltaReader.stream(
              readContext,
              chunk,
              previousChunkEndExclusive,
              row -> {
                rowConsumer.accept(row);
                rowCounter.incrementAndGet();
              },
              Math.max(1, pageSize));
          historicalObservability.recordHistoricalRead(ReadPath.DELTA_EVENT, 1, rowCounter.get());
          return null;
        });
  }

  @Override
  public void readInitialState(
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      Consumer<HistoricalSourceEvent> rowConsumer) {
    streamActiveSnapshot(
        buildContext(executionContext),
        executionContext,
        chunk,
        null,
        DEFAULT_PAGE_SIZE,
        rowConsumer);
  }

  public void streamActiveSnapshot(
      HistoricalReadContext context,
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      HistoricalReadCursor cursor,
      int pageSize,
      Consumer<HistoricalSourceEvent> rowConsumer) {
    repeatableReadTransaction.execute(
        _ -> {
          HistoricalReadContext readContext =
              context == null ? buildContext(executionContext) : context;
          if (readContext.isEmpty()) {
            return null;
          }

          int requestedPageSize = Math.max(1, pageSize);
          observeActiveSnapshotStream(
              candidateReadPath(readContext),
              readContext,
              executionContext,
              chunk,
              requestedPageSize,
              () ->
                  streamActiveSnapshotRows(
                      readContext, chunk, cursor, requestedPageSize, rowConsumer));
          return null;
        });
  }

  private StreamReadSummary streamActiveSnapshotRows(
      HistoricalReadContext readContext,
      HistoricalChunkPlanEntry chunk,
      HistoricalReadCursor cursor,
      int requestedPageSize,
      Consumer<HistoricalSourceEvent> rowConsumer) {
    if (readContext.isSingleSource() && !readContext.usesCheckpoint()) {
      return streamSingleSourceActiveSnapshotRows(
          readContext, chunk, cursor, requestedPageSize, rowConsumer);
    }

    if (readContext.usesCheckpoint()) {
      StreamReadSummary checkpointSummary =
          streamCheckpointActiveSnapshotRows(
              readContext, chunk, cursor, requestedPageSize, rowConsumer);
      if (checkpointSummary != null) {
        return checkpointSummary;
      }
    }

    return streamPagedActiveSnapshotRows(
        readContext, chunk, cursor, requestedPageSize, rowConsumer);
  }

  private StreamReadSummary streamSingleSourceActiveSnapshotRows(
      HistoricalReadContext readContext,
      HistoricalChunkPlanEntry chunk,
      HistoricalReadCursor cursor,
      int requestedPageSize,
      Consumer<HistoricalSourceEvent> rowConsumer) {
    AtomicInteger rowCounter = new AtomicInteger();
    AtomicInteger stableCounter = new AtomicInteger();
    AtomicInteger residualCounter = new AtomicInteger();
    ActiveSnapshotReadPath streamPath =
        activeSnapshotReader.streamSingleSourceViaHelperFastPath(
            readContext,
            chunk,
            cursor,
            requestedPageSize,
            (row, residual) -> {
              rowConsumer.accept(row);
              rowCounter.incrementAndGet();
              if (residual.booleanValue()) {
                residualCounter.incrementAndGet();
              } else {
                stableCounter.incrementAndGet();
              }
            });
    ReadPath readPath = toReadPath(streamPath);
    historicalObservability.recordHistoricalRead(readPath, 1, rowCounter.get());
    return new StreamReadSummary(readPath, stableCounter.get(), residualCounter.get());
  }

  private StreamReadSummary streamCheckpointActiveSnapshotRows(
      HistoricalReadContext readContext,
      HistoricalChunkPlanEntry chunk,
      HistoricalReadCursor cursor,
      int requestedPageSize,
      Consumer<HistoricalSourceEvent> rowConsumer) {
    AtomicInteger rowCounter = new AtomicInteger();
    HistoricalReadCursor nextCursor = cursor;
    HistoricalReadPage checkpointPage;
    do {
      checkpointPage = checkpointReader.read(readContext, chunk, requestedPageSize, nextCursor);
      if (checkpointPage.rows().isEmpty()) {
        return null;
      }

      for (HistoricalSourceEvent row : checkpointPage.rows()) {
        rowConsumer.accept(row);
        rowCounter.incrementAndGet();
      }
      nextCursor = checkpointPage.nextCursor();
    } while (checkpointPage.hasMore());

    historicalObservability.recordHistoricalRead(ReadPath.CHECKPOINT, 1, rowCounter.get());
    return new StreamReadSummary(ReadPath.CHECKPOINT, 0, 0);
  }

  private StreamReadSummary streamPagedActiveSnapshotRows(
      HistoricalReadContext readContext,
      HistoricalChunkPlanEntry chunk,
      HistoricalReadCursor cursor,
      int requestedPageSize,
      Consumer<HistoricalSourceEvent> rowConsumer) {
    AtomicInteger rowCounter = new AtomicInteger();
    ActiveSnapshotReadPath streamPath = null;
    HistoricalReadCursor nextCursor = cursor;
    HistoricalReadPage page;
    do {
      ActiveSnapshotReadResult activeResult =
          activeSnapshotReader.readWithPath(readContext, chunk, nextCursor, requestedPageSize);
      page = activeResult.page();
      if (streamPath == null && !page.rows().isEmpty()) {
        streamPath = activeResult.path();
      }

      for (HistoricalSourceEvent row : page.rows()) {
        rowConsumer.accept(row);
        rowCounter.incrementAndGet();
      }
      nextCursor = page.nextCursor();
    } while (page.hasMore());

    ReadPath readPath = streamPath == null ? ReadPath.ACTIVE_FULL : toReadPath(streamPath);
    historicalObservability.recordHistoricalRead(readPath, 1, rowCounter.get());
    return new StreamReadSummary(readPath, 0, 0);
  }

  private ObservedReadPage observeActiveSnapshotRead(
      ReadPath candidatePath,
      HistoricalReadContext readContext,
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      int requestedPageSize,
      ActiveSnapshotReadOperation operation) {
    Span span =
        startActiveSnapshotSpan(
            candidatePath, readContext, executionContext, chunk, requestedPageSize);
    historicalObservability.recordActiveSnapshotReadStarted(candidatePath);
    Instant startedAt = Instant.now();
    try (var _ = span.makeCurrent()) {
      ObservedReadPage result = operation.read();
      historicalObservability.recordActiveSnapshotReadDuration(
          candidatePath,
          ActiveSnapshotReadOutcome.SUCCESS,
          Duration.between(startedAt, Instant.now()));
      span.setStatus(StatusCode.OK, Outcome.SUCCESS.value());
      return result;
    } catch (RuntimeException error) {
      historicalObservability.recordActiveSnapshotReadDuration(
          candidatePath,
          ActiveSnapshotReadOutcome.ERROR,
          Duration.between(startedAt, Instant.now()));
      span.setStatus(StatusCode.ERROR, Outcome.ERROR.value());
      span.recordException(error);
      throw error;
    } finally {
      span.end();
    }
  }

  private void observeActiveSnapshotStream(
      ReadPath candidatePath,
      HistoricalReadContext readContext,
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      int requestedPageSize,
      ActiveSnapshotStreamOperation operation) {
    Span span =
        startActiveSnapshotSpan(
            candidatePath, readContext, executionContext, chunk, requestedPageSize);
    historicalObservability.recordActiveSnapshotReadStarted(candidatePath);
    Instant startedAt = Instant.now();
    try (var _ = span.makeCurrent()) {
      StreamReadSummary result = operation.stream();
      historicalObservability.recordActiveSnapshotReadDuration(
          candidatePath,
          ActiveSnapshotReadOutcome.SUCCESS,
          Duration.between(startedAt, Instant.now()));
      historicalObservability.recordActiveSnapshotRows(
          result.path(), ActiveSnapshotRowKind.STABLE, result.stableRows());
      historicalObservability.recordActiveSnapshotRows(
          result.path(), ActiveSnapshotRowKind.RESIDUAL, result.residualRows());
      span.setStatus(StatusCode.OK, Outcome.SUCCESS.value());
    } catch (RuntimeException error) {
      historicalObservability.recordActiveSnapshotReadDuration(
          candidatePath,
          ActiveSnapshotReadOutcome.ERROR,
          Duration.between(startedAt, Instant.now()));
      span.setStatus(StatusCode.ERROR, Outcome.ERROR.value());
      span.recordException(error);
      throw error;
    } finally {
      span.end();
    }
  }

  private Span startActiveSnapshotSpan(
      ReadPath candidatePath,
      HistoricalReadContext readContext,
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      int requestedPageSize) {
    Span span = tracer.spanBuilder(ACTIVE_SNAPSHOT_READ_SPAN).startSpan();
    span.setAllAttributes(
        HistoricalTraceContext.spanAttributes(
            activeSnapshotSpanAttributes(
                candidatePath, readContext, executionContext, chunk, requestedPageSize)));
    return span;
  }

  private Map<String, String> activeSnapshotSpanAttributes(
      ReadPath candidatePath,
      HistoricalReadContext readContext,
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      int requestedPageSize) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_REQUEST_ID, executionContext.requestId().toString());
    attributes.put(HistoricalTraceContext.ATTRIBUTE_JOB_ID, executionContext.jobId().toString());
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_CHUNK_INDEX, Integer.toString(chunk.chunkIndex()));
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_SUBJECT_TYPE, executionContext.subjectType().name());
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_READ_PATH_CANDIDATE,
        candidatePath.value());
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_SOURCE_COUNT,
        Integer.toString(readContext.sourceIds().size()));
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_CHUNK_FROM, chunk.from().toString());
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_CHUNK_TO, chunk.to().toString());
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_FETCH_SIZE,
        Integer.toString(requestedPageSize));
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_TIMEOUT_SECONDS,
        Integer.toString(activeSnapshotQueryTimeoutSeconds));
    return attributes;
  }

  private static ReadPath candidateReadPath(HistoricalReadContext readContext) {
    if (readContext.usesCheckpoint()) {
      return ReadPath.CHECKPOINT;
    }
    if (readContext.isSingleSource()) {
      return ReadPath.ACTIVE_HELPER;
    }
    return ReadPath.ACTIVE_FULL;
  }

  private static ReadPath toReadPath(ActiveSnapshotReadPath path) {
    return switch (path) {
      case HELPER -> ReadPath.ACTIVE_HELPER;
      case RESIDUAL -> ReadPath.ACTIVE_RESIDUAL;
      case FULL -> ReadPath.ACTIVE_FULL;
      case CHECKPOINT -> ReadPath.CHECKPOINT;
    };
  }

  private record ObservedReadPage(ReadPath path, HistoricalReadPage page) {}

  private record StreamReadSummary(ReadPath path, int stableRows, int residualRows) {}

  @FunctionalInterface
  private interface ActiveSnapshotReadOperation {
    ObservedReadPage read();
  }

  @FunctionalInterface
  private interface ActiveSnapshotStreamOperation {
    StreamReadSummary stream();
  }
}
