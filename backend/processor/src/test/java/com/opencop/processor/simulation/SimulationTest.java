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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.geojson.Feature;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.junit.jupiter.api.BeforeAll;
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
class SimulationTest {

  @Autowired private KafkaTemplate<String, byte[]> template;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final int DOT_COUNT = 1000;
  private static final int TICKS = 60;
  private static final long TICK_MS = 1000;

  // meters per tick
  private static final double MOVE_METERS_PER_TICK = 10_000.0;

  // heading jitter for nicer movement
  private static final double TURN_JITTER_DEG = 6.0;

  // rounding for stability (optional)
  private static final int ROUND_SCALE = 6;

  // minLon, minLat, maxLon, maxLat
  private static final BBox BBOX = new BBox(-90.0, -90.0, 90.0, 90.0);

  // topic + source
  private static final String TOPIC = "opencop.event.external.raw";
  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

  private static final Map<UUID, Entry> entries = Collections.synchronizedMap(new HashMap<>());

  @BeforeAll
  static void beforeAll() {
    entries.clear();

    for (int i = 0; i < DOT_COUNT; i++) {
      UUID id = UUID.randomUUID();

      double lat = ThreadLocalRandom.current().nextDouble(BBOX.minLat, BBOX.maxLat);
      double lon = ThreadLocalRandom.current().nextDouble(BBOX.minLon, BBOX.maxLon);
      double headingDeg = ThreadLocalRandom.current().nextDouble(0, 360);

      Element element =
          new PointElement.Builder().symbol(randomSidc()).type(ElementTypes.SYMBOL).build();

      Timestampz timestampz = new Timestampz();
      Instant now = Instant.now();
      timestampz.setTime(now.toEpochMilli());
      timestampz.setStart(now.toEpochMilli());
      timestampz.setStale(now.plus(Duration.ofMinutes(5)).toEpochMilli());

      entries.put(
          id,
          new Entry(
              id,
              new Point(new LngLatAlt(lon, lat)),
              element,
              UUID.randomUUID().toString(),
              String.valueOf(i),
              timestampz,
              headingDeg));
    }
  }

  public static String randomSidc() {
    int randomIndex =
        ThreadLocalRandom.current().nextInt(SIDC_LIST.size()); // 0 (inclusive) to size (exclusive)

    return SIDC_LIST.get(randomIndex);
  }

  @Test
  @Disabled
  void simulation() throws Exception {
    MessageBrokerProducer<byte[]> broker = new KafkaProducer<>(this.template, TOPIC);

    for (int tick = 0; tick < TICKS; tick++) {

      synchronized (entries) {
        for (Entry entry : entries.values()) {
          updateEntry(entry);

          Feature feature = generateFeature(entry);
          byte[] payload = objectMapper.writeValueAsBytes(feature);

          broker.send(
              payload,
              Map.of(
                  OpencopKafkaHeaders.OBJECT_ID.getName(), UUID.randomUUID().toString(),
                  OpencopKafkaHeaders.SOURCE_ID.getName(), SOURCE_ID.toString(),
                  OpencopKafkaHeaders.MAPPER_ID.getName(), "4017d49f-2a68-4900-8e50-2dbb3bd435df"));
        }
      }

      Thread.sleep(TICK_MS);
    }
  }

  private void updateEntry(Entry entry) {
    Timestampz timestampz = new Timestampz();
    Instant now = Instant.now();
    timestampz.setTime(now.toEpochMilli());
    timestampz.setStart(now.toEpochMilli());
    timestampz.setStale(now.plus(Duration.ofMinutes(5)).toEpochMilli());
    entry.timestampz = timestampz;

    double jitter = ThreadLocalRandom.current().nextDouble(-TURN_JITTER_DEG, TURN_JITTER_DEG);
    entry.headingDeg = normalizeDeg(entry.headingDeg + jitter);

    moveByMeters(entry, MOVE_METERS_PER_TICK, entry.headingDeg);

    LngLatAlt ll = entry.point.getCoordinates();
    double lon = ll.getLongitude();
    double lat = ll.getLatitude();

    if (lon < BBOX.minLon) {
      lon = BBOX.minLon;
      entry.headingDeg = normalizeDeg(180 - entry.headingDeg);
    } else if (lon > BBOX.maxLon) {
      lon = BBOX.maxLon;
      entry.headingDeg = normalizeDeg(180 - entry.headingDeg);
    }

    if (lat < BBOX.minLat) {
      lat = BBOX.minLat;
      entry.headingDeg = normalizeDeg(-entry.headingDeg);
    } else if (lat > BBOX.maxLat) {
      lat = BBOX.maxLat;
      entry.headingDeg = normalizeDeg(-entry.headingDeg);
    }

    lon = new BigDecimal(lon).setScale(ROUND_SCALE, RoundingMode.HALF_UP).doubleValue();
    lat = new BigDecimal(lat).setScale(ROUND_SCALE, RoundingMode.HALF_UP).doubleValue();

    entry.point.setCoordinates(new LngLatAlt(lon, lat));
  }

