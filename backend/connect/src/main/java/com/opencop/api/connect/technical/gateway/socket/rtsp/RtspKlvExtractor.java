// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.rtsp;

public interface RtspKlvExtractor extends AutoCloseable {

  void start() throws RtspKlvExtractorException;

  byte[] readNextPacket(long deadlineNanos) throws RtspKlvExtractorException;

  @Override
  void close();
}
