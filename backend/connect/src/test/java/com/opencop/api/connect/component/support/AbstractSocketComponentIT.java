// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.component.support;

import com.opencop.api.connect.component.support.fixture.SocketFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/** Base for socket-facing component tests that use real localhost peers. */
public abstract class AbstractSocketComponentIT extends ConnectComponentDatabaseSupport {

  protected final SocketFixtures fixture = new SocketFixtures();

  @BeforeEach
  void resetComponentDatabase() {
    resetSources();
  }

  @AfterEach
  void resetSocketFixtures() {
    fixture.reset();
  }
}
