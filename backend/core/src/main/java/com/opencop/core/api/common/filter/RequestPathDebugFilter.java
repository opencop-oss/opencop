// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(
    prefix = "opencop.scim",
    name = "inbound-logging-enabled",
    havingValue = "true")
public class RequestPathDebugFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger("REQ_PATHS");

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {

    if (req.getRequestURI().contains("scim")) {
      log.info(
          "URI={} contextPath={} servletPath={} pathInfo={}",
          req.getRequestURI(),
          req.getContextPath(),
          req.getServletPath(),
          req.getPathInfo());
    }
    chain.doFilter(req, res);
  }
}
