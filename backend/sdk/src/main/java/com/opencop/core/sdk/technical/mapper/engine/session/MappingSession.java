// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.session;

import com.opencop.core.sdk.model.mapper.MappedRecord;
import com.opencop.core.sdk.model.mapper.MappingError;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.model.mapper.MappingWarning;
import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.rules.TargetField;
import com.opencop.core.sdk.model.mapper.source.ConstSource;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.technical.mapper.engine.FeatureBuilder;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import com.opencop.core.sdk.technical.mapper.engine.coercer.TypeCoercer;
import com.opencop.core.sdk.technical.mapper.engine.extractor.PartAwareExtractor;
import com.opencop.core.sdk.technical.mapper.engine.extractor.RecordExtractor;
import com.opencop.core.sdk.technical.mapper.engine.transformer.TransformEngine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.geojson.Feature;

public final class MappingSession<R> {

  private static final String RULE_DISABLED_CODE = "RULE_DISABLED";
  private static final String RULE_DISABLED_MESSAGE =
      "A mapping rule is disabled and was not applied";
  private static final String ON_MISSING_DEFAULT_CODE = "ON_MISSING_DEFAULT";
  private static final String ON_MISSING_DEFAULT_MESSAGE =
      "Missing field, no default value was provided";
  private static final String ON_MISSING_ERROR_CODE = "ON_MISSING_ERROR";
  private static final String EMPTY_REQUIRED_VALUE_MESSAGE =
      "Encountered an empty value on a non optional field";
  private static final String ON_MISSING_FAILURE_MESSAGE = "On missing condition failure";

  private final DynamicMapper profile;
  private final RecordExtractor<R> extractor;
  private final TransformEngine transformEngine;
  private final TypeCoercer typeCoercer = new TypeCoercer();
  private final MappingProperties mappingProperties;

  public MappingSession(
      TransformEngine transformEngine,
      DynamicMapper profile,
      RecordExtractor<R> extractor,
      MappingProperties mappingProperties) {
    this.transformEngine = transformEngine;
    this.profile = profile;
    this.extractor = extractor;
    this.mappingProperties = mappingProperties;
  }

  public MappingResponse execute(byte[] payload) {
    List<MappedRecord> records = new ArrayList<>();
    List<MappingError> errors = new ArrayList<>();
    List<MappingWarning> warnings = new ArrayList<>();

    try {
      List<R> recordNodes = extractor.selectRecords(payload, profile);
      for (int recordIndex = 0; recordIndex < recordNodes.size(); recordIndex++) {
        Optional<MappedRecord> mappedRecord =
            mapSingleRecord(recordIndex, recordNodes.get(recordIndex), errors);
        mappedRecord.ifPresent(records::add);
      }
    } catch (MapperEngineException e) {
      errors.add(
          new MappingError(
              mappingProperties.preview() ? null : payload, e.getCode(), e.getMessage(), null, -1));
    }

    return new MappingResponse(
        profile.id(), profile.formatConfig().format(), records, errors, warnings);
  }

  private Optional<MappedRecord> mapSingleRecord(
      int recordIndex, R recordNode, List<MappingError> errors) {
    RecordContext<R> context =
        new RecordContext<>(
            recordIndex, recordNode, extractor.toBytes(recordNode), new ArrayList<>(), errors);

    try {
      int partCount = resolvePartCount(recordNode);
      for (int partIndex = 0; partIndex < partCount; partIndex++) {
        if (!mapPart(context, mappingProperties, partIndex)) {
          return Optional.empty();
        }
      }

      return Optional.of(new MappedRecord(recordIndex, context.features(), context.warnings));
    } catch (MapperEngineException e) {
      errors.add(
          new MappingError(
              mappingProperties.preview() ? null : context.payload(),
              e.getCode(),
              e.getMessage(),
              e.getRuleIds(),
              e.getRecordIndex() == -1 ? context.recordIndex() : e.getRecordIndex()));
      return Optional.empty();
    }
  }

  private boolean mapPart(RecordContext<R> context, MappingProperties properties, int partIndex)
      throws MapperEngineException {
    Map<String, SessionField> fields = new LinkedHashMap<>();

    for (MappingRule rule : profile.rules()) {
      if (!applyRule(context, partIndex, rule, fields)) {
        return false;
      }
    }

    context
        .features()
        .addAll(
            new FeatureBuilder(fields, properties, context.warnings(), context.recordIndex())
                .build());
    return true;
  }

  private boolean applyRule(
      RecordContext<R> context, int partIndex, MappingRule rule, Map<String, SessionField> fields)
      throws MapperEngineException {
    if (!rule.enabled()) {
      addWarning(context, RULE_DISABLED_CODE, RULE_DISABLED_MESSAGE, rule.id());
      return true;
    }

    String rawValue = resolveRawValue(context, partIndex, rule, fields);
    if (StringUtils.isBlank(rawValue) && !canTransformMissingValue(rule)) {
      return handleMissingValue(context, rule, fields);
    }

    Object typedValue = transformAndCoerce(context, rawValue, rule);
    putField(fields, rule, typedValue);

    return true;
  }

