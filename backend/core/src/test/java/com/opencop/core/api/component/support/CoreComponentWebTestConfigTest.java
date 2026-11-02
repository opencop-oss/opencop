// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.common.CoreSecurityExceptionAdvice;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

class CoreComponentWebTestConfigTest {

  @Test
  void importsCoreSecurityAdvice() {
    var importAnnotation = CoreComponentWebTestConfig.class.getAnnotation(Import.class);

    assertThat(importAnnotation).isNotNull();
    assertThat(Arrays.asList(importAnnotation.value())).contains(CoreSecurityExceptionAdvice.class);
  }
}
