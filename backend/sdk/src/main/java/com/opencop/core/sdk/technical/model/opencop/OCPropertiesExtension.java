// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.opencop;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface OCPropertiesExtension {

  String getVersion();

  String getObjectId();

  String getKind();

  UUID getSource();

  String getDisplayName();

  List<Contact> getContacts();

  Timestampz getTimestampz();

  Map<String, String> getAttributes();

  List<EventExtension> getExtensions();
  // String toJson();
  // OCPropertiesExtension fromJson(String json);

}
