// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.geocoding;

import com.opencop.core.api.general.ports.usecase.geocoding.ListGeocoders;
import com.opencop.core.api.general.ports.usecase.geocoding.SearchGeocoder;
import com.opencop.core.api.general.ports.usecase.geocoding.SearchGeocoderCommand;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Finds places by name through the geocoders this deployment offers.
 *
 * <p>Open to any authenticated user. Which geocoders exist is decided by deployment configuration;
 * whether each is usable is decided by its integration's activation. Clients discover geocoders
 * first, then address one by its pair of ids.
 */
@RestController
@Tag(
    name = "Geocoding",
    description =
        """
        Find places (cities, districts, landmarks, streets) by name, for typeahead search and \
        moving the map. List the available geocoders, let the user pick one, then search it.""")
public class GeocodersEndpoint {

  private final ListGeocoders listGeocoders;
  private final SearchGeocoder searchGeocoder;

  GeocodersEndpoint(ListGeocoders listGeocoders, SearchGeocoder searchGeocoder) {
    this.listGeocoders = listGeocoders;
    this.searchGeocoder = searchGeocoder;
  }

  @GetMapping("/v1/geocoders")
  @Operation(
      summary = "List the geocoders available to search",
      description =
          """
          Returns every geocoder this deployment enables whose integration an administrator \
          has left active, ordered by integration label, then geocoder label.

          Each entry carries the `integrationId` and `geocoderId` pair that addresses the \
          geocoder in operation paths, and the `operations` it supports. Call only the \
          operations listed; ignore values you do not recognise, since operations will be \
          added over time.

          An empty list is a valid answer: the deployment offers no geocoding. Clients may \
          remember the user's chosen geocoder locally, but must drop the choice when it no \
          longer appears here.""")
  @ApiResponse(
      responseCode = "200",
      description = "The available geocoders; empty when none is enabled and active",
      content =
          @Content(
              array = @ArraySchema(schema = @Schema(implementation = GeocoderResponse.class)),
              examples = {
                @ExampleObject(
                    name = "One geocoder",
                    value = GeocodingOpenApiExamples.GEOCODERS_RESPONSE),
                @ExampleObject(
                    name = "None available",
                    value = GeocodingOpenApiExamples.NO_GEOCODERS_RESPONSE)
              }))
  public List<GeocoderResponse> list() {
    return listGeocoders.list().stream().map(GeocoderResponse::from).toList();
  }

