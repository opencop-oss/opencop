// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.runtimefeature;

import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_INVALID_NAME;
import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_RESERVED_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.business.policy.runtimefeature.RuntimeFeatureNamePolicy;
import com.opencop.core.sdk.model.runtimefeature.BackendRuntimeFeature;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class RuntimeFeatureNamePolicyTest {

  private final RuntimeFeatureNamePolicy policy = new RuntimeFeatureNamePolicy();

  @ParameterizedTest
  @ValueSource(strings = {"themes-page", "plugin-loader", "rtsp", "a", "flag.v2", "x_9", "a1-b.c"})
  void acceptsLowercaseSeparatorLimitedNames(String name) {
    assertThatCode(() -> policy.assertRegistrable(name)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "themesPage", // the pre-migration camelCase value
        "Themes-Page",
        "themes page",
        "themes/page",
        "themes:page",
        "thémes",
        "themes*",
        "",
        "   "
      })
  void rejectsNamesOutsideTheWireGrammar(String name) {
    assertRejectedAsInvalid(name);
  }

  /**
   * A name is used as a whole URI path segment in the {@code Location} header and the audience
   * {@code PATCH} URL. Dot segments are collapsed by URI normalization, so accepting one would
   * create a durable row that no request can address; the separator shapes below are excluded by
   * the same rule that excludes them.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        ".",
        "..",
        "...",
        "./.",
        ".themes",
        "themes.",
        "-themes",
        "themes-",
        "_themes",
        "themes__page",
        "themes--page",
        "themes.-page",
        "-",
        "_"
      })
  void rejectsDotSegmentsAndUnanchoredSeparators(String name) {
    assertRejectedAsInvalid(name);
  }

  private void assertRejectedAsInvalid(String name) {
    assertThatThrownBy(() -> policy.assertRegistrable(name))
        .isInstanceOfSatisfying(
            RuntimeFeatureException.class,
            exception -> {
              assertThat(exception.getCode()).isEqualTo(RUNTIME_FEATURE_INVALID_NAME.name());
              assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
  }

  @Test
  void rejectsNullAndOverLongNames() {
    assertThatThrownBy(() -> policy.assertRegistrable(null))
        .isInstanceOfSatisfying(
            RuntimeFeatureException.class,
            exception ->
                assertThat(exception.getCode()).isEqualTo(RUNTIME_FEATURE_INVALID_NAME.name()));

    assertThatCode(() -> policy.assertRegistrable("a".repeat(64))).doesNotThrowAnyException();
    assertThatThrownBy(() -> policy.assertRegistrable("a".repeat(65)))
        .isInstanceOfSatisfying(
            RuntimeFeatureException.class,
            exception ->
                assertThat(exception.getCode()).isEqualTo(RUNTIME_FEATURE_INVALID_NAME.name()));
  }

  // Guards the whole enum rather than the literal "watchbox" so a newly added backend-enforced
  // identity is protected the moment it lands, without anyone remembering to update this test.
  @Test
  void rejectsEveryBackendEnforcedIdentity() {
    assertThat(BackendRuntimeFeature.values()).isNotEmpty();
    Arrays.stream(BackendRuntimeFeature.values())
        .forEach(
            reserved ->
                assertThatThrownBy(() -> policy.assertRegistrable(reserved.wireName()))
                    .isInstanceOfSatisfying(
                        RuntimeFeatureException.class,
                        exception -> {
                          assertThat(exception.getCode())
                              .isEqualTo(RUNTIME_FEATURE_RESERVED_NAME.name());
                          assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        }));
  }
}
