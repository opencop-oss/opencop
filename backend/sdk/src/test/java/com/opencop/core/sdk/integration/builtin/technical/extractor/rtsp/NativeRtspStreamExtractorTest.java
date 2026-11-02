// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.extractor.rtsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.business.model.integration.connection.input.DeclaredConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorFieldKind;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import com.opencop.core.sdk.integration.builtin.business.policy.rtsp.RtspCoordinatesPolicy;
import com.opencop.core.sdk.integration.builtin.business.policy.rtsp.RtspUrlPolicy;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import java.util.ArrayList;
import java.util.List;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.junit.jupiter.api.Test;

class NativeRtspStreamExtractorTest {

  private static final String URL = "rtsp://camera.example.test/live";

  private final NativeRtspStreamExtractor extractor =
      new NativeRtspStreamExtractor(new NativeConnectionSchema(new NativeIntegration()));

  @Test
  void belongsToNativeRtsp() {
    assertThat(extractor.protocol()).isEqualTo(TransportProtocol.RTSP);
    assertThat(extractor.schema().reference()).isEqualTo(NativeConnectionSchema.V1_REFERENCE);
    assertThat(extractor.descriptor().id()).isEqualTo(NativeRtspStreamExtractor.EXTRACTOR_ID);
  }

  @Test
  void declaresARequiredUrlAndAnOptionalCameraPosition() {
    assertThat(extractor.descriptor().fields())
        .extracting(ExtractorField::name, ExtractorField::kind, ExtractorField::required)
        .containsExactly(
            tuple("url", ExtractorFieldKind.TEXT, true),
            tuple("latitude", ExtractorFieldKind.TEXT, false),
            tuple("longitude", ExtractorFieldKind.TEXT, false));
  }

  @Test
  void extractsTheStreamAtTheSuppliedCameraPosition() throws ConnectionInputException {
    var material = extractor.extract(input(URL, "45.5", "-73.6"));

    assertThat(material.endpoint().url()).isEqualTo(URL);
    assertThat(material.endpoint().coordinates()).isEqualTo(new Point(new LngLatAlt(-73.6, 45.5)));
  }

  @Test
  void extractsAStreamWithNoCameraPosition() throws ConnectionInputException {
    assertThat(extractor.extract(input(URL, null, null)).endpoint().coordinates()).isNull();
  }

  @Test
  void rejectsAnUnusableUrlOrPartialPosition() {
    assertThatThrownBy(() -> extractor.validate(input("http://camera.example.test", null, null)))
        .isInstanceOfSatisfying(
            ConnectionInputException.class,
            exception ->
                assertThat(exception.providerDetailCode())
                    .contains(RtspUrlPolicy.UNSUPPORTED_SCHEME));
    assertThatThrownBy(() -> extractor.validate(input(URL, "45.5", null)))
        .isInstanceOfSatisfying(
            ConnectionInputException.class,
            exception ->
                assertThat(exception.providerDetailCode())
                    .contains(RtspCoordinatesPolicy.INCOMPLETE_COORDINATES));
  }

  private static DeclaredConnectionInput input(String url, String latitude, String longitude) {
    var values = new ArrayList<ConnectionInputValue>();
    if (url != null) {
      values.add(new ConnectionInputValue("url", url));
    }
    if (latitude != null) {
      values.add(new ConnectionInputValue("latitude", latitude));
    }
    if (longitude != null) {
      values.add(new ConnectionInputValue("longitude", longitude));
    }
    return new DeclaredConnectionInput(values, List.of());
  }
}
