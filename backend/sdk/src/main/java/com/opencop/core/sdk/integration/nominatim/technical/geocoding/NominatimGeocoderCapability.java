// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.nominatim.technical.geocoding;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.geocoding.BoundingBox;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodedPlace;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocoderDescriptor;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingAnswer;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingSearchQuery;
import com.opencop.core.sdk.integration.nominatim.business.policy.NominatimViewboxPolicy;
import com.opencop.core.sdk.integration.nominatim.technical.config.NominatimGeocoderProperties;
import com.opencop.core.sdk.ports.integration.geocoding.ForwardGeocoding;
import com.opencop.core.sdk.ports.integration.geocoding.GeocodingException;
import com.opencop.core.sdk.ports.integration.geocoding.GeocodingFailure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Forward geocoding against a Nominatim instance the deploying organization hosts.
 *
 * <p>Logs only the outcome and duration of a call, never the request URL: it carries the user's
 * search text.
 */
public class NominatimGeocoderCapability implements ForwardGeocoding {

  public static final UUID GEOCODER_ID = UUID.fromString("680892bb-c3a8-4484-8b93-7d3deea339c0");

  private static final Logger log = LoggerFactory.getLogger(NominatimGeocoderCapability.class);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final GeocoderDescriptor DESCRIPTOR =
      new GeocoderDescriptor(
          GEOCODER_ID, "Nominatim", "Places from OpenStreetMap, searched through Nominatim.");
  private static final String USER_AGENT = "OpenCOP";
  private static final String SEARCH_PATH = "/search";
  private static final int HTTP_OK = 200;
  private static final int HTTP_UNAUTHORIZED = 401;
  private static final int HTTP_FORBIDDEN = 403;
  private static final int HTTP_NOT_FOUND = 404;
  private static final int HTTP_TOO_MANY_REQUESTS = 429;
  private static final int HTTP_SERVER_ERROR = 500;

  /**
   * The most response bytes collected before giving up. A full answer of the largest allowed limit,
   * with every alternative name of a major city, stays in the hundreds of kilobytes.
   */
  static final long MAX_RESPONSE_BYTES = 1024 * 1024;

  private static final String USER_AGENT_HEADER = "User-Agent";
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BASIC_SCHEME = "Basic ";

  private static final String QUERY_PARAMETER = "q";
  private static final String FORMAT_PARAMETER = "format";
  private static final String LIMIT_PARAMETER = "limit";
  private static final String LANGUAGE_PARAMETER = "accept-language";
  private static final String DEDUPE_PARAMETER = "dedupe";
  private static final String ADDRESS_DETAILS_PARAMETER = "addressdetails";
  private static final String NAME_DETAILS_PARAMETER = "namedetails";
  private static final String VIEWBOX_PARAMETER = "viewbox";
  private static final String BOUNDED_PARAMETER = "bounded";

  private static final String VIEWBOX_SEPARATOR = ",";
  private static final int COORDINATE_SCALE = 7;

  private static final String JSON_V2_FORMAT = "jsonv2";
  private static final String ENABLED = "1";
  private static final String DISABLED = "0";

  private final Integration integration;
  private final NominatimGeocoderProperties properties;
  private final HttpClient http;

  public NominatimGeocoderCapability(
      Integration integration, NominatimGeocoderProperties properties) {
    this.integration = integration;
    this.properties = properties;
    this.http = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
  }

  @Override
  public Integration integration() {
    return integration;
  }

  @Override
  public UUID id() {
    return GEOCODER_ID;
  }

  @Override
  public GeocoderDescriptor descriptor() {
    return DESCRIPTOR;
  }

  /**
   * Sends one request, without retrying: an interactive caller retries with its next keystroke.
   *
   * <p>No failure keeps its cause. HTTP client and JSON parser messages can carry the instance's
   * address or fragments of the response body, so only the failure family leaves this method. Each
   * failure is logged here with what an operator needs to tell causes apart and nothing more: the
   * HTTP status or the exception's class name, and how long the call took.
   */
  @Override
  public GeocodingAnswer search(GeocodingSearchQuery query) throws GeocodingException {
    var response = send(searchRequest(query));
    if (response.statusCode() != HTTP_OK) {
      throw failure(failureOf(response.statusCode()), "Nominatim answered an error status");
    }
    return new GeocodingAnswer(places(response.body()));
  }

  /**
   * The request's own timeout stops at the response headers, so a body that stalls after them would
   * hold the search indefinitely. The whole exchange is therefore bounded by the connect and read
   * timeouts together, and cancelled once that deadline passes. A body that arrives quickly but
   * exceeds {@link #MAX_RESPONSE_BYTES} fails the exchange instead of being collected whole.
   */
  private HttpResponse<byte[]> send(HttpRequest request) throws GeocodingException {
    var started = System.nanoTime();
    var exchange =
        http.sendAsync(
            request,
            HttpResponse.BodyHandlers.limiting(
                HttpResponse.BodyHandlers.ofByteArray(), MAX_RESPONSE_BYTES));
    try {
      var response = exchange.get(deadline().toMillis(), TimeUnit.MILLISECONDS);
      if (response.statusCode() == HTTP_OK) {
        log.debug("Nominatim search returned {} in {} ms", HTTP_OK, elapsedMillis(started));
      } else {
        log.warn(
            "Nominatim search answered HTTP {} after {} ms",
            response.statusCode(),
            elapsedMillis(started));
      }
      return response;
    } catch (TimeoutException exception) {
      exchange.cancel(true);
      throw timedOut(started, exception);
    } catch (ExecutionException exception) {
      throw failed(started, exception.getCause());
    } catch (InterruptedException _) {
      exchange.cancel(true);
      Thread.currentThread().interrupt();
      throw failure(GeocodingFailure.UNAVAILABLE, "Nominatim search interrupted");
    }
  }

