// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionBody;
import com.opencop.core.sdk.business.registry.integration.connection.extension.ConnectionExtensionRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.extension.RegisteredExtension;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionOperation;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Publishes every operation integrations' extensions declare as its own path, although one
 * catch-all endpoint serves them all, with the bodies its declared types describe.
 *
 * <p>OpenAPI allows one operation per path and method. When several declarations answer the same
 * requests, whatever their variables' names, the first in the registry's order is published: within
 * an integration its newest schema version. The operation carries the tag of every integration
 * declaring it, and names every declaring schema version.
 */
public class ConnectionExtensionOpenApiCustomizer implements OpenApiCustomizer {

  private static final String EXTENSION_PATH = "/v1/connections/{sourceId}/extensions/%s";
  private static final String ROOT_TEMPLATE = "/";
  private static final String SOURCE_ID = "sourceId";
  private static final String JSON = "application/json";
  private static final String TAG = "Connection extensions – %s";
  private static final String OPENAPI_31 = "3.1";
  private static final Pattern VARIABLE = Pattern.compile("\\{[^}]+}");
  private static final String ANY_VARIABLE = "{}";
  private static final Pattern NOT_IDENTIFIER = Pattern.compile("[^A-Za-z0-9]+");
  private static final Pattern TRAILING_SEPARATORS = Pattern.compile("_+$");
  private static final String IDENTIFIER_SEPARATOR = "_";
  private static final String OPERATION_ID_PREFIX = "connectionExtension";
  private static final String RESULT_STATUS = "200";
  private static final String NO_CONTENT_STATUS = "204";
  private static final String DECLARED_BY = "Declared by ";
  private static final String ERROR_SCHEMA = GlobalErrorResponse.class.getSimpleName();
  private static final String COMPONENT_REF = "#/components/schemas/";
  private static final String SCHEMA_CLASH =
      "Extension body type %s publishes the OpenAPI schema '%s', which the document already"
          + " describes differently; rename one of the types";

  private static final String FORBIDDEN_STATUS = "403";
  private static final String FORBIDDEN =
      "The operation changes something and the caller does not administer the connection, or the"
          + " request's CSRF token was refused";

  /**
   * The body the security layer answers a refused request with, rather than the host's error body:
   * {@code error} is {@code forbidden}, and {@code code} is set for a refused CSRF token.
   */
  @SuppressWarnings("rawtypes")
  private static final Schema FORBIDDEN_BODY =
      new ObjectSchema()
          .addProperty("error", new StringSchema()._enum(List.of("forbidden")))
          .addProperty("code", new StringSchema())
          .required(List.of("error"));

  /** What the host itself answers with its error body, whichever operation is invoked. */
  private static final Map<String, String> HOST_ERRORS =
      Map.of(
          "400", "The request body is not valid for the operation, or the extension refused it",
          "404",
              "The connection does not exist or the caller cannot see it, it has no such "
                  + "extension or operation, or something the request names does not exist",
          "409",
              "The connection's integration does not run here, its settings no longer resolve, "
                  + "or the request conflicts with the current state",
          "500", "The operation failed unexpectedly",
          "502", "The remote system the extension reached failed",
          "504", "The remote system the extension reached did not answer in time");

  private final ConnectionExtensionRegistry extensions;

  public ConnectionExtensionOpenApiCustomizer(ConnectionExtensionRegistry extensions) {
    this.extensions = extensions;
  }

  @Override
  public void customise(OpenAPI document) {
    var publication = new Publication(document);
    extensions.all().forEach(publication::publish);
    publication.describeDeclarers();
  }

  /** One document's publication: what is published so far, so a later declaration joins it. */
  private final class Publication {

    private final OpenAPI document;
    private final Map<String, String> publishedPaths = new HashMap<>();
    private final Set<String> operationIds = new HashSet<>();
    private final Map<Operation, Map<String, List<Integer>>> declarers = new IdentityHashMap<>();

