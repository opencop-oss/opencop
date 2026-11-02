// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.framing;

import com.opencop.api.connect.ports.gateway.socket.framing.FramingStrategy;
import java.io.IOException;
import java.io.InputStream;

/**
 * Pass-through framing strategy.
 *
 * <p>Reads everything until the end of the stream and returns it as one message.
 */
public class PassThroughFramingStrategy implements FramingStrategy {

  @Override
  public byte[] readMessage(InputStream input) throws IOException {
    byte[] data = input.readAllBytes();
    return data.length > 0 ? data : null;
  }
}
