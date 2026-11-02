// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.simulation;

import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.elements.Element;
import com.opencop.core.sdk.technical.model.opencop.elements.ElementTypes;
import com.opencop.core.sdk.technical.model.opencop.elements.PointElement;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import org.geojson.Feature;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Generates synthetic moving-point history when development mode is enabled.
 *
 * <p>For each active entity at each tick, the service creates a GeoJSON feature and publishes its
 * bytes through {@code rawEventProducer}. Every message includes object, source, and stable GeoJSON
 * mapper headers. Entity positions follow the configured movement and seed settings, while stale
 * times are distributed across the configured chunks.
 */
@Service
@ConditionalOnProperty(name = "opencop.dev.mode", havingValue = "true", matchIfMissing = false)
public class HistoricalSimulationService {

  private static final Logger log = LoggerFactory.getLogger(HistoricalSimulationService.class);

  private static final int ROUND_SCALE = 6;
  private static final BBox BBOX = new BBox(-90.0, -90.0, 90.0, 90.0);

  /** Stable GeoJSON mapper identifier attached to every published event. */
  private static final String UNIVERSAL_TEST_MAPPER_ID = "4017d49f-2a68-4900-8e50-2dbb3bd435df";

  private final MessageBrokerProducer<byte[]> rawEventProducer;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public HistoricalSimulationService(
      @Qualifier("rawEventProducer") MessageBrokerProducer<byte[]> rawEventProducer) {
    this.rawEventProducer = rawEventProducer;
  }

  /** Run the simulation and return a structured summary. Synchronous. */
  public HistoricalSimulationResponse generate(HistoricalSimulationParameters parameters) {
    Random random = parameters.randomSeeded() ? new Random(parameters.seed()) : null;
    List<ChunkPlan> chunkPlans = chunkPlans(parameters);
    List<Entry> entries = createEntries(parameters, chunkPlans, random);

    long totalMessages = 0L;
    long totalTicks = 0L;

    for (ChunkPlan chunkPlan : chunkPlans) {
      long ticksInChunk =
          Math.max(1L, chunkPlan.duration().toMillis() / parameters.tickDuration().toMillis());
      totalTicks += ticksInChunk;

      for (long tick = 0; tick < ticksInChunk; tick++) {
        Instant eventTime = chunkPlan.start().plus(parameters.tickDuration().multipliedBy(tick));
        for (Entry entry : entries) {
          if (eventTime.isAfter(entry.staleAt)) {
            continue;
          }
          updateEntry(entry, parameters, random);
          if (entry.firstEventAt == null) entry.firstEventAt = eventTime;
          entry.lastEventAt = eventTime;
          entry.tickCount++;
          Feature feature = generateFeature(entry, parameters, eventTime);
          byte[] payload = serializeFeature(feature);
          rawEventProducer.send(payload, kafkaHeaders(parameters.sourceId()));
          totalMessages++;
        }
      }
    }

    log.info(
        "HistoricalSimulationService generated {} messages for source {} across {} using {} unique points",
        totalMessages,
        parameters.sourceId(),
        parameters.timeframe(),
        parameters.uniquePoints());

    Instant windowEnd = parameters.start().plus(parameters.timeframe());

    List<HistoricalSimulationResponse.EntitySummary> summaries = new ArrayList<>(entries.size());
    for (Entry entry : entries) {
      summaries.add(
          new HistoricalSimulationResponse.EntitySummary(
              entry.objectId,
              entry.displayName,
              entry.sidc,
              entry.firstEventAt,
              entry.lastEventAt,
              entry.staleAt,
              entry.tickCount));
    }

    return new HistoricalSimulationResponse(
        parameters.sourceId(),
        parameters.start(),
        windowEnd,
        totalMessages,
        chunkPlans.size(),
        (int) totalTicks,
        parameters.tickDuration().toMillis(),
        entries.size(),
        summaries);
  }

  // Simulation calculations