  private Feature generateFeature(Entry entry) {
    entry.element.setCourse(entry.headingDeg);
    entry.element.setSpeed(MOVE_METERS_PER_TICK / (TICK_MS / 1000.0));

    OpenCopProperties openCopProperties = new OpenCopProperties();
    openCopProperties.setId(UUID.randomUUID());
    openCopProperties.setSource(SOURCE_ID);
    openCopProperties.setObjectId(entry.objectId);
    openCopProperties.setDisplayName(entry.displayName);
    openCopProperties.setTimestampz(entry.timestampz);
    openCopProperties.setElement(entry.element);

    return OpenCopPropertiesParser.writeToFeature(openCopProperties, entry.point);
  }

  private void moveByMeters(Entry entry, double meters, double bearingDeg) {
    double br = Math.toRadians(bearingDeg);
    double dNorth = meters * Math.cos(br);
    double dEast = meters * Math.sin(br);

    LngLatAlt ll = entry.point.getCoordinates();
    double lat = ll.getLatitude();
    double lon = ll.getLongitude();

    double metersPerDegLat = 111_320.0;
    double metersPerDegLon = 111_320.0 * Math.cos(Math.toRadians(lat));
    if (Math.abs(metersPerDegLon) < 1e-6) metersPerDegLon = 1e-6;

    lat += dNorth / metersPerDegLat;
    lon += dEast / metersPerDegLon;

    entry.point.setCoordinates(new LngLatAlt(lon, lat));
  }

  private static double normalizeDeg(double d) {
    double r = d % 360.0;
    return r < 0 ? r + 360.0 : r;
  }

  private record BBox(double minLon, double minLat, double maxLon, double maxLat) {}

  // Mutable entry (records are annoying for “tick updates”)
  private static final class Entry {
    final UUID id;
    final Element element;
    final String objectId;
    final String displayName;

    Point point;
    Timestampz timestampz;
    double headingDeg;

    Entry(
        UUID id,
        Point point,
        Element element,
        String objectId,
        String displayName,
        Timestampz timestampz,
        double headingDeg) {
      this.id = id;
      this.point = point;
      this.element = element;
      this.objectId = objectId;
      this.displayName = displayName;
      this.timestampz = timestampz;
      this.headingDeg = headingDeg;
    }
  }

  static final List<String> SIDC_LIST =
      List.of(
          // Pending (0) - 10
          "10001000000000000000",
          "10001000000000000001",
          "10001000000000000002",
          "10001000000000000003",
          "10001000000000000004",
          "10001000000000000005",
          "10001000000000000006",
          "10001000000000000007",
          "10001000000000000008",
          "10001000000000000009",

          // Unknown (1) - 15
          "10011000000000000010",
          "10011000000000000011",
          "10011000000000000012",
          "10011000000000000013",
          "10011000000000000014",
          "10011000000000000015",
          "10011000000000000016",
          "10011000000000000017",
          "10011000000000000018",
          "10011000000000000019",
          "10011000000000000020",
          "10011000000000000021",
          "10011000000000000022",
          "10011000000000000023",
          "10011000000000000024",

          // Assumed Friendly (2) - 15
          "10021000000000000025",
          "10021000000000000026",
          "10021000000000000027",
          "10021000000000000028",
          "10021000000000000029",
          "10021000000000000030",
          "10021000000000000031",
          "10021000000000000032",
          "10021000000000000033",
          "10021000000000000034",
          "10021000000000000035",
          "10021000000000000036",
          "10021000000000000037",
          "10021000000000000038",
          "10021000000000000039",

          // Friendly (3) - 15
          "10031000000000000040",
          "10031000000000000041",
          "10031000000000000042",
          "10031000000000000043",
          "10031000000000000044",
          "10031000000000000045",
          "10031000000000000046",
          "10031000000000000047",
          "10031000000000000048",
          "10031000000000000049",
          "10031000000000000050",
          "10031000000000000051",
          "10031000000000000052",
          "10031000000000000053",
          "10031000000000000054",

          // Neutral (4) - 15
          "10041000000000000055",
          "10041000000000000056",
          "10041000000000000057",
          "10041000000000000058",
          "10041000000000000059",
          "10041000000000000060",
          "10041000000000000061",
          "10041000000000000062",
          "10041000000000000063",
          "10041000000000000064",
          "10041000000000000065",
          "10041000000000000066",
          "10041000000000000067",
          "10041000000000000068",
          "10041000000000000069",

          // Suspect (5) - 15
          "10051000000000000070",
          "10051000000000000071",
          "10051000000000000072",
          "10051000000000000073",
          "10051000000000000074",
          "10051000000000000075",
          "10051000000000000076",
          "10051000000000000077",
          "10051000000000000078",
          "10051000000000000079",
          "10051000000000000080",
          "10051000000000000081",
          "10051000000000000082",
          "10051000000000000083",
          "10051000000000000084",

          // Hostile (6) - 15
          "10061000000000000085",
          "10061000000000000086",
          "10061000000000000087",
          "10061000000000000088",
          "10061000000000000089",
          "10061000000000000090",
          "10061000000000000091",
          "10061000000000000092",
          "10061000000000000093",
          "10061000000000000094",
          "10061000000000000095",
          "10061000000000000096",
          "10061000000000000097",
          "10061000000000000098",
          "10061000000000000099");
}
