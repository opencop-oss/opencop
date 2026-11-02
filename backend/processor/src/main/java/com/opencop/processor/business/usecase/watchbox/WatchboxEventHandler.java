// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.watchbox;

import com.opencop.core.sdk.model.watchbox.WatchboxFilterEvaluator;
import com.opencop.core.sdk.observability.BoundedMetricTagGuard;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome;
import com.opencop.processor.business.model.watchbox.WatchboxObservation;
import com.opencop.processor.business.model.watchbox.WatchboxRuntimeSnapshot;
import com.opencop.processor.business.model.watchbox.WatchboxRuntimeTarget;
import com.opencop.processor.business.model.watchbox.WatchboxStateKey;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxAlertDrainSignal;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxRuntimeConfigGateway;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.Tags;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.geojson.Feature;
import org.geojson.Point;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class WatchboxEventHandler {

  private static final String GUARD_METRIC = "opencop.watchbox.pipeline.guard";
  private static final String EVALUATED_METRIC = "opencop.watchbox.pipeline.evaluated";
  private static final String BATCH_DURATION_METRIC = "opencop.watchbox.pipeline.batch.duration";
  private static final String GENERATION_RETRY_METRIC =
      "opencop.watchbox.pipeline.config.generation_retries";
  private static final Set<String> GUARD_REASONS =
      Set.of(
          "unwatched_source",
          "import",
          "non_point",
          "missing_identity",
          "oversized_object_id",
          "stale",
          "future_skew",
          "unparseable",
          "frozen_scope");

  // At least as strict as the V37 varchar(255) bound on the watchbox object_id columns: this
  // counts UTF-16 units where PostgreSQL counts characters, so astral-plane ids are rejected
  // slightly early. Rejecting here keeps one oversized producer value from failing the whole
  // batch at the database.
  private static final int MAX_OBJECT_ID_LENGTH = 255;
  // Display metadata only, so an overlong label is trimmed rather than rejected. The bound matches
  // the object id's so a client may fall back to the object id without a width mismatch; V46
  // declares it on both retained copies. Counted in code points, which is how PostgreSQL counts
  // varchar characters, so a surrogate pair is never split.
  private static final int MAX_DISPLAY_NAME_LENGTH = 255;

  private final WatchboxRuntimeConfigGateway runtimeConfig;
  private final WatchboxTransitionEngine engine;
  private final WatchboxAlertDrainSignal alertDrainSignal;
  private final Duration liveWindow;
  private final Duration futureTolerance;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final ProcessorMetrics metrics;
  private final GeometryFactory geometryFactory = new GeometryFactory();

  public WatchboxEventHandler(
      WatchboxRuntimeConfigGateway runtimeConfig,
      WatchboxTransitionEngine engine,
      WatchboxAlertDrainSignal alertDrainSignal,
      Duration liveWindow,
      Duration futureTolerance,
      ObjectMapper objectMapper,
      Clock clock,
      ProcessorMetrics metrics) {
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
    this.engine = Objects.requireNonNull(engine, "engine");
    this.alertDrainSignal = Objects.requireNonNull(alertDrainSignal, "alertDrainSignal");
    this.liveWindow = requirePositive(liveWindow, "liveWindow");
    this.futureTolerance = requirePositive(futureTolerance, "futureTolerance");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
  }

  public void process(List<Feature> features) {
    Objects.requireNonNull(features, "features");
    var startedAt = System.nanoTime();
    try {
      processWithGenerationRetry(features);
    } finally {
      metrics.recordTimer(
          BATCH_DURATION_METRIC, Tags.empty(), Duration.ofNanos(System.nanoTime() - startedAt));
    }
  }

  private void processWithGenerationRetry(List<Feature> features) {
    var snapshot = runtimeConfig.load();
    try {
      processSnapshot(features, snapshot);
      return;
    } catch (WatchboxGenerationChangedException _) {
      snapshot = runtimeConfig.reloadAfterGenerationChange(snapshot);
    }

    metrics.incrementCounter(GENERATION_RETRY_METRIC, Tags.empty());
    try {
      processSnapshot(features, snapshot);
    } catch (WatchboxGenerationChangedException exception) {
      runtimeConfig.invalidateAfterGenerationChange(snapshot);
      throw exception;
    }
  }

  private void processSnapshot(List<Feature> features, WatchboxRuntimeSnapshot snapshot) {
    if (snapshot.isEmpty()) {
      countGuard("unwatched_source", features.size());
      return;
    }

    var observations = new ArrayList<WatchboxObservation>();
    var now = clock.instant();
    for (var feature : features) {
      switch (screen(feature, snapshot, now)) {
        case Screened.Rejected(String reason) -> countGuard(reason, 1);
        case Screened.Admitted admitted -> collectObservations(admitted, feature, observations);
      }
    }

    if (!observations.isEmpty()) {
      metrics.incrementCounter(EVALUATED_METRIC, Tags.empty(), observations.size());
      var result = engine.process(observations);
      // The engine committed the detections and their outbox rows inside its own transaction;
      // this post-commit nudge only wakes the drainer early. Delivery does not depend on it —
      // the drainer's startup drain and fallback poll cover a missed or crashed nudge. Latency
      // does depend on it, though: the nudge is in-process and effectively instantaneous, so the
      // instance that commits detections drains its own work milliseconds later in every
      // topology. The fallback poll is only the recovery bound for rows orphaned by a crash, not
      // the normal delivery path.
      if (result.count(WatchboxBatchOutcome.DETECTIONS_EMITTED) > 0) {
        alertDrainSignal.requestDrain();
      }
    }
  }

  /**
   * Applies the per-feature guard chain in cheapness order: source peek and watched-source lookup
   * before the full property parse, parse-shape guards before geometry, event-time window last.
   * Reordering changes both the unwatched-source fast path and which guard reason a
   * multiply-defective feature is counted under.
   */
  private Screened screen(Feature feature, WatchboxRuntimeSnapshot snapshot, Instant now) {
    var sourceId = peekSourceId(feature);
    if (sourceId == null) {
      return new Screened.Rejected("unparseable");
    }
    var targets = snapshot.targetsFor(sourceId);
    if (targets.isEmpty()) {
      return new Screened.Rejected("unwatched_source");
    }

    OpenCopProperties properties;
    try {
      properties = OpenCopPropertiesParser.readFromFeature(feature);
    } catch (RuntimeException _) {
      return new Screened.Rejected("unparseable");
    }
    if (properties == null
        || properties.getSource() == null
        || properties.getObjectId() == null
        || properties.getObjectId().isBlank()) {
      return new Screened.Rejected("unparseable");
    }
    if (properties.getObjectId().length() > MAX_OBJECT_ID_LENGTH) {
      return new Screened.Rejected("oversized_object_id");
    }
    if (properties.getImportId() != null) {
      return new Screened.Rejected("import");
    }
    if (!(feature.getGeometry() instanceof Point point)) {
      return new Screened.Rejected("non_point");
    }
    if (properties.getId() == null) {
      return new Screened.Rejected("missing_identity");
    }
    if (properties.getTimestampz() == null) {
      return new Screened.Rejected("unparseable");
    }

    var eventTime = Instant.ofEpochMilli(properties.getTimestampz().getStart());
    if (eventTime.isBefore(now.minus(liveWindow))) {
      return new Screened.Rejected("stale");
    }
    if (eventTime.isAfter(now.plus(futureTolerance))) {
      return new Screened.Rejected("future_skew");
    }

    var coordinate = point.getCoordinates();
    var geometryPoint =
        geometryFactory.createPoint(
            new Coordinate(coordinate.getLongitude(), coordinate.getLatitude()));
    return new Screened.Admitted(targets, properties, sourceId, eventTime, geometryPoint);
  }

  /**
   * Evaluates one admitted feature against its targets. The JSON tree stays lazy: it is built at
   * most once per feature, and only when some non-frozen target carries filter predicates.
   */
  private void collectObservations(
      Screened.Admitted admitted, Feature feature, List<WatchboxObservation> observations) {
    JsonNode featureNode = null;
    for (var target : admitted.targets()) {
      if (target.frozen()) {
        countGuard("frozen_scope", 1);
        continue;
      }
      var geometryInside = target.geometry().covers(admitted.geometryPoint());
      boolean filterMatches;
      if (target.filter().predicates().isEmpty()) {
        filterMatches = true;
      } else {
        if (featureNode == null) {
          featureNode = objectMapper.valueToTree(feature);
        }
        filterMatches = WatchboxFilterEvaluator.evaluate(target.filter(), featureNode).matches();
      }
      observations.add(
          observation(
              target,
              admitted.properties(),
              admitted.sourceId(),
              admitted.eventTime(),
              geometryInside,
              filterMatches));
    }
  }

  private WatchboxObservation observation(
      WatchboxRuntimeTarget target,
      OpenCopProperties properties,
      UUID sourceId,
      Instant eventTime,
      boolean geometryInside,
      boolean filterMatches) {
    return new WatchboxObservation(
        new WatchboxStateKey(
            target.watchboxId(), target.configEpoch(), sourceId, properties.getObjectId()),
        properties.getId(),
        eventTime,
        geometryInside,
        geometryInside && filterMatches,
        target.direction(),
        normalizeDisplayName(properties.getDisplayName()));
  }

  private static String normalizeDisplayName(String displayName) {
    if (displayName == null || displayName.isBlank()) {
      return null;
    }
    return displayName.codePointCount(0, displayName.length()) <= MAX_DISPLAY_NAME_LENGTH
        ? displayName
        : displayName.substring(0, displayName.offsetByCodePoints(0, MAX_DISPLAY_NAME_LENGTH));
  }

  private UUID peekSourceId(Feature feature) {
    if (feature == null) {
      return null;
    }
    var raw = feature.getProperty(OpenCopPropertiesParser.OPENCOP_NODE);
    if (raw instanceof OpenCopProperties properties) {
      return properties.getSource();
    }
    if (raw instanceof Map<?, ?> map) {
      return uuid(map.get("source"));
    }
    if (raw instanceof JsonNode node) {
      return uuid(node.get("source"));
    }
    return null;
  }

  private static UUID uuid(Object value) {
    if (value instanceof UUID id) {
      return id;
    }
    if (value instanceof JsonNode node && node.isString()) {
      value = node.asString();
    }
    if (value instanceof String text) {
      try {
        return UUID.fromString(text);
      } catch (IllegalArgumentException _) {
        return null;
      }
    }
    return null;
  }

  private void countGuard(String reason, long amount) {
    if (amount == 0) {
      return;
    }
    var boundedReason = BoundedMetricTagGuard.requireAllowed("reason", reason, GUARD_REASONS);
    metrics.incrementCounter(GUARD_METRIC, Tags.of("reason", boundedReason), amount);
  }

  private static Duration requirePositive(Duration duration, String name) {
    Objects.requireNonNull(duration, name);
    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return duration;
  }

  /**
   * Result of the per-feature guard chain: either the validated bundle every later stage needs, or
   * the guard reason to count. The pattern switch in {@code process} is exhaustive over this sealed
   * type, so a new variant fails compilation until it is handled.
   */
  private sealed interface Screened {
    record Admitted(
        List<WatchboxRuntimeTarget> targets,
        OpenCopProperties properties,
        UUID sourceId,
        Instant eventTime,
        org.locationtech.jts.geom.Point geometryPoint)
        implements Screened {}

    record Rejected(String reason) implements Screened {}
  }
}
