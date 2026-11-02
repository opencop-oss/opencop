// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.extractor.rtsp;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.TextField;
import com.opencop.core.sdk.business.model.integration.connection.material.rtsp.RtspConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.rtsp.RtspEndpointMaterial;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.policy.rtsp.RtspCoordinatesPolicy;
import com.opencop.core.sdk.integration.builtin.business.policy.rtsp.RtspUrlPolicy;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.RtspConnectionExtractorCapability;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Native RTSP extractor for a stream at the URL the request declares, from a placed camera. */
public final class NativeRtspStreamExtractor implements RtspConnectionExtractorCapability {

  public static final UUID EXTRACTOR_ID = UUID.fromString("9a4c2e71-5b3d-4f86-a1c0-7e2d9b5f3a48");

  private final NativeConnectionSchema schema;

  public NativeRtspStreamExtractor(NativeConnectionSchema schema) {
    this.schema = Objects.requireNonNull(schema, "schema");
  }

  @Override
  public IntegrationConnectionSchema schema() {
    return schema;
  }

  @Override
  public UUID id() {
    return EXTRACTOR_ID;
  }

  /**
   * Builds the declaration on call rather than holding it in a constant, so a malformed field
   * reaches the registry and is rejected with the integration and extractor named.
   */
  @Override
  public ExtractorDescriptor descriptor() {
    return new ExtractorDescriptor(
        EXTRACTOR_ID,
        "RTSP stream",
        List.of(
            new TextField(RtspUrlPolicy.URL_FIELD, "Stream URL", true),
            new TextField(RtspCoordinatesPolicy.LATITUDE_FIELD, "Camera latitude", false),
            new TextField(RtspCoordinatesPolicy.LONGITUDE_FIELD, "Camera longitude", false)));
  }

  @Override
  public void validate(ConnectionInput input) throws ConnectionInputException {
    extract(input);
  }

  @Override
  public RtspConnectionMaterial extract(ConnectionInput input) throws ConnectionInputException {
    var url = input.value(RtspUrlPolicy.URL_FIELD).orElse(null);
    RtspUrlPolicy.requireValid(url);
    var position =
        RtspCoordinatesPolicy.cameraPosition(
            input.value(RtspCoordinatesPolicy.LATITUDE_FIELD).orElse(null),
            input.value(RtspCoordinatesPolicy.LONGITUDE_FIELD).orElse(null));
    return RtspConnectionMaterial.plain(new RtspEndpointMaterial(url, position.orElse(null)));
  }
}
