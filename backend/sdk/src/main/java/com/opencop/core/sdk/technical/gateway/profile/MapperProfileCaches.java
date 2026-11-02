// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.profile;

/**
 * Names of the caches that hold mapper reads. Every service that reads mappers registers these in
 * its cache manager, and {@link MapperProfileRepository} fills them.
 */
public final class MapperProfileCaches {

  /** The latest revision of a mapper, by the mapper's identifier. */
  public static final String BY_MAPPER_ID = "mapperProfileByMapperId";

  /**
   * The latest revision of the ingress mapper a source decodes with, by the source's identifier.
   */
  public static final String BY_SOURCE_ID = "mapperProfileBySourceId";

  /** Whether a source exists and only writes, by the source's identifier. */
  public static final String SOURCE_ONLY_WRITES = "sourceOnlyWritesBySourceId";

  private MapperProfileCaches() {}
}
