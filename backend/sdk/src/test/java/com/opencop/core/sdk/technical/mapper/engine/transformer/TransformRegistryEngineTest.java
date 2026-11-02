// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import com.opencop.core.sdk.technical.mapper.engine.transformer.operation.AddDurationHandler;
import com.opencop.core.sdk.technical.mapper.engine.transformer.operation.DefaultNowHandler;
import com.opencop.core.sdk.technical.mapper.engine.transformer.operation.KnotsToMpsHandler;
import com.opencop.core.sdk.technical.mapper.engine.transformer.operation.LowerHandler;
import com.opencop.core.sdk.technical.mapper.engine.transformer.operation.ParseDateTimeHandler;
import com.opencop.core.sdk.technical.mapper.engine.transformer.operation.RadToDegreesHandler;
import com.opencop.core.sdk.technical.mapper.engine.transformer.operation.RegexHandler;
import com.opencop.core.sdk.technical.mapper.engine.transformer.operation.TransformHandler;
import com.opencop.core.sdk.technical.mapper.engine.transformer.operation.TrimHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransformRegistryEngineTest {

  @Test
  void applies_steps_in_order() throws Exception {
    TransformEngine engine =
        new TransformRegistryEngine(
            List.of(new TrimHandler(), new LowerHandler(), new RegexHandler()));

    List<TransformStep> steps =
        List.of(
            TransformStepBuilder.trim(),
            TransformStepBuilder.lower(),
            TransformStepBuilder.replaceRegex("\\s+", "_"));

    Object out = engine.apply("  HeLLo  WoRLD  ", steps);

    assertEquals("hello_world", out);
  }

  @Test
  void returns_input_when_steps_empty() throws Exception {
    TransformEngine engine =
        new TransformRegistryEngine(List.of(new TrimHandler(), new LowerHandler()));

    Object out = engine.apply("X", List.of());

    assertEquals("X", out);
  }

  @Test
  void applies_timestamp_pipeline_in_order() throws Exception {
    TransformEngine engine =
        new TransformRegistryEngine(List.of(new ParseDateTimeHandler(), new AddDurationHandler()));

    Object out =
        engine.apply(
            "2024-01-15T10:30:45Z",
            List.of(
                TransformStepBuilder.parseDateTime(), TransformStepBuilder.addDuration("PT15M")));

    assertEquals(Instant.parse("2024-01-15T10:45:45Z"), out);
  }

  @Test
  void applies_unit_conversion_pipeline_in_order() throws Exception {
    TransformEngine engine =
        new TransformRegistryEngine(List.of(new RadToDegreesHandler(), new KnotsToMpsHandler()));

    Object heading = engine.apply(Math.PI, List.of(TransformStepBuilder.radToDegrees()));
    Object speed = engine.apply(10d, List.of(TransformStepBuilder.knotsToMps()));

    assertEquals(180d, heading);
    assertEquals(5.14444d, (Double) speed, 1e-9);
  }

  @Test
  void applies_default_now_before_duration_when_input_is_null() throws Exception {
    TransformEngine engine =
        new TransformRegistryEngine(List.of(new DefaultNowHandler(), new AddDurationHandler()));

    Instant before = Instant.now();
    Object out =
        engine.apply(
            null,
            List.of(TransformStepBuilder.defaultNow(), TransformStepBuilder.addDuration("PT15M")));
    Instant after = Instant.now();

    assertInstanceOf(Instant.class, out);
    assertFalse(((Instant) out).isBefore(before.plusSeconds(15 * 60)));
    assertFalse(((Instant) out).isAfter(after.plusSeconds(15 * 60)));
  }

  @Test
  void reports_whether_a_step_accepts_null_input() {
    TransformRegistryEngine engine =
        new TransformRegistryEngine(List.of(new DefaultNowHandler(), new AddDurationHandler()));

    assertTrue(engine.acceptsNullInput(TransformStepBuilder.defaultNow()));
    assertFalse(engine.acceptsNullInput(TransformStepBuilder.addDuration("PT15M")));
  }

  @Test
  void throws_when_no_handler_for_operation() {
    TransformEngine engine = new TransformRegistryEngine(List.of(new TrimHandler()));

    List<TransformStep> steps = List.of(TransformStepBuilder.lower());

    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> engine.apply("Hello", steps));

    assertEquals("TRANSFORM_ENGINE_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("No handler for"));
  }

  @Test
  void throws_when_current_becomes_null_mid_pipeline() {
    TransformHandler nullHandler =
        new TransformHandler() {
          @Override
          public TransformOperation operation() {
            return TransformOperation.TRIM;
          }

          @Override
          public Object apply(Object in, TransformStep step) {
            return null;
          }
        };

    TransformEngine engine = new TransformRegistryEngine(List.of(nullHandler, new LowerHandler()));

    List<TransformStep> steps =
        List.of(
            TransformStepBuilder.trim(), // returns null
            TransformStepBuilder.lower() // engine should throw before applying
            );

    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> engine.apply("Hello", steps));

    assertEquals("TRANSFORM_ENGINE_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("Encountered a null value"));
  }

  @Test
  void throws_when_input_is_null_and_steps_not_empty() {
    TransformEngine engine = new TransformRegistryEngine(List.of(new TrimHandler()));

    MapperEngineException ex =
        assertThrows(
            MapperEngineException.class,
            () -> engine.apply(null, List.of(TransformStepBuilder.trim())));

    assertEquals("TRANSFORM_ENGINE_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("Encountered a null value"));
  }
}
