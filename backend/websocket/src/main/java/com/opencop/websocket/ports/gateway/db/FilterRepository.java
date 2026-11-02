// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.gateway.db;

import java.util.List;

public interface FilterRepository<I> {

  String persist(I input);

  void delete(String id);

  I retrievePluginInfo(String id);

  List<I> retrieveAllPluginInfo();
}
