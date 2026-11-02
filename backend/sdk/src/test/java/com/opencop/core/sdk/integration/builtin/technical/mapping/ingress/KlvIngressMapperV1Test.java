// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.mapping.ingress;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.ports.gateway.mapping.ingress.ObjectIdStability;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;
import java.util.UUID;
import org.geojson.Point;
import org.jmisb.api.klv.st0601.FrameCenterLatitude;
import org.jmisb.api.klv.st0601.FrameCenterLongitude;
import org.jmisb.api.klv.st0601.IUasDatalinkValue;
import org.jmisb.api.klv.st0601.SensorLatitude;
import org.jmisb.api.klv.st0601.SensorLongitude;
import org.jmisb.api.klv.st0601.SensorTrueAltitude;
import org.jmisb.api.klv.st0601.UasDatalinkMessage;
import org.jmisb.api.klv.st0601.UasDatalinkString;
import org.jmisb.api.klv.st0601.UasDatalinkTag;
import org.junit.jupiter.api.Test;

class KlvIngressMapperV1Test {

  private static final UUID SOURCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OBJECT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private final KlvIngressMapperV1 decoder = new KlvIngressMapperV1();

  // --- Metadata ---

  @Test
  void exposesStableMapperMetadata() {
    assertThat(decoder.id()).isEqualTo(KlvIngressMapperV1.MAPPER_ID);
    assertThat(decoder.name()).isEqualTo("KLV Metadata");
    assertThat(decoder.version()).isEqualTo(1);
    assertThat(decoder.objectIdStability()).isEqualTo(ObjectIdStability.STABLE_PER_ENTITY);
  }

  // --- No position → empty list ---

  @Test
  void returnsEmptyListWhenNoPosition() {
    var props = new MappingProperties(SOURCE_ID, null, false);

    var features = decoder.map(OBJECT_ID, props, new byte[0]);

    assertThat(features).isEmpty();
  }

  // --- KLV position tags ---

