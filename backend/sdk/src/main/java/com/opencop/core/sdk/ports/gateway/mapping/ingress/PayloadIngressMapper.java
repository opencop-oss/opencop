// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.mapping.ingress;

import com.opencop.core.sdk.technical.mapper.MappingProperties;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.geojson.Feature;

public interface PayloadIngressMapper<O extends Feature> {

  UUID id();

  String name();

  String description();

  int version();

  List<O> map(UUID id, MappingProperties properties, byte[] input) throws Exception;

  /**
   * Reports whether this Java ingress mapper guarantees stable OpenCOP object IDs for the same
   * real-world entity across repeated events from the same source.
   *
   * <p>This capability applies only to Java-backed ingress mapper profiles where {@code
   * MapperProfile.dynamicMapper()} is null. JSON-rule-backed mapper profiles are evaluated from
   * their mapping rules instead of this ingress mapper trait.
   */
  default ObjectIdStability objectIdStability() {
    return ObjectIdStability.UNKNOWN;
  }

  /**
   * Default wrapper for ingress mappers that only return features.
   *
   * <p>Ingress mappers backed by richer mapping execution may override this to preserve structured
   * records, errors, and warnings in the returned {@link DecodedMappingResult}.
   */
  default DecodedMappingResult mapResult(UUID id, MappingProperties properties, byte[] input)
      throws Exception {
    var features = Objects.requireNonNull(map(id, properties, input), "ingress mapper features");
    return DecodedMappingResult.fromFeatures(this.id(), this.version(), this.name(), features);
  }
}
