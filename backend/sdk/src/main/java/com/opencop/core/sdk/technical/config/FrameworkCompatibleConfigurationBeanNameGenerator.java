// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.FullyQualifiedConfigurationBeanNameGenerator;
import org.springframework.core.type.MethodMetadata;

/**
 * Fully qualifies application and plugin defaults without renaming Spring framework bean methods.
 * Framework auto-configuration may use its conventional method names as by-name contracts, so those
 * names are retained at definition time instead of restored later through context-wide aliases.
 * Application beans that override name-conditional auto-configuration must still declare that
 * conventional name explicitly.
 */
public final class FrameworkCompatibleConfigurationBeanNameGenerator
    extends FullyQualifiedConfigurationBeanNameGenerator {

  public static final FrameworkCompatibleConfigurationBeanNameGenerator INSTANCE =
      new FrameworkCompatibleConfigurationBeanNameGenerator();

  private static final String SPRING_PACKAGE_PREFIX = "org.springframework.";

  private FrameworkCompatibleConfigurationBeanNameGenerator() {}

  @Override
  public String deriveBeanName(MethodMetadata beanMethod, @Nullable String beanName) {
    if (beanName == null && beanMethod.getDeclaringClassName().startsWith(SPRING_PACKAGE_PREFIX)) {
      return beanMethod.getMethodName();
    }
    return super.deriveBeanName(beanMethod, beanName);
  }
}