  private String resolveRawValue(
      RecordContext<R> context, int partIndex, MappingRule rule, Map<String, SessionField> fields)
      throws MapperEngineException {
    String value;
    if (OnMissing.DEFAULT.equals(rule.onMissing())) {
      var extractedValue = extractValue(context.recordNode(), partIndex, rule);
      value = extractedValue != null ? extractedValue : rule.defaultValue();
    } else {
      value = extractValue(context.recordNode(), partIndex, rule);
    }

    if (value == null) {
      addWarning(context, ON_MISSING_DEFAULT_CODE, ON_MISSING_DEFAULT_MESSAGE, rule.id());
    }
    putField(fields, rule, value);
    return value;
  }

  private boolean handleMissingValue(
      RecordContext<R> context, MappingRule rule, Map<String, SessionField> fields) {
    if (OnMissing.ERROR.equals(rule.onMissing())) {
      context
          .errors()
          .add(
              new MappingError(
                  mappingProperties.preview() ? null : context.payload(),
                  ON_MISSING_ERROR_CODE,
                  EMPTY_REQUIRED_VALUE_MESSAGE,
                  List.of(rule.id()),
                  context.recordIndex()));
      return false;
    }

    if (OnMissing.NULL.equals(rule.onMissing())) {
      putField(fields, rule, null);
      return true;
    }

    if (OnMissing.DEFAULT.equals(rule.onMissing())) {
      putField(fields, rule, rule.defaultValue());
      return true;
    }

    context
        .errors()
        .add(
            new MappingError(
                mappingProperties.preview() ? null : context.payload(),
                ON_MISSING_ERROR_CODE,
                ON_MISSING_FAILURE_MESSAGE,
                List.of(rule.id()),
                context.recordIndex()));
    return false;
  }

  private Object transformAndCoerce(RecordContext<R> context, String rawValue, MappingRule rule)
      throws MapperEngineException {
    try {
      Object transformedValue = transformEngine.apply(rawValue, rule.transforms());
      return typeCoercer.coerce(transformedValue, rule.outputType());
    } catch (MapperEngineException e) {
      // Re-throw with the rule ID attached so the error can be linked in the UI
      throw new MapperEngineException(
          e.getCode(), e.getMessage(), rule.id(), e.getCause(), context.recordIndex());
    }
  }

  private void putField(Map<String, SessionField> fields, MappingRule rule, Object value) {
    fields.put(sessionFieldKey(rule), new SessionField(rule, value));
  }

  private String sessionFieldKey(MappingRule rule) {
    if (rule.isAttributeEntry()) {
      return TargetField.ATTRIBUTES.path() + "[" + rule.id() + ":" + rule.attributeKey() + "]";
    }
    if (rule.target().field() == TargetField.ATTRIBUTES) {
      return TargetField.ATTRIBUTES.path() + "[" + rule.id() + "]";
    }
    return rule.target().path();
  }

  private boolean canTransformMissingValue(MappingRule rule) {
    return !rule.transforms().isEmpty()
        && transformEngine.acceptsNullInput(rule.transforms().getFirst());
  }

  private int resolvePartCount(R recordNode) {
    if (!(extractor instanceof PartAwareExtractor<?>)) {
      return 1;
    }

    return Math.max(1, partAwareExtractor().partCount(recordNode));
  }

  private String extractValue(R recordNode, int partIndex, MappingRule rule)
      throws MapperEngineException {
    if (rule.source() instanceof ConstSource(String value)) {
      return value;
    }
    if (!(extractor instanceof PartAwareExtractor<?>)) {
      return extractor.selectString(recordNode, rule);
    }

    return partAwareExtractor().selectString(recordNode, partIndex, rule);
  }

  private void addWarning(RecordContext<R> context, String code, String message, UUID ruleId) {
    context
        .warnings()
        .add(new MappingWarning(code, message, List.of(ruleId), context.recordIndex()));
  }

  @SuppressWarnings("unchecked")
  private PartAwareExtractor<R> partAwareExtractor() {
    return (PartAwareExtractor<R>) extractor;
  }

  private record RecordContext<T>(
      int recordIndex,
      T recordNode,
      byte[] payload,
      List<MappingWarning> warnings,
      List<MappingError> errors,
      List<Feature> features) {

    private RecordContext(
        int recordIndex,
        T recordNode,
        byte[] payload,
        List<MappingWarning> warnings,
        List<MappingError> errors) {
      this(recordIndex, recordNode, payload, warnings, errors, new ArrayList<>());
    }
  }
}