  private static List<Entry> createEntries(
      HistoricalSimulationParameters parameters, List<ChunkPlan> chunkPlans, Random random) {
    List<Entry> entries = new ArrayList<>(parameters.uniquePoints());
    List<Instant> staleSchedule = buildStaleSchedule(parameters, chunkPlans, random);
    for (int i = 0; i < parameters.uniquePoints(); i++) {
      double lat = nextDouble(parameters.randomSeeded(), random, BBOX.minLat(), BBOX.maxLat());
      double lon = nextDouble(parameters.randomSeeded(), random, BBOX.minLon(), BBOX.maxLon());
      double headingDeg = nextDouble(parameters.randomSeeded(), random, 0.0, 360.0);
      String sidc = selectSidc(parameters, i, random);
      Element element = new PointElement.Builder().symbol(sidc).type(ElementTypes.SYMBOL).build();
      entries.add(
          new Entry(
              "history-object-" + i,
              "Historical Object " + i,
              sidc,
              element,
              new Point(new LngLatAlt(round(lon), round(lat))),
              headingDeg,
              staleSchedule.get(i)));
    }
    return entries;
  }

  private static List<Instant> buildStaleSchedule(
      HistoricalSimulationParameters parameters, List<ChunkPlan> chunkPlans, Random random) {
    List<Instant> staleSchedule =
        new ArrayList<>(Collections.nCopies(parameters.uniquePoints(), null));
    List<Integer> entryIndexes =
        IntStream.range(0, parameters.uniquePoints())
            .boxed()
            .collect(ArrayList::new, List::add, List::addAll);
    if (parameters.randomSeeded()) {
      Collections.shuffle(entryIndexes, new Random(parameters.seed() ^ 0x5DEECE66DL));
    }

    int baseCountPerChunk = parameters.uniquePoints() / chunkPlans.size();
    int remainder = parameters.uniquePoints() % chunkPlans.size();
    int assignedCount = 0;

    for (int chunkIndex = 0; chunkIndex < chunkPlans.size(); chunkIndex++) {
      ChunkPlan chunkPlan = chunkPlans.get(chunkIndex);
      int staleCountInChunk = baseCountPerChunk + (chunkIndex < remainder ? 1 : 0);
      long ticksInChunk =
          Math.max(1L, chunkPlan.duration().toMillis() / parameters.tickDuration().toMillis());

      for (int positionInChunk = 0; positionInChunk < staleCountInChunk; positionInChunk++) {
        int entryIndex = entryIndexes.get(assignedCount++);
        long staleTick =
            Math.min(
                ticksInChunk - 1,
                Math.max(1L, ((long) (positionInChunk + 1) * ticksInChunk) / staleCountInChunk));
        Instant staleAt = chunkPlan.start().plus(parameters.tickDuration().multipliedBy(staleTick));
        staleSchedule.set(entryIndex, staleAt);
      }
    }

    return staleSchedule;
  }

  private static List<ChunkPlan> chunkPlans(HistoricalSimulationParameters parameters) {
    List<ChunkPlan> plans = new ArrayList<>();
    Instant chunkStart = parameters.start();
    long chunkCount =
        Math.max(1L, parameters.timeframe().toMillis() / parameters.chunkDuration().toMillis());

    for (int i = 0; i < chunkCount; i++) {
      Instant chunkEnd = chunkStart.plus(parameters.chunkDuration());
      plans.add(new ChunkPlan(chunkStart, chunkEnd));
      chunkStart = chunkEnd;
    }
    return plans;
  }

  private static void updateEntry(
      Entry entry, HistoricalSimulationParameters parameters, Random random) {
    double jitter =
        nextDouble(
            parameters.randomSeeded(),
            random,
            -parameters.turnJitterDeg(),
            parameters.turnJitterDeg());
    entry.headingDeg = normalizeDeg(entry.headingDeg + jitter);
    moveByMeters(entry, parameters.moveMetersPerTick(), entry.headingDeg);

    LngLatAlt ll = entry.point.getCoordinates();
    double lon = ll.getLongitude();
    double lat = ll.getLatitude();

    if (lon < BBOX.minLon()) {
      lon = BBOX.minLon();
      entry.headingDeg = normalizeDeg(180 - entry.headingDeg);
    } else if (lon > BBOX.maxLon()) {
      lon = BBOX.maxLon();
      entry.headingDeg = normalizeDeg(180 - entry.headingDeg);
    }

    if (lat < BBOX.minLat()) {
      lat = BBOX.minLat();
      entry.headingDeg = normalizeDeg(-entry.headingDeg);
    } else if (lat > BBOX.maxLat()) {
      lat = BBOX.maxLat();
      entry.headingDeg = normalizeDeg(-entry.headingDeg);
    }

    entry.point.setCoordinates(new LngLatAlt(round(lon), round(lat)));
  }

