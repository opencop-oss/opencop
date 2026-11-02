// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.mapping.ingress;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TreeMap;
import org.jmisb.api.klv.st0601.FrameCenterLatitude;
import org.jmisb.api.klv.st0601.FrameCenterLongitude;
import org.jmisb.api.klv.st0601.IUasDatalinkValue;
import org.jmisb.api.klv.st0601.PrecisionTimeStamp;
import org.jmisb.api.klv.st0601.SensorLatitude;
import org.jmisb.api.klv.st0601.SensorLongitude;
import org.jmisb.api.klv.st0601.SensorTrueAltitude;
import org.jmisb.api.klv.st0601.UasDatalinkMessage;
import org.jmisb.api.klv.st0601.UasDatalinkString;
import org.jmisb.api.klv.st0601.UasDatalinkTag;
import org.junit.jupiter.api.Test;

class KlvTelemetryParserTest {

  // --- Null / short input guards ---

  @Test
  void returnsEmptyResultForNullInput() {
    assertAllNull(KlvTelemetryParser.parse(null));
  }

  @Test
  void returnsEmptyResultForEmptyArray() {
    assertAllNull(KlvTelemetryParser.parse(new byte[0]));
  }

  @Test
  void returnsEmptyResultForMalformedInput() {
    assertAllNull(KlvTelemetryParser.parse(new byte[] {0x06, 0x0e, 0x2b}));
  }

  // --- Timestamp (tag 2) ---

  @Test
  void parsesTimestampFromTag2() {
    byte[] unit = buildUnit(UasDatalinkTag.PrecisionTimeStamp, new PrecisionTimeStamp(1_000_000L));

    var result = KlvTelemetryParser.parse(unit);

    assertThat(result.timestampMicros()).isEqualTo(1_000_000L);
  }

  // --- Sensor position (tags 13/14) ---

  @Test
  void parsesSensorLatLon() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.SensorLatitude, new SensorLatitude(45.0),
            UasDatalinkTag.SensorLongitude, new SensorLongitude(90.0));

    var result = KlvTelemetryParser.parse(unit);

    assertThat(result.sensorLat()).isCloseTo(45.0, within(0.001));
    assertThat(result.sensorLon()).isCloseTo(90.0, within(0.001));
  }

  @Test
  void parsesSensorLatLonNegativeValues() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.SensorLatitude, new SensorLatitude(-45.0),
            UasDatalinkTag.SensorLongitude, new SensorLongitude(-90.0));

    var result = KlvTelemetryParser.parse(unit);

    assertThat(result.sensorLat()).isCloseTo(-45.0, within(0.001));
    assertThat(result.sensorLon()).isCloseTo(-90.0, within(0.001));
  }

  // --- Frame center (tags 23/24) ---

  @Test
  void parsesFrameCenterLatLon() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.FrameCenterLatitude, new FrameCenterLatitude(45.0),
            UasDatalinkTag.FrameCenterLongitude, new FrameCenterLongitude(90.0));

    var result = KlvTelemetryParser.parse(unit);

    assertThat(result.frameCenterLat()).isCloseTo(45.0, within(0.001));
    assertThat(result.frameCenterLon()).isCloseTo(90.0, within(0.001));
  }

  // --- Altitude (tag 15) ---

  @Test
  void parsesAltitudeFromTag15() {
    byte[] unit = buildUnit(UasDatalinkTag.SensorTrueAltitude, new SensorTrueAltitude(-900.0));

    var result = KlvTelemetryParser.parse(unit);

    assertThat(result.sensorAltM()).isCloseTo(-900.0, within(1.0));
  }

  // --- Platform designation (tag 10) ---

  @Test
  void parsesPlatformDesignation() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.PlatformDesignation,
            new UasDatalinkString(UasDatalinkString.PLATFORM_DESIGNATION, "SYNTHETIC-PLATFORM"));

    var result = KlvTelemetryParser.parse(unit);

    assertThat(result.platformDesignation()).isEqualTo("SYNTHETIC-PLATFORM");
  }

  @Test
  void returnsNullForBlankPlatformDesignation() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.PlatformDesignation,
            new UasDatalinkString(UasDatalinkString.PLATFORM_DESIGNATION, "  "));

    var result = KlvTelemetryParser.parse(unit);

    assertThat(result.platformDesignation()).isNull();
  }

  // --- Multi-tag ---

  @Test
  void parsesMultipleTagsInOneUnit() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.PrecisionTimeStamp, new PrecisionTimeStamp(1_000_000L),
            UasDatalinkTag.FrameCenterLatitude, new FrameCenterLatitude(45.0),
            UasDatalinkTag.FrameCenterLongitude, new FrameCenterLongitude(90.0));

    var result = KlvTelemetryParser.parse(unit);

    assertThat(result.timestampMicros()).isEqualTo(1_000_000L);
    assertThat(result.frameCenterLat()).isCloseTo(45.0, within(0.001));
    assertThat(result.frameCenterLon()).isCloseTo(90.0, within(0.001));
  }

  // --- Platform tail number (tag 70) ---

  @Test
  void parsesPlatformTailNumber() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.PlatformTailNumber,
            new UasDatalinkString(UasDatalinkString.PLATFORM_TAIL_NUMBER, "TEST-TAIL-001"));

    var result = KlvTelemetryParser.parse(unit);

    assertThat(result.platformTailNumber()).isEqualTo("TEST-TAIL-001");
  }

  @Test
  void normalisesPlatformTailNumberToUpperCase() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.PlatformTailNumber,
            new UasDatalinkString(UasDatalinkString.PLATFORM_TAIL_NUMBER, "test-tail-001"));

    var result = KlvTelemetryParser.parse(unit);

    assertThat(result.platformTailNumber()).isEqualTo("TEST-TAIL-001");
  }

  @Test
  void returnsNullForBlankPlatformTailNumber() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.PlatformTailNumber,
            new UasDatalinkString(UasDatalinkString.PLATFORM_TAIL_NUMBER, "  "));

    var result = KlvTelemetryParser.parse(unit);

    assertThat(result.platformTailNumber()).isNull();
  }

  // --- Helpers ---

  private static void assertAllNull(KlvTelemetryParser.ParsedLocalSet result) {
    assertThat(result.timestampMicros()).isNull();
    assertThat(result.sensorLat()).isNull();
    assertThat(result.sensorLon()).isNull();
    assertThat(result.sensorAltM()).isNull();
    assertThat(result.frameCenterLat()).isNull();
    assertThat(result.frameCenterLon()).isNull();
    assertThat(result.platformCallSign()).isNull();
    assertThat(result.platformDesignation()).isNull();
    assertThat(result.platformTailNumber()).isNull();
    assertThat(result.missionId()).isNull();
    assertThat(result.miisPlatformUUID()).isNull();
  }

  private static org.assertj.core.data.Offset<Double> within(double delta) {
    return org.assertj.core.data.Offset.offset(delta);
  }

  /** Builds a complete ST 0601 KLV unit from tag/value pairs using jMISB. */
  private static byte[] buildUnit(Object... tagValuePairs) {
    var fields = new TreeMap<UasDatalinkTag, IUasDatalinkValue>();
    for (int i = 0; i < tagValuePairs.length; i += 2) {
      fields.put((UasDatalinkTag) tagValuePairs[i], (IUasDatalinkValue) tagValuePairs[i + 1]);
    }
    return new UasDatalinkMessage(fields).frameMessage(false);
  }
}