  private Duration deadline() {
    return properties.getConnectTimeout().plus(properties.getReadTimeout());
  }

  private static GeocodingException failed(long started, Throwable cause) {
    if (cause instanceof HttpTimeoutException) {
      return timedOut(started, cause);
    }
    log.warn(
        "Nominatim search failed after {} ms: {}",
        elapsedMillis(started),
        cause.getClass().getSimpleName());
    return failure(GeocodingFailure.UNAVAILABLE, "Nominatim search failed");
  }

  private static GeocodingException timedOut(long started, Throwable cause) {
    log.warn(
        "Nominatim search timed out after {} ms: {}",
        elapsedMillis(started),
        cause.getClass().getSimpleName());
    return failure(GeocodingFailure.TIMEOUT, "Nominatim search timed out");
  }

  private static List<GeocodedPlace> places(byte[] body) throws GeocodingException {
    NominatimPlace[] places;
    try {
      places = JSON.readValue(body, NominatimPlace[].class);
    } catch (JacksonException _) {
      log.warn("Nominatim returned an unreadable body");
      throw failure(GeocodingFailure.BAD_RESPONSE, "Nominatim returned an unreadable body");
    }
    if (!isResultList(places)) {
      log.warn("Nominatim returned no list of results");
      throw failure(GeocodingFailure.BAD_RESPONSE, "Nominatim returned no list of results");
    }
    return Arrays.stream(places).map(NominatimPlaceFactory::create).toList();
  }

  /** Whether the body is a JSON array of objects; {@code null} and {@code [null]} are not. */
  private static boolean isResultList(NominatimPlace[] places) {
    return places != null && !Arrays.asList(places).contains(null);
  }

  /**
   * Classifies a non-success status. Any status not named here, including another client error,
   * means the instance did not understand a request it should have.
   */
  private static GeocodingFailure failureOf(int status) {
    if (isDeploymentFault(status)) {
      return GeocodingFailure.MISCONFIGURED;
    }
    if (status == HTTP_TOO_MANY_REQUESTS) {
      return GeocodingFailure.RATE_LIMITED;
    }
    if (status >= HTTP_SERVER_ERROR) {
      return GeocodingFailure.UNAVAILABLE;
    }
    return GeocodingFailure.BAD_RESPONSE;
  }

  /**
   * Refused credentials, or no search at the configured base URL, is a deployment fault that fails
   * every search, not a passing outage.
   */
  private static boolean isDeploymentFault(int status) {
    return status == HTTP_UNAUTHORIZED || status == HTTP_FORBIDDEN || status == HTTP_NOT_FOUND;
  }

  private HttpRequest searchRequest(GeocodingSearchQuery query) {
    var parameters = new LinkedHashMap<String, String>();
    parameters.put(QUERY_PARAMETER, query.text());
    parameters.put(FORMAT_PARAMETER, JSON_V2_FORMAT);
    parameters.put(LIMIT_PARAMETER, Integer.toString(query.limit()));
    parameters.put(LANGUAGE_PARAMETER, query.language().toLanguageTag());
    parameters.put(DEDUPE_PARAMETER, ENABLED);
    parameters.put(ADDRESS_DETAILS_PARAMETER, ENABLED);
    parameters.put(NAME_DETAILS_PARAMETER, DISABLED);
    query
        .focus()
        .ifPresent(
            focus -> {
              parameters.put(
                  VIEWBOX_PARAMETER,
                  viewbox(
                      NominatimViewboxPolicy.viewbox(focus, properties.getFocusHalfSizeDegrees())));
              parameters.put(BOUNDED_PARAMETER, DISABLED);
            });

    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create(searchUrl() + "?" + encode(parameters)))
            .timeout(properties.getReadTimeout())
            .header(USER_AGENT_HEADER, USER_AGENT)
            .GET();
    basicAuth().ifPresent(credentials -> builder.header(AUTHORIZATION_HEADER, credentials));
    return builder.build();
  }

  /** Nominatim's viewbox order is {@code <minLon>,<minLat>,<maxLon>,<maxLat>}. */
  private static String viewbox(BoundingBox box) {
    return Stream.of(box.minLon(), box.minLat(), box.maxLon(), box.maxLat())
        .map(NominatimGeocoderCapability::degrees)
        .collect(Collectors.joining(VIEWBOX_SEPARATOR));
  }

  /**
   * Rounds away floating-point noise from the focus arithmetic, so 116.4 - 0.5 is sent as 115.9.
   */
  private static String degrees(double value) {
    return BigDecimal.valueOf(value)
        .setScale(COORDINATE_SCALE, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString();
  }

  private String searchUrl() {
    var baseUrl = properties.getBaseUrl();
    return (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
        + SEARCH_PATH;
  }

  private Optional<String> basicAuth() {
    if (!properties.hasUsername()) {
      return Optional.empty();
    }
    var credentials = properties.getUsername() + ":" + properties.getPassword();
    return Optional.of(
        BASIC_SCHEME
            + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
  }

  private static String encode(Map<String, String> parameters) {
    return parameters.entrySet().stream()
        .map(
            entry ->
                URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  private static long elapsedMillis(long startedNanos) {
    return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
  }

  private static GeocodingException failure(GeocodingFailure failure, String safeMessage) {
    return new GeocodingException(failure, safeMessage);
  }
}
