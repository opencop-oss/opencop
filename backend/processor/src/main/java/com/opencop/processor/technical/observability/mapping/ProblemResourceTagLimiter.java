// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.observability.mapping;

import com.opencop.processor.business.model.mapping.MappingResourceKind;
import com.opencop.processor.business.model.mapping.MappingResourceRef;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class ProblemResourceTagLimiter {

  public static final String OVERFLOW_TAG = "other";

  private final Object lock = new Object();
  private final int maximumResources;
  private final Function<UUID, String> idFormatter;
  private final Set<MappingResourceRef> admitted = new HashSet<>();

  public ProblemResourceTagLimiter(int maximumResources) {
    this(maximumResources, UUID::toString);
  }

  ProblemResourceTagLimiter(int maximumResources, Function<UUID, String> idFormatter) {
    if (maximumResources <= 0 || maximumResources > 100) {
      throw new IllegalArgumentException("maximumResources must be between 1 and 100");
    }
    this.maximumResources = maximumResources;
    this.idFormatter = Objects.requireNonNull(idFormatter, "idFormatter");
  }

  public AdmittedResourceTag admit(MappingResourceRef resource) {
    Objects.requireNonNull(resource, "resource");
    synchronized (lock) {
      if (admitted.contains(resource)) {
        return admitted(resource);
      }
      if (admitted.size() < maximumResources) {
        admitted.add(resource);
        return admitted(resource);
      }
      return new AdmittedResourceTag(resource.kind(), OVERFLOW_TAG, true);
    }
  }

  int admittedCount() {
    synchronized (lock) {
      return admitted.size();
    }
  }

  private AdmittedResourceTag admitted(MappingResourceRef resource) {
    return new AdmittedResourceTag(resource.kind(), idFormatter.apply(resource.id()), false);
  }

  public record AdmittedResourceTag(
      MappingResourceKind resourceKind, String resourceId, boolean overflow) {

    public AdmittedResourceTag {
      Objects.requireNonNull(resourceKind, "resourceKind");
      Objects.requireNonNull(resourceId, "resourceId");
    }
  }
}
