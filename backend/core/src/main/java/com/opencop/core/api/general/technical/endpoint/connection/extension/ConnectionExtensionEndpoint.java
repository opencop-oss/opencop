// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.extension;

import com.opencop.core.api.general.ports.usecase.connection.extension.InvokeConnectionExtension;
import com.opencop.core.api.general.ports.usecase.connection.extension.InvokeConnectionExtensionCommand;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionMethod;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Serves every operation integrations' extensions declare, under {@code
 * /v1/connections/{sourceId}/extensions/{extensionId}}. Which operation answers, and whether the
 * caller may invoke it, is decided by the use case; this only adapts HTTP.
 */
@RestController
@Hidden
public class ConnectionExtensionEndpoint {

  private static final String SEPARATOR = "/";
  private static final String EXTENSION_PATH =
      "/v1/connections/{sourceId}/extensions/{extensionId}";

  /** Segments of the extension's own path, the leading empty one included. */
  private static final int EXTENSION_PATH_SEGMENTS = EXTENSION_PATH.split(SEPARATOR).length;

  private final InvokeConnectionExtension usecase;
  private final ObjectMapper mapper;

  ConnectionExtensionEndpoint(InvokeConnectionExtension usecase, ObjectMapper mapper) {
    this.usecase = usecase;
    this.mapper = mapper;
  }

  @RequestMapping(
      path = EXTENSION_PATH + "/**",
      method = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.PATCH,
        RequestMethod.DELETE
      })
  public ResponseEntity<Object> invoke(
      @AuthenticationPrincipal OpenCopPrincipal principal,
      @PathVariable UUID sourceId,
      @PathVariable String extensionId,
      HttpServletRequest request) {
    var method = extensionMethod(request.getMethod());
    if (method.isEmpty()) {
      return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }
    var result =
        usecase.invoke(
            new InvokeConnectionExtensionCommand(
                sourceId,
                principal.internalUserId(),
                principal.isGlobalAdmin(),
                extensionId,
                method.get(),
                operationPath(request),
                query(request),
                new JsonRequestBody(request, mapper)));
    if (!result.hasBody()) {
      return ResponseEntity.noContent().build();
    }
    // Serialized here rather than by Spring, which writes a string body as plain text whatever
    // the content type: every result, a string included, reaches the client as JSON.
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(mapper.writeValueAsBytes(result.body()));
  }

  /**
   * The method as extensions declare it, or empty for one no operation can answer: Spring also
   * routes HEAD here, since the mapping declares GET.
   */
  private static Optional<ExtensionMethod> extensionMethod(String method) {
    return Arrays.stream(ExtensionMethod.values())
        .filter(declarable -> declarable.name().equals(method))
        .findFirst();
  }

  /**
   * The request path below the extension, as decoded segments. Each segment is decoded on its own,
   * so an encoded slash stays inside its segment instead of splitting it.
   */
  private static List<String> operationPath(HttpServletRequest request) {
    var path = request.getRequestURI().substring(request.getContextPath().length());
    var segments = path.split(SEPARATOR, -1);
    return Arrays.stream(segments, EXTENSION_PATH_SEGMENTS, segments.length)
        .map(ConnectionExtensionEndpoint::decode)
        .toList();
  }

  /**
   * The query string's parameters, decoded as form data, like the request parameters of every other
   * endpoint: a plus is a space, and {@code %2B} a plus. Only the query string is read: a form body
   * is not a query, and the operation's body is read on its own.
   */
  private static Map<String, List<String>> query(HttpServletRequest request) {
    var parameters =
        UriComponentsBuilder.newInstance().query(request.getQueryString()).build().getQueryParams();
    var decoded = new LinkedHashMap<String, List<String>>();
    parameters.forEach(
        (name, values) ->
            decoded.put(
                decodeQuery(name),
                values.stream().map(value -> value == null ? "" : decodeQuery(value)).toList()));
    return decoded;
  }

  private static String decodeQuery(String component) {
    return URLDecoder.decode(component, StandardCharsets.UTF_8);
  }

  private static String decode(String component) {
    return UriUtils.decode(component, StandardCharsets.UTF_8);
  }
}
