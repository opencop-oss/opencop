// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.simulation;

import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaProducer;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SimulationConfig.class)
class HistoricalSimulationTest {

  private static final String TOPIC = "opencop.event.external.raw";
  private static final UUID DEFAULT_SOURCE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000000");
  private static final int ROUND_SCALE = 6;
  private static final BBox BBOX = new BBox(-90.0, -90.0, 90.0, 90.0);
  private static final HistoricalSimulationParameters PARAMETERS =
      new HistoricalSimulationParameters(
          DEFAULT_SOURCE_ID,
          Instant.now().minus(Duration.ofHours(168)),
          Duration.ofHours(168),
          Duration.ofHours(1),
          Duration.ofMinutes(5),
          1_000,
          1_000,
          1_000,
          true,
          42L,
          1.0,
          3.0);

  @Autowired private KafkaTemplate<String, byte[]> template;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @Disabled(
      "Manual-only historical data generator. Configure via HistoricalSimulationTest.PARAMETERS and run explicitly.")
  void generateHistoricalSourceData() {
    HistoricalSimulationParameters parameters = PARAMETERS;
    MessageBrokerProducer<byte[]> broker = new KafkaProducer<>(this.template, TOPIC);
    Random random = parameters.randomSeeded() ? new Random(parameters.seed()) : null;
    List<ChunkPlan> chunkPlans = chunkPlans(parameters);

    List<Entry> entries = createEntries(parameters, chunkPlans, random);
    long totalMessages = 0L;

    for (ChunkPlan chunkPlan : chunkPlans) {
      long tickCount =
          Math.max(1L, chunkPlan.duration().toMillis() / parameters.tickDuration().toMillis());

      for (long tick = 0; tick < tickCount; tick++) {
        Instant eventTime = chunkPlan.start().plus(parameters.tickDuration().multipliedBy(tick));
        for (Entry entry : entries) {
          if (eventTime.isAfter(entry.staleAt)) {
            continue;
          }
          updateEntry(entry, parameters, eventTime, random);
          Feature feature = generateFeature(entry, parameters.sourceId(), eventTime);
          byte[] payload = objectMapper.writeValueAsBytes(feature);
          broker.send(payload, kafkaHeaders(parameters.sourceId()));
          totalMessages++;
        }
      }
    }

    System.out.println(
        "HistoricalSimulationTest generated "
            + totalMessages
            + " messages for source "
            + parameters.sourceId()
            + " across "
            + parameters.timeframe()
            + " using "
            + parameters.uniquePoints()
            + " unique points");
  }

