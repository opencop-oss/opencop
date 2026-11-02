// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttachmentStreamPathTest {

  private static final UUID ATTACHMENT_ID = UUID.fromString("6d063523-0461-439e-b025-ed3baf3b1d18");

  @Test
  void producedPathNameIsAcceptedByForwardAuthPathParser() {
    var pathName = AttachmentStreamPath.pathName(ATTACHMENT_ID);

    assertEquals("att_" + ATTACHMENT_ID, pathName);
    assertEquals(
        ATTACHMENT_ID,
        AttachmentStreamPath.parseForwardedPath("/streams/" + pathName + "/index.m3u8")
            .orElseThrow());
  }

  @Test
  void dataSourcePathNameIsNotAcceptedByForwardAuthPathParser() {
    assertTrue(
        AttachmentStreamPath.parseForwardedPath("/streams/ds_" + ATTACHMENT_ID + "/index.m3u8")
            .isEmpty());
  }
}
