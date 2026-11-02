// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.mapping.ingress;

import java.util.UUID;
import org.jmisb.api.klv.KlvParser;
import org.jmisb.api.klv.st0601.FrameCenterLatitude;
import org.jmisb.api.klv.st0601.FrameCenterLongitude;
import org.jmisb.api.klv.st0601.MiisCoreIdentifier;
import org.jmisb.api.klv.st0601.PrecisionTimeStamp;
import org.jmisb.api.klv.st0601.SensorLatitude;
import org.jmisb.api.klv.st0601.SensorLongitude;
import org.jmisb.api.klv.st0601.SensorTrueAltitude;
import org.jmisb.api.klv.st0601.UasDatalinkMessage;
import org.jmisb.api.klv.st0601.UasDatalinkString;
import org.jmisb.api.klv.st0601.UasDatalinkTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses a complete MISB ST 0601 KLV unit into position and identity fields.
 *
 * <p>Receives a fully-framed unit (universal key + BER length + local-set payload) as produced by
 * {@code KlvPacketFramer}. Delegates to jMISB for spec-compliant decoding.
 */
public final class KlvTelemetryParser {

  private static final Logger log = LoggerFactory.getLogger(KlvTelemetryParser.class);

  private static final ParsedLocalSet EMPTY =
      new ParsedLocalSet(null, null, null, null, null, null, null, null, null, null, null);

  private KlvTelemetryParser() {}

  public record ParsedLocalSet(
      Long timestampMicros,
      Double sensorLat,
      Double sensorLon,
      Double sensorAltM,
      Double frameCenterLat,
      Double frameCenterLon,
      String platformCallSign,
      String platformDesignation,
      String platformTailNumber,
      String missionId,
      UUID miisPlatformUUID) {}

  public static ParsedLocalSet parse(byte[] unit) {
    if (unit == null || unit.length == 0) {
      return EMPTY;
    }
    try {
      for (var message : KlvParser.parseBytes(unit)) {
        if (message instanceof UasDatalinkMessage uds) {
          return extract(uds);
        }
      }
    } catch (Exception e) {
      log.debug("KLV parse failed: {}", e.getMessage());
    }
    return EMPTY;
  }

  private static ParsedLocalSet extract(UasDatalinkMessage uds) {
    var result =
        new ParsedLocalSet(
            timestamp(uds),
            sensorLat(uds),
            sensorLon(uds),
            sensorAlt(uds),
            frameCenterLat(uds),
            frameCenterLon(uds),
            platformCallSign(uds),
            platformDesignation(uds),
            platformTailNumber(uds),
            missionId(uds),
            miisPlatformUUID(uds));
    return result;
  }

  private static Long timestamp(UasDatalinkMessage uds) {
    var f = uds.getField(UasDatalinkTag.PrecisionTimeStamp);
    return f instanceof PrecisionTimeStamp ts ? ts.getMicroseconds() : null;
  }

  private static Double sensorLat(UasDatalinkMessage uds) {
    var f = uds.getField(UasDatalinkTag.SensorLatitude);
    return f instanceof SensorLatitude v ? v.getDegrees() : null;
  }

  private static Double sensorLon(UasDatalinkMessage uds) {
    var f = uds.getField(UasDatalinkTag.SensorLongitude);
    return f instanceof SensorLongitude v ? v.getDegrees() : null;
  }

  private static Double sensorAlt(UasDatalinkMessage uds) {
    var f = uds.getField(UasDatalinkTag.SensorTrueAltitude);
    return f instanceof SensorTrueAltitude v ? v.getMeters() : null;
  }

  private static Double frameCenterLat(UasDatalinkMessage uds) {
    var f = uds.getField(UasDatalinkTag.FrameCenterLatitude);
    return f instanceof FrameCenterLatitude v ? v.getDegrees() : null;
  }

  private static Double frameCenterLon(UasDatalinkMessage uds) {
    var f = uds.getField(UasDatalinkTag.FrameCenterLongitude);
    return f instanceof FrameCenterLongitude v ? v.getDegrees() : null;
  }

  private static String platformCallSign(UasDatalinkMessage uds) {
    var f = uds.getField(UasDatalinkTag.PlatformCallSign);
    if (!(f instanceof UasDatalinkString str)) return null;
    var s = str.getValue();
    return (s == null || s.isBlank()) ? null : s.strip();
  }

  private static String missionId(UasDatalinkMessage uds) {
    var f = uds.getField(UasDatalinkTag.MissionId);
    if (!(f instanceof UasDatalinkString str)) return null;
    var s = str.getValue();
    return (s == null || s.isBlank()) ? null : s.strip();
  }

  private static String platformDesignation(UasDatalinkMessage uds) {
    var f = uds.getField(UasDatalinkTag.PlatformDesignation);
    if (!(f instanceof UasDatalinkString str)) return null;
    var s = str.getValue();
    return (s == null || s.isBlank()) ? null : s.strip();
  }

  private static String platformTailNumber(UasDatalinkMessage uds) {
    var f = uds.getField(UasDatalinkTag.PlatformTailNumber);
    if (!(f instanceof UasDatalinkString str)) return null;
    var s = str.getValue();
    return (s == null || s.isBlank()) ? null : s.strip().toUpperCase(java.util.Locale.ROOT);
  }

  private static UUID miisPlatformUUID(UasDatalinkMessage uds) {
    var f = uds.getField(UasDatalinkTag.MiisCoreIdentifier);
    if (!(f instanceof MiisCoreIdentifier miis)) return null;
    try {
      return miis.getCoreIdentifier().getPlatformUUID();
    } catch (Exception e) {
      log.debug("MIIS UUID extraction failed: {}", e.getMessage());
      return null;
    }
  }
}
