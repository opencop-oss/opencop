// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.blob.azure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PublicBlobUrlRewriterTest {

  private static final String INTERNAL_ENDPOINT = "http://azurite:10000/devstoreaccount1";
  private static final String PUBLIC_ENDPOINT = "http://localhost:10000/devstoreaccount1";
  private static final String BLOB_URL =
      INTERNAL_ENDPOINT + "/opencop-media/media/2026/03/23/file.png";

  @Test
  void rewrite_publicEndpointMissing_returnsOriginalUrl() {
    assertEquals(BLOB_URL, PublicBlobUrlRewriter.rewrite(BLOB_URL, INTERNAL_ENDPOINT, null));
    assertEquals(BLOB_URL, PublicBlobUrlRewriter.rewrite(BLOB_URL, INTERNAL_ENDPOINT, "  "));
  }

  @Test
  void rewrite_matchingPrefix_rewritesToPublicEndpoint() {
    var rewritten = PublicBlobUrlRewriter.rewrite(BLOB_URL, INTERNAL_ENDPOINT, PUBLIC_ENDPOINT);
    assertEquals(PUBLIC_ENDPOINT + "/opencop-media/media/2026/03/23/file.png", rewritten);
  }

  @Test
  void rewrite_trailingSlashDifferences_normalizesEndpoints() {
    var rewritten =
        PublicBlobUrlRewriter.rewrite(BLOB_URL, INTERNAL_ENDPOINT + "/", PUBLIC_ENDPOINT + "/");
    assertEquals(PUBLIC_ENDPOINT + "/opencop-media/media/2026/03/23/file.png", rewritten);
  }

  @Test
  void rewrite_internalPrefixMismatch_returnsOriginalUrl() {
    var blobUrl = "http://azurite:10000/devstoreaccount12/opencop-media/media/2026/03/23/file.png";
    var rewritten = PublicBlobUrlRewriter.rewrite(blobUrl, INTERNAL_ENDPOINT, PUBLIC_ENDPOINT);
    assertEquals(blobUrl, rewritten);
  }

  @Test
  void rewrite_preservesRemainingPathAndQuery() {
    var blobUrl =
        INTERNAL_ENDPOINT
            + "/opencop-media/media/2026/03/23/folder/file.png?sig=abc123&se=2026-03-23T10%3A00%3A00Z";
    var rewritten = PublicBlobUrlRewriter.rewrite(blobUrl, INTERNAL_ENDPOINT, PUBLIC_ENDPOINT);
    assertEquals(
        PUBLIC_ENDPOINT
            + "/opencop-media/media/2026/03/23/folder/file.png?sig=abc123&se=2026-03-23T10%3A00%3A00Z",
        rewritten);
  }
}
