// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

import com.opencop.core.api.watchbox.business.model.WatchboxSettings;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxLayerAccess;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;
import org.junit.jupiter.api.Test;

class WatchboxGeometryValidatorTest {

  private final WatchboxValidationService validator = validator();

  private static WatchboxValidationService validator() {
    WatchboxLayerAccess layerAccess = mock(WatchboxLayerAccess.class);
    return new WatchboxValidationService(new WatchboxSettings(2_000), layerAccess);
  }

  @Test
  void acceptsClosedOneRingPolygon() {
    Polygon normalized = validator.validateAndNormalizePolygon(square());

    assertThat(normalized.getExteriorRing()).hasSize(5);
    assertThat(normalized.getExteriorRing())
        .extracting(LngLatAlt::getLongitude, LngLatAlt::getLatitude)
        .containsExactly(
            tuple(-100.2, 39.8),
            tuple(-99.8, 39.8),
            tuple(-99.8, 40.2),
            tuple(-100.2, 40.2),
            tuple(-100.2, 39.8));
    assertThat(normalized.getInteriorRings()).isEmpty();
  }

  @Test
  void acceptsClosedTrianglePolygon() {
    var triangle =
        new Polygon(
            java.util.List.of(
                new LngLatAlt(-100.2, 39.8),
                new LngLatAlt(-99.8, 39.8),
                new LngLatAlt(-100.0, 40.0),
                new LngLatAlt(-100.2, 39.8)));

    Polygon normalized = validator.validateAndNormalizePolygon(triangle);

    assertThat(normalized.getExteriorRing()).hasSize(4);
  }

  @Test
  void rejectsFewerThanThreeUniqueVertices() {
    var line =
        new Polygon(
            java.util.List.of(
                new LngLatAlt(-100.2, 39.8),
                new LngLatAlt(-99.8, 39.8),
                new LngLatAlt(-100.2, 39.8),
                new LngLatAlt(-100.2, 39.8)));

    assertThatThrownBy(() -> validator.validateAndNormalizePolygon(line))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_INVALID_POLYGON));
  }

  @Test
  void rejectsUnclosedPolygon() {
    var polygon =
        new Polygon(
            java.util.List.of(
                new LngLatAlt(-100.2, 39.8),
                new LngLatAlt(-99.8, 39.8),
                new LngLatAlt(-99.8, 40.2),
                new LngLatAlt(-100.2, 40.2)));

    assertThatThrownBy(() -> validator.validateAndNormalizePolygon(polygon))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_INVALID_POLYGON));
  }

  @Test
  void rejectsHolesAndAntimeridianCrossing() {
    var hole = square();
    hole.addInteriorRing(
        new LngLatAlt(-100.1, 39.9),
        new LngLatAlt(-99.9, 39.9),
        new LngLatAlt(-99.9, 40.1),
        new LngLatAlt(-100.1, 40.1),
        new LngLatAlt(-100.1, 39.9));

    assertThatThrownBy(() -> validator.validateAndNormalizePolygon(hole))
        .isInstanceOf(WatchboxException.class);

    var antimeridian =
        new Polygon(
            java.util.List.of(
                new LngLatAlt(179.0, 10.0),
                new LngLatAlt(-179.0, 10.0),
                new LngLatAlt(-179.0, 11.0),
                new LngLatAlt(179.0, 11.0),
                new LngLatAlt(179.0, 10.0)));

    assertThatThrownBy(() -> validator.validateAndNormalizePolygon(antimeridian))
        .isInstanceOf(WatchboxException.class);
  }

  @Test
  void rejectsSelfIntersectingPolygon() {
    var bowtie =
        new Polygon(
            java.util.List.of(
                new LngLatAlt(-100.2, 39.8),
                new LngLatAlt(-99.8, 40.2),
                new LngLatAlt(-100.2, 40.2),
                new LngLatAlt(-99.8, 39.8),
                new LngLatAlt(-100.2, 39.8)));

    assertThatThrownBy(() -> validator.validateAndNormalizePolygon(bowtie))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_INVALID_POLYGON);
              // A self-crossing ring says so; every other validity failure stays generic.
              assertThat(ex.getMessage()).contains("crosses itself");
            });
  }

  @Test
  void acceptsPolygonWithARepeatedVertex() {
    // A double-click while drawing repeats a vertex. The client's turf.kinks check
    // tolerates this too; if either side changes, the two disagree and a drawable
    // shape becomes unsavable.
    var repeated =
        new Polygon(
            java.util.List.of(
                new LngLatAlt(-100.2, 39.8),
                new LngLatAlt(-99.8, 39.8),
                new LngLatAlt(-99.8, 39.8),
                new LngLatAlt(-99.8, 40.2),
                new LngLatAlt(-100.2, 40.2),
                new LngLatAlt(-100.2, 39.8)));

    assertThat(validator.validateAndNormalizePolygon(repeated).getExteriorRing()).hasSize(6);
  }

  static Polygon square() {
    return new Polygon(
        java.util.List.of(
            new LngLatAlt(-100.2, 39.8),
            new LngLatAlt(-99.8, 39.8),
            new LngLatAlt(-99.8, 40.2),
            new LngLatAlt(-100.2, 40.2),
            new LngLatAlt(-100.2, 39.8)));
  }
}
