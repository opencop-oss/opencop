// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import java.io.InputStream;
import java.io.OutputStream;

public interface Session extends AutoCloseable {

  InputStream input();

  OutputStream output();

  @Override
  void close();
}
