// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.extractor;

import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import java.util.List;

public interface RecordExtractor<R> {

  byte[] toBytes(R recordEntry);

  List<R> selectRecords(byte[] payload, DynamicMapper profile) throws MapperEngineException;

  String selectString(R recordEntry, MappingRule rule) throws MapperEngineException;
}
