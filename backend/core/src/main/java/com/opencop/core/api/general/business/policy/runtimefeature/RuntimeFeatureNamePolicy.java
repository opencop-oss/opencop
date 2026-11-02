// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.runtimefeature;

import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_INVALID_NAME;
import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_RESERVED_NAME;

import com.opencop.core.api.general.business.model.runtimefeature.RuntimeFeatureRejectionReason;
import com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureException;
import com.opencop.core.sdk.model.runtimefeature.BackendRuntimeFeature;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

/**
 * Decides which names a runtime registration request may claim.
 *
 * <p>Both rejections are deterministic and depend only on the submitted string, so a caller gets
 * the same answer regardless of catalog state. Reserved names are checked against {@link
 * BackendRuntimeFeature}, the closed set of identities the typed backend-enforcement path can
 * resolve; letting registration claim one would create a {@code FRONTEND_ONLY} row under a name the
 * enforcement path expects to be {@code BACKEND_ENFORCED}, which fails as a per-request 500 on
 * every gated call rather than anything visible at boot.
 */
public class RuntimeFeatureNamePolicy {

  /**
   * Wire names are lowercase, separator-limited, and safe to use as a whole URI path segment.
   *
   * <p>A registered name becomes a complete path segment in the registration {@code Location}
   * header and in the audience {@code PATCH} URL, so it must survive URI normalization. Requiring
   * an alphanumeric run on both sides of every separator excludes the dot segments {@code .} and
   * {@code ..} — which normalization collapses, leaving a durable row with no address that reaches
   * it — along with leading, trailing, and repeated separators. Names are also broadcast to every
   * authenticated caller through app metadata, so the grammar keeps them terse enough to read in a
   * URL and a log line.
   */
  private static final Pattern VALID_NAME = Pattern.compile("^[a-z0-9]+(?:[._-][a-z0-9]+)*$");

  private static final int MAX_NAME_LENGTH = 64;

  private static final Set<String> RESERVED_NAMES =
      Arrays.stream(BackendRuntimeFeature.values())
          .map(BackendRuntimeFeature::wireName)
          .collect(Collectors.toUnmodifiableSet());

  /**
   * @throws RuntimeFeatureException when the name is malformed or reserved for backend enforcement
   */
  public void assertRegistrable(String name) {
    checkRegistrable(name)
        .ifPresent(
            reason -> {
              throw new RuntimeFeatureException(reason.code(), reason.message(), reason.status());
            });
  }

  /**
   * The reason this name may not be registered, or empty when it may.
   *
   * <p>The non-throwing form exists for batch reconciliation, which reports a refusal inside a 200
   * rather than raising it — see {@link RuntimeFeatureRejectionReason}.
   */
  public Optional<RuntimeFeatureRejectionReason> checkRegistrable(String name) {
    if (name == null || name.isBlank()) {
      return Optional.of(invalidName("Runtime feature name is required"));
    }
    if (name.length() > MAX_NAME_LENGTH) {
      return Optional.of(
          invalidName("Runtime feature name must be at most " + MAX_NAME_LENGTH + " characters"));
    }
    if (!VALID_NAME.matcher(name).matches()) {
      return Optional.of(
          invalidName("Runtime feature name must match " + VALID_NAME.pattern() + ": " + name));
    }
    if (RESERVED_NAMES.contains(name)) {
      return Optional.of(
          new RuntimeFeatureRejectionReason(
              RUNTIME_FEATURE_RESERVED_NAME,
              "Runtime feature name is reserved for backend enforcement: " + name,
              HttpStatus.BAD_REQUEST));
    }
    return Optional.empty();
  }

  private static RuntimeFeatureRejectionReason invalidName(String message) {
    return new RuntimeFeatureRejectionReason(
        RUNTIME_FEATURE_INVALID_NAME, message, HttpStatus.BAD_REQUEST);
  }
}