  private Feature generateFeature(
      Entry entry, HistoricalSimulationParameters parameters, Instant eventTime) {
    entry.element.setCourse(entry.headingDeg);
    entry.element.setSpeed(
        parameters.moveMetersPerTick() / (parameters.tickDuration().toMillis() / 1000.0));

    OpenCopProperties properties = new OpenCopProperties();
    properties.setId(UUID.randomUUID());
    properties.setSource(parameters.sourceId());
    properties.setObjectId(entry.objectId);
    properties.setDisplayName(entry.displayName);
    properties.setTimestampz(timestampz(eventTime, entry.staleAt));
    properties.setElement(entry.element);

    return OpenCopPropertiesParser.writeToFeature(properties, entry.point);
  }

  private byte[] serializeFeature(Feature feature) {
    return objectMapper.writeValueAsBytes(feature);
  }

  private static Map<String, String> kafkaHeaders(UUID sourceId) {
    return Map.of(
        OpencopKafkaHeaders.OBJECT_ID.getName(), UUID.randomUUID().toString(),
        OpencopKafkaHeaders.SOURCE_ID.getName(), sourceId.toString(),
        OpencopKafkaHeaders.MAPPER_ID.getName(), UNIVERSAL_TEST_MAPPER_ID);
  }

  private static Timestampz timestampz(Instant eventTime, Instant staleAt) {
    Timestampz timestampz = new Timestampz();
    timestampz.setTime(eventTime.toEpochMilli());
    timestampz.setStart(eventTime.toEpochMilli());
    timestampz.setStale(staleAt.toEpochMilli());
    return timestampz;
  }

  private static void moveByMeters(Entry entry, double meters, double bearingDeg) {
    double br = Math.toRadians(bearingDeg);
    double dNorth = meters * Math.cos(br);
    double dEast = meters * Math.sin(br);

    LngLatAlt ll = entry.point.getCoordinates();
    double lat = ll.getLatitude();
    double lon = ll.getLongitude();

    double metersPerDegLat = 111_320.0;
    double metersPerDegLon = 111_320.0 * Math.cos(Math.toRadians(lat));
    if (Math.abs(metersPerDegLon) < 1e-6) {
      metersPerDegLon = 1e-6;
    }

    lat += dNorth / metersPerDegLat;
    lon += dEast / metersPerDegLon;

    entry.point.setCoordinates(new LngLatAlt(lon, lat));
  }

  private static double normalizeDeg(double d) {
    double r = d % 360.0;
    return r < 0 ? r + 360.0 : r;
  }

  private static double round(double value) {
    return new BigDecimal(value).setScale(ROUND_SCALE, RoundingMode.HALF_UP).doubleValue();
  }

  private static String selectSidc(
      HistoricalSimulationParameters parameters, int index, Random random) {
    var list = HistoricalSimulationSidcCatalog.LIST;
    if (!parameters.randomSeeded()) {
      return list.get(index % list.size());
    }
    return list.get(random.nextInt(list.size()));
  }

  private static double nextDouble(boolean randomMode, Random random, double min, double max) {
    if (!randomMode) {
      return min + ((max - min) / 2.0);
    }
    return min + (random.nextDouble() * (max - min));
  }

  // ---------------------------------------------------------------------------
  // Internal records
  // ---------------------------------------------------------------------------

  private record BBox(double minLon, double minLat, double maxLon, double maxLat) {}

  private record ChunkPlan(Instant start, Instant end) {
    Duration duration() {
      return Duration.between(start, end);
    }
  }

  /**
   * Per-entity mutable state during simulation. The summary fields ({@code firstEventAt}, {@code
   * lastEventAt}, {@code tickCount}) are populated incrementally as the entity's events are
   * published, so the response carries accurate per-entity coverage info.
   */
  private static final class Entry {
    final String objectId;
    final String displayName;
    final String sidc;
    final Element element;
    final Instant staleAt;

    Point point;
    double headingDeg;
    Instant firstEventAt;
    Instant lastEventAt;
    long tickCount;

    Entry(
        String objectId,
        String displayName,
        String sidc,
        Element element,
        Point point,
        double headingDeg,
        Instant staleAt) {
      this.objectId = objectId;
      this.displayName = displayName;
      this.sidc = sidc;
      this.element = element;
      this.point = point;
      this.headingDeg = headingDeg;
      this.staleAt = staleAt;
    }
  }
}