  @GetMapping("/v1/integrations/{integrationId}/geocoders/{geocoderId}/search")
  @Operation(
      summary = "Search a geocoder for places by name",
      description =
          """
          Runs a forward-geocoding search against the geocoder named by the pair of ids from \
          `GET /v1/geocoders`. Designed for typeahead: call it as the user types, debounced.

          Display the credits in a result's `attribution` wherever that place is shown; its \
          provider or data licence requires it. The top-level `attribution` lists every result's \
          credits once, for display under the whole result list, and is empty when there are no \
          results. Credits are plain text: render them as text, never as HTML. Treat an unknown \
          `placeType` as `other`.

          Places the provider returns without an id, name, label or valid position, without an \
          attribution list, with a blank credit, or with a malformed bounding box, are dropped \
          rather than failing the search, so `results` may hold fewer places than the provider \
          found. A place of unknown type is returned as `other`.""")
  @ApiResponse(
      responseCode = "200",
      description = "The places found, possibly none",
      content =
          @Content(
              schema = @Schema(implementation = GeocodingSearchResponse.class),
              examples = {
                @ExampleObject(
                    name = "One district",
                    value = GeocodingOpenApiExamples.SEARCH_RESPONSE),
                @ExampleObject(
                    name = "No match",
                    value = GeocodingOpenApiExamples.EMPTY_SEARCH_RESPONSE)
              }))
  @ApiResponse(
      responseCode = "400",
      description =
          """
          `text` is missing, blank or too long; `limit` is out of range; only one of `lat` and \
          `lon` is given, or either is out of range; `lang` is not a language tag; or an id is \
          not a UUID""",
      content =
          @Content(
              examples =
                  @ExampleObject(
                      name = "Invalid request",
                      value = GeocodingOpenApiExamples.INVALID_REQUEST_RESPONSE)))
  @ApiResponse(
      responseCode = "404",
      description =
          """
          No geocoder is registered under this pair of ids, or its integration is inactive. \
          The two cases are deliberately indistinguishable; refresh the discovery list.""",
      content =
          @Content(
              examples =
                  @ExampleObject(
                      name = "Unknown or unavailable geocoder",
                      value = GeocodingOpenApiExamples.GEOCODER_NOT_FOUND_RESPONSE)))
  @ApiResponse(
      responseCode = "429",
      description =
          """
          This user is searching faster than the deployment allows. Fail quietly; searches \
          succeed again within a second. Debouncing keystrokes keeps typeahead under the limit.""",
      content =
          @Content(
              examples =
                  @ExampleObject(
                      name = "Too many requests",
                      value = GeocodingOpenApiExamples.TOO_MANY_REQUESTS_RESPONSE)))
  @ApiResponse(
      responseCode = "502",
      description =
          """
          The geocoding provider could not answer. Transient from the client's point of view: \
          fail quietly and let the next keystroke retry.""",
      content =
          @Content(
              examples =
                  @ExampleObject(
                      name = "Provider failed",
                      value = GeocodingOpenApiExamples.PROVIDER_FAILED_RESPONSE)))
  @ApiResponse(
      responseCode = "503",
      description =
          """
          The geocoding provider is rate limiting this deployment. Fail quietly and avoid \
          retrying on every keystroke for a while.""",
      content =
          @Content(
              examples =
                  @ExampleObject(
                      name = "Provider rate limited",
                      value = GeocodingOpenApiExamples.PROVIDER_RATE_LIMITED_RESPONSE)))
  @ApiResponse(
      responseCode = "504",
      description =
          """
          The geocoding provider did not answer in time. Transient: fail quietly and let the \
          next keystroke retry.""",
      content =
          @Content(
              examples =
                  @ExampleObject(
                      name = "Provider timed out",
                      value = GeocodingOpenApiExamples.PROVIDER_TIMED_OUT_RESPONSE)))
  public GeocodingSearchResponse search(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @Parameter(description = "Integration owning the geocoder, from `GET /v1/geocoders`")
          @PathVariable
          UUID integrationId,
      @Parameter(description = "Geocoder to search, from `GET /v1/geocoders`") @PathVariable
          UUID geocoderId,
      @Parameter(
              description =
                  "What to look for, such as a place name; trimmed, 1 to 200 characters by default",
              example = "朝阳区")
          @RequestParam(required = false)
          String text,
      @Parameter(
              description =
                  """
                  Maximum number of places to return, 1 to 20 by default. Omit it for the default \
                  of 10; an empty value is rejected with `GEOCODING-1000`.""",
              schema = @Schema(type = "integer", minimum = "1"),
              example = "10")
          @RequestParam(required = false)
          String limit,
      @Parameter(
              description =
                  """
                  Latitude of the focus point, -90 to 90. Send it with `lon`, typically the map \
                  centre: nearby places rank higher, but distant ones are not excluded. Omit both \
                  for an unfocused search; an empty value is rejected with `GEOCODING-1000`.""",
              schema = @Schema(type = "number", minimum = "-90", maximum = "90"),
              example = "39.9")
          @RequestParam(required = false)
          String lat,
      @Parameter(
              description =
                  """
                  Longitude of the focus point, -180 to 180. Send it with `lat`. Omit both for an \
                  unfocused search; an empty value is rejected with `GEOCODING-1000`.""",
              schema = @Schema(type = "number", minimum = "-180", maximum = "180"),
              example = "116.4")
          @RequestParam(required = false)
          String lon,
      @Parameter(
              description =
                  """
                  BCP 47 tag of the language to label results in, such as the user's preferred \
                  locale. Without it, the `Accept-Language` header decides, then the deployment \
                  default. Places with no name in this language keep their default name. An empty \
                  value is rejected with `GEOCODING-1000`.""",
              example = "zh-CN")
          @RequestParam(required = false)
          String lang,
      @Parameter(
              description = "Used only when `lang` is omitted; a malformed header is ignored",
              example = "de-DE,de;q=0.9")
          @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false)
          String acceptLanguage) {
    return GeocodingSearchResponse.from(
        searchGeocoder.search(
            new SearchGeocoderCommand(
                authentication.internalUserId(),
                integrationId,
                geocoderId,
                text,
                limit,
                lat,
                lon,
                lang,
                AcceptLanguageHeader.preferredLanguages(acceptLanguage))));
  }
}
