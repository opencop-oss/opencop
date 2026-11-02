// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.blob.azure;

public final class PublicBlobUrlRewriter {

  private PublicBlobUrlRewriter() {}

  public static String rewrite(String blobUrl, String internalEndpoint, String publicEndpoint) {
    if (blobUrl == null || blobUrl.isBlank()) {
      return blobUrl;
    }
    if (internalEndpoint == null || internalEndpoint.isBlank()) {
      return blobUrl;
    }
    if (publicEndpoint == null || publicEndpoint.isBlank()) {
      return blobUrl;
    }

    var internalPrefix = trimTrailingSlashes(internalEndpoint);
    var publicPrefix = trimTrailingSlashes(publicEndpoint);

    if (internalPrefix.isBlank() || publicPrefix.isBlank()) {
      return blobUrl;
    }
    if (!hasMatchingPrefix(blobUrl, internalPrefix)) {
      return blobUrl;
    }
    return publicPrefix + blobUrl.substring(internalPrefix.length());
  }

  private static boolean hasMatchingPrefix(String blobUrl, String internalPrefix) {
    if (!blobUrl.startsWith(internalPrefix)) {
      return false;
    }
    if (blobUrl.length() == internalPrefix.length()) {
      return true;
    }
    var nextChar = blobUrl.charAt(internalPrefix.length());
    return nextChar == '/' || nextChar == '?' || nextChar == '#';
  }

  private static String trimTrailingSlashes(String endpoint) {
    var normalized = endpoint;
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