  @Test
  void usesSensorPositionFromKlv() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.SensorLatitude, new SensorLatitude(45.0),
            UasDatalinkTag.SensorLongitude, new SensorLongitude(90.0));
    var props = new MappingProperties(SOURCE_ID, null, false);

    var features = decoder.map(OBJECT_ID, props, unit);

    assertThat(features).hasSize(1);
    var point = (Point) features.get(0).getGeometry();
    assertThat(point.getCoordinates().getLatitude()).isCloseTo(45.0, offset(0.001));
    assertThat(point.getCoordinates().getLongitude()).isCloseTo(90.0, offset(0.001));
  }

  @Test
  void prefersSensorPositionOverFrameCenter() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.SensorLatitude, new SensorLatitude(45.0),
            UasDatalinkTag.SensorLongitude, new SensorLongitude(90.0),
            UasDatalinkTag.FrameCenterLatitude, new FrameCenterLatitude(-45.0),
            UasDatalinkTag.FrameCenterLongitude, new FrameCenterLongitude(-90.0));
    var props = new MappingProperties(SOURCE_ID, null, false);

    var features = decoder.map(OBJECT_ID, props, unit);

    assertThat(features).hasSize(1);
    var point = (Point) features.get(0).getGeometry();
    assertThat(point.getCoordinates().getLatitude()).isCloseTo(45.0, offset(0.001));
    assertThat(point.getCoordinates().getLongitude()).isCloseTo(90.0, offset(0.001));
  }

  @Test
  void includesAltitudeWhenPresentInKlv() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.SensorLatitude, new SensorLatitude(45.0),
            UasDatalinkTag.SensorLongitude, new SensorLongitude(90.0),
            UasDatalinkTag.SensorTrueAltitude, new SensorTrueAltitude(-900.0));
    var props = new MappingProperties(SOURCE_ID, null, false);

    var features = decoder.map(OBJECT_ID, props, unit);

    var point = (Point) features.get(0).getGeometry();
    assertThat(point.getCoordinates().hasAltitude()).isTrue();
    assertThat(point.getCoordinates().getAltitude()).isCloseTo(-900.0, offset(1.0));
  }

  // --- Feature identity and properties ---

  @Test
  void setsObjectIdFromParameter() {
    UUID entityId = UUID.nameUUIDFromBytes(SOURCE_ID.toString().getBytes(StandardCharsets.UTF_8));
    byte[] unit =
        buildUnit(
            UasDatalinkTag.FrameCenterLatitude, new FrameCenterLatitude(51.5),
            UasDatalinkTag.FrameCenterLongitude, new FrameCenterLongitude(0.0));
    var props = new MappingProperties(SOURCE_ID, null, false);

    var features = decoder.map(entityId, props, unit);

    OpenCopProperties oc = OpenCopPropertiesParser.readFromFeature(features.get(0));
    assertThat(oc.getObjectId()).isEqualTo(entityId.toString());
    assertThat(oc.getSource()).isEqualTo(SOURCE_ID);
    assertThat(oc.getKind()).isEqualTo("rtsp.klv.v1");
  }

  @Test
  void derivesObjectIdFromTailNumberWhenPresent() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.SensorLatitude,
            new SensorLatitude(45.0),
            UasDatalinkTag.SensorLongitude,
            new SensorLongitude(90.0),
            UasDatalinkTag.PlatformTailNumber,
            new UasDatalinkString(UasDatalinkString.PLATFORM_TAIL_NUMBER, "TEST-TAIL-001"));
    var props = new MappingProperties(SOURCE_ID, null, false);
    UUID expected =
        UUID.nameUUIDFromBytes((SOURCE_ID + ":TEST-TAIL-001").getBytes(StandardCharsets.UTF_8));

    var features = decoder.map(OBJECT_ID, props, unit);

    OpenCopProperties oc = OpenCopPropertiesParser.readFromFeature(features.get(0));
    assertThat(oc.getObjectId()).isEqualTo(expected.toString());
  }

  @Test
  void fallsBackToSourceIdDerivedObjectIdWhenNoKlvIdentifier() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.SensorLatitude, new SensorLatitude(45.0),
            UasDatalinkTag.SensorLongitude, new SensorLongitude(90.0));
    var props = new MappingProperties(SOURCE_ID, null, false);
    UUID expected = UUID.nameUUIDFromBytes(SOURCE_ID.toString().getBytes(StandardCharsets.UTF_8));

    var features = decoder.map(OBJECT_ID, props, unit);

    OpenCopProperties oc = OpenCopPropertiesParser.readFromFeature(features.get(0));
    assertThat(oc.getObjectId()).isEqualTo(expected.toString());
  }

  // --- Display name ---

  @Test
  void prefersPlatformCallSignAsDisplayName() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.PlatformCallSign,
            new UasDatalinkString(UasDatalinkString.PLATFORM_CALL_SIGN, "TEST-CALLSIGN-001"),
            UasDatalinkTag.PlatformDesignation,
            new UasDatalinkString(UasDatalinkString.PLATFORM_DESIGNATION, "SYNTHETIC-PLATFORM"),
            UasDatalinkTag.FrameCenterLatitude,
            new FrameCenterLatitude(45.0),
            UasDatalinkTag.FrameCenterLongitude,
            new FrameCenterLongitude(90.0));
    var props = new MappingProperties(SOURCE_ID, null, false);

    var features = decoder.map(OBJECT_ID, props, unit);

    OpenCopProperties oc = OpenCopPropertiesParser.readFromFeature(features.get(0));
    assertThat(oc.getDisplayName()).isEqualTo("TEST-CALLSIGN-001");
  }

  @Test
  void fallsBackToPlatformDesignationWhenNoCallSign() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.PlatformDesignation,
            new UasDatalinkString(UasDatalinkString.PLATFORM_DESIGNATION, "SYNTHETIC-PLATFORM"),
            UasDatalinkTag.FrameCenterLatitude,
            new FrameCenterLatitude(45.0),
            UasDatalinkTag.FrameCenterLongitude,
            new FrameCenterLongitude(90.0));
    var props = new MappingProperties(SOURCE_ID, null, false);

    var features = decoder.map(OBJECT_ID, props, unit);

    OpenCopProperties oc = OpenCopPropertiesParser.readFromFeature(features.get(0));
    assertThat(oc.getDisplayName()).isEqualTo("SYNTHETIC-PLATFORM");
  }

  @Test
  void displayNameIsNullWhenNoIdentityTags() {
    byte[] unit =
        buildUnit(
            UasDatalinkTag.FrameCenterLatitude, new FrameCenterLatitude(45.0),
            UasDatalinkTag.FrameCenterLongitude, new FrameCenterLongitude(90.0));
    var props = new MappingProperties(SOURCE_ID, null, false);

    var features = decoder.map(OBJECT_ID, props, unit);

    OpenCopProperties oc = OpenCopPropertiesParser.readFromFeature(features.get(0));
    assertThat(oc.getDisplayName()).isNull();
  }

  // --- Helpers ---

  private static org.assertj.core.data.Offset<Double> offset(double d) {
    return org.assertj.core.data.Offset.offset(d);
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
