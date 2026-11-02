// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.factory.connection;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.general.business.model.connection.TcpTimeouts;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpEndpointMaterial;
import com.opencop.core.sdk.business.model.profile.DelimiterModes;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.CustomTcpFraming;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import org.junit.jupiter.api.Test;

class TcpConnectionSpecFactoryTest {

  @Test
  void assemblesTheTcpTransportFromItsDecidedParts() {
    var framing = new CustomTcpFraming(DelimiterModes.DELIMITER_SPLIT, "\n");

    var spec =
        TcpConnectionSpecFactory.create(
            framing,
            new TcpEndpointMaterial("tcp.example.test", 8087),
            new NoSecurity(),
            new TcpTimeouts(1234, 5678));

    assertThat(spec.transport())
        .isEqualTo(
            new TcpConfig("tcp.example.test", 8087, 1234, 5678, true, framing, new NoSecurity()));
  }
}
