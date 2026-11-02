// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.scim;

import com.opencop.core.api.common.jackson.PathScopedHttpMessageConverter;
import java.util.List;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class ScimJsonConfig implements WebMvcConfigurer {

  @Override
  public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    HttpMessageConverter<Object> scoped =
        new PathScopedHttpMessageConverter(
            new ScimHttpMessageConverter(),
            req -> {
              String servletPath = req.getServletPath();
              return servletPath != null && servletPath.startsWith("/scim/");
            });

    // Put it at the front so it wins for /scim/** only.
    converters.addFirst(scoped);
  }
}