    private Publication(OpenAPI document) {
      this.document = document;
    }

    private void publish(RegisteredExtension extension) {
      extension.operations().forEach(operation -> publish(extension, operation));
    }

    /** Publishes the operation, or joins the one already answering the same requests. */
    private void publish(RegisteredExtension extension, ExtensionOperation<?, ?, ?> operation) {
      var path =
          publishedPaths.computeIfAbsent(
              shape(extension, operation), ignored -> path(extension, operation));
      var item = pathItem(document, path);
      var method = PathItem.HttpMethod.valueOf(operation.method().name());
      var published = item.readOperationsMap().get(method);
      if (published == null) {
        published = operation(document, path, extension, operation, operationIds);
        item.operation(method, published);
      }
      var integration = extension.schema().integration().name();
      var tag = TAG.formatted(integration);
      if (!published.getTags().contains(tag)) {
        published.addTagsItem(tag);
      }
      declarers
          .computeIfAbsent(published, ignored -> new LinkedHashMap<>())
          .computeIfAbsent(integration, ignored -> new ArrayList<>())
          .add(extension.schema().schemaVersion());
    }

    private void describeDeclarers() {
      declarers.forEach((operation, versions) -> operation.setDescription(declaredBy(versions)));
    }
  }

  private static String path(RegisteredExtension extension, ExtensionOperation<?, ?, ?> operation) {
    var template = operation.path().template();
    return EXTENSION_PATH.formatted(extension.id())
        + (ROOT_TEMPLATE.equals(template) ? "" : template);
  }

  /**
   * Paths differing only in their variables' names answer the same requests, and OpenAPI treats
   * them as one path, so they are published under the first one's names.
   */
  private static String shape(
      RegisteredExtension extension, ExtensionOperation<?, ?, ?> operation) {
    return VARIABLE.matcher(path(extension, operation)).replaceAll(ANY_VARIABLE);
  }

  private static PathItem pathItem(OpenAPI document, String path) {
    if (document.getPaths() == null) {
      document.setPaths(new Paths());
    }
    return document.getPaths().computeIfAbsent(path, ignored -> new PathItem());
  }

  private Operation operation(
      OpenAPI document,
      String path,
      RegisteredExtension extension,
      ExtensionOperation<?, ?, ?> operation,
      Set<String> operationIds) {
    var published =
        new Operation()
            .operationId(uniqueOperationId(operationId(extension, operation), operationIds))
            .summary(
                extension.descriptor().label()
                    + ": "
                    + operation.method()
                    + " "
                    + operation.path().template())
            .tags(new ArrayList<>())
            .parameters(parameters(path))
            .responses(responses(document, operation));
    if (operation.request() instanceof ExtensionBody.Json<?>(var type)) {
      published.requestBody(new RequestBody().required(true).content(json(schema(document, type))));
    }
    return published;
  }

  /**
   * Different paths can read alike once reduced to an identifier, such as a literal and a variable
   * of the same name, so a repeated identifier is numbered.
   */
  private static String uniqueOperationId(String candidate, Set<String> operationIds) {
    var identifier = candidate;
    for (int occurrence = 2; !operationIds.add(identifier); occurrence++) {
      identifier = candidate + IDENTIFIER_SEPARATOR + occurrence;
    }
    return identifier;
  }

  private static String operationId(
      RegisteredExtension extension, ExtensionOperation<?, ?, ?> operation) {
    var identifier =
        OPERATION_ID_PREFIX
            + " "
            + extension.id()
            + " "
            + operation.method().name().toLowerCase(Locale.ROOT)
            + " "
            + operation.path().template();
    return TRAILING_SEPARATORS
        .matcher(NOT_IDENTIFIER.matcher(identifier).replaceAll(IDENTIFIER_SEPARATOR))
        .replaceAll("");
  }