  private static List<Entry> createEntries(
      HistoricalSimulationParameters parameters, List<ChunkPlan> chunkPlans, Random random) {
    List<Entry> entries = new ArrayList<>(parameters.uniquePoints());
    List<Instant> staleSchedule = buildStaleSchedule(parameters, chunkPlans, random);
    for (int i = 0; i < parameters.uniquePoints(); i++) {
      double lat = nextDouble(parameters.randomSeeded(), random, BBOX.minLat(), BBOX.maxLat());
      double lon = nextDouble(parameters.randomSeeded(), random, BBOX.minLon(), BBOX.maxLon());
      double headingDeg = nextDouble(parameters.randomSeeded(), random, 0.0, 360.0);
      String sidc = randomSidc(parameters, i, random);
      Element element = new PointElement.Builder().symbol(sidc).type(ElementTypes.SYMBOL).build();
      Instant initialTime = parameters.start();

      entries.add(
          new Entry(
              UUID.randomUUID(),
              new Point(new LngLatAlt(round(lon), round(lat))),
              element,
              "history-object-" + i,
              "Historical Object " + i,
              timestampz(initialTime, staleSchedule.get(i)),
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
      long tickCount =
          Math.max(1L, chunkPlan.duration().toMillis() / parameters.tickDuration().toMillis());

      for (int positionInChunk = 0; positionInChunk < staleCountInChunk; positionInChunk++) {
        int entryIndex = entryIndexes.get(assignedCount++);
        long staleTick =
            Math.min(
                tickCount - 1,
                Math.max(1L, ((long) (positionInChunk + 1) * tickCount) / staleCountInChunk));
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
      int uniquePointsInChunk = parameters.uniquePointsInChunkForIndex(i);
      plans.add(new ChunkPlan(chunkStart, chunkEnd, uniquePointsInChunk));
      chunkStart = chunkEnd;
    }
    return plans;
  }

  private static void updateEntry(
      Entry entry, HistoricalSimulationParameters parameters, Instant eventTime, Random random) {
    entry.timestampz = timestampz(eventTime, entry.staleAt);
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

  private Feature generateFeature(Entry entry, UUID sourceId, Instant eventTime) {
    entry.element.setCourse(entry.headingDeg);
    entry.element.setSpeed(
        PARAMETERS.moveMetersPerTick() / (PARAMETERS.tickDuration().toMillis() / 1000.0));

    OpenCopProperties openCopProperties = new OpenCopProperties();
    openCopProperties.setId(UUID.randomUUID());
    openCopProperties.setSource(sourceId);
    openCopProperties.setObjectId(entry.objectId);
    openCopProperties.setDisplayName(entry.displayName);
    openCopProperties.setTimestampz(timestampz(eventTime, entry.staleAt));
    openCopProperties.setElement(entry.element);

    return OpenCopPropertiesParser.writeToFeature(openCopProperties, entry.point);
  }

  private static Map<String, String> kafkaHeaders(UUID sourceId) {
    return Map.of(
        OpencopKafkaHeaders.OBJECT_ID.getName(), UUID.randomUUID().toString(),
        OpencopKafkaHeaders.SOURCE_ID.getName(), sourceId.toString(),
        OpencopKafkaHeaders.MAPPER_ID.getName(), "4017d49f-2a68-4900-8e50-2dbb3bd435df");
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

  private static String randomSidc(
      HistoricalSimulationParameters parameters, int index, Random random) {
    if (!parameters.randomSeeded()) {
      return SimulationTest.SIDC_LIST.get(index % SimulationTest.SIDC_LIST.size());
    }
    return SimulationTest.SIDC_LIST.get(random.nextInt(SimulationTest.SIDC_LIST.size()));
  }

  private static double nextDouble(boolean randomMode, Random random, double min, double max) {
    if (!randomMode) {
      return min + ((max - min) / 2.0);
    }
    return min + (random.nextDouble() * (max - min));
  }

  private record BBox(double minLon, double minLat, double maxLon, double maxLat) {}

  private record ChunkPlan(Instant start, Instant end, int uniquePointsInChunk) {
    Duration duration() {
      return Duration.between(start, end);
    }
  }

  private static final class Entry {
    final UUID id;
    final Element element;
    final String objectId;
    final String displayName;

    Point point;
    Timestampz timestampz;
    double headingDeg;
    final Instant staleAt;

    Entry(
        UUID id,
        Point point,
        Element element,
        String objectId,
        String displayName,
        Timestampz timestampz,
        double headingDeg,
        Instant staleAt) {
      this.id = id;
      this.point = point;
      this.element = element;
      this.objectId = objectId;
      this.displayName = displayName;
      this.timestampz = timestampz;
      this.headingDeg = headingDeg;
      this.staleAt = staleAt;
    }
  }

  private record HistoricalSimulationParameters(
      UUID sourceId,
      Instant start,
      Duration timeframe,
      Duration chunkDuration,
      Duration tickDuration,
      int uniquePoints,
      int minUniquePointsPerChunk,
      int maxUniquePointsPerChunk,
      boolean randomSeeded,
      long seed,
      double moveMetersPerTick,
      double turnJitterDeg) {

    int uniquePointsInChunkForIndex(int chunkIndex) {
      if (chunkIndex == 0) {
        return uniquePoints;
      }
      int chunkCount = Math.max(1, (int) (timeframe.toMillis() / chunkDuration.toMillis()));
      int baseCountPerChunk = uniquePoints / chunkCount;
      int remainder = uniquePoints % chunkCount;
      return baseCountPerChunk + (chunkIndex < remainder ? 1 : 0);
    }
  }
}