  /**
   * The variables of the path the operation is published under, which carry the names of the first
   * declaration of that path, whatever this operation's own declaration calls them.
   */
  private static List<Parameter> parameters(String path) {
    return VARIABLE
        .matcher(path)
        .results()
        .map(variable -> variableName(variable.group()))
        .<Parameter>map(
            name ->
                new PathParameter()
                    .name(name)
                    .required(true)
                    .schema(SOURCE_ID.equals(name) ? new UUIDSchema() : new StringSchema()))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private static String variableName(String variable) {
    return variable.substring(1, variable.length() - 1);
  }

  private ApiResponses responses(OpenAPI document, ExtensionOperation<?, ?, ?> operation) {
    var responses = new ApiResponses();
    switch (operation.response()) {
      case ExtensionBody.Json<?>(var type) ->
          responses.addApiResponse(
              RESULT_STATUS,
              new ApiResponse()
                  .description("The operation's result")
                  .content(json(schema(document, type))));
      case ExtensionBody.None _ ->
          responses.addApiResponse(
              NO_CONTENT_STATUS, new ApiResponse().description("The operation ran"));
    }
    var errorBody = hostErrorSchema(document);
    HOST_ERRORS.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            error ->
                responses.addApiResponse(
                    error.getKey(),
                    new ApiResponse().description(error.getValue()).content(json(errorBody))));
    responses.addApiResponse(
        FORBIDDEN_STATUS, new ApiResponse().description(FORBIDDEN).content(json(FORBIDDEN_BODY)));
    return responses;
  }

  /**
   * The body the host answers its errors with. Core's own endpoints may already describe it, and
   * then it is referred to as they describe it, rather than described a second time.
   */
  @SuppressWarnings("rawtypes")
  private static Schema hostErrorSchema(OpenAPI document) {
    var components = document.getComponents();
    if (components != null
        && components.getSchemas() != null
        && components.getSchemas().containsKey(ERROR_SCHEMA)) {
      return new Schema<>().$ref(COMPONENT_REF + ERROR_SCHEMA);
    }
    return schema(document, GlobalErrorResponse.class);
  }

  /**
   * The type's schema, a reference when it is an object, with what it refers to registered.
   *
   * @throws IllegalStateException when a schema the type refers to is named like a different one
   *     the document already holds, Core's own or another extension's: registering it would
   *     silently describe the other's operations with it
   */
  @SuppressWarnings("rawtypes")
  private static Schema schema(OpenAPI document, Class<?> type) {
    var resolved =
        ModelConverters.getInstance(isOpenApi31(document))
            .resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(true));
    if (resolved.referencedSchemas != null && !resolved.referencedSchemas.isEmpty()) {
      if (document.getComponents() == null) {
        document.setComponents(new Components());
      }
      resolved.referencedSchemas.forEach(
          (name, schema) -> register(document.getComponents(), type, name, schema));
    }
    return resolved.schema;
  }

  @SuppressWarnings("rawtypes")
  private static void register(Components components, Class<?> type, String name, Schema schema) {
    var existing = components.getSchemas() == null ? null : components.getSchemas().get(name);
    if (existing != null && !existing.equals(schema)) {
      throw new IllegalStateException(SCHEMA_CLASH.formatted(type.getName(), name));
    }
    components.addSchemas(name, schema);
  }

  private static boolean isOpenApi31(OpenAPI document) {
    return document.getOpenapi() != null && document.getOpenapi().startsWith(OPENAPI_31);
  }

  @SuppressWarnings("rawtypes")
  private static Content json(Schema schema) {
    return new Content().addMediaType(JSON, new MediaType().schema(schema));
  }

  private static String declaredBy(Map<String, List<Integer>> versions) {
    return DECLARED_BY
        + versions.entrySet().stream()
            .map(
                entry ->
                    entry.getKey()
                        + " connection schema "
                        + (entry.getValue().size() == 1 ? "version " : "versions ")
                        + entry.getValue().stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(", ")))
            .collect(Collectors.joining("; "))
        + ".";
  }
}
