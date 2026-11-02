// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.geocoding;

import com.opencop.core.api.general.business.model.geocoding.GeocodingSettings;
import com.opencop.core.api.general.business.model.geocoding.ScreenedAnswer;
import com.opencop.core.api.general.business.policy.geocoding.GeocodingLanguagePolicy;
import com.opencop.core.api.general.business.policy.geocoding.GeocodingQueryPolicy;
import com.opencop.core.api.general.business.policy.geocoding.GeocodingResultPolicy;
import com.opencop.core.api.general.ports.gateway.geocoding.GeocodingRateLimiter;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.geocoding.GeocodingExceptionCodes;
import com.opencop.core.api.general.ports.usecase.geocoding.GeocodingRequestException;
import com.opencop.core.api.general.ports.usecase.geocoding.GeocodingSearchResult;
import com.opencop.core.api.general.ports.usecase.geocoding.SearchGeocoder;
import com.opencop.core.api.general.ports.usecase.geocoding.SearchGeocoderCommand;
import com.opencop.core.sdk.business.model.integration.geocoding.GeoPoint;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingAnswer;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingSearchQuery;
import com.opencop.core.sdk.business.registry.integration.geocoding.GeocoderRegistry;
import com.opencop.core.sdk.ports.integration.geocoding.ForwardGeocoding;
import com.opencop.core.sdk.ports.integration.geocoding.GeocodingException;
import com.opencop.core.sdk.ports.integration.geocoding.GeocodingFailure;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchGeocoderUsecase implements SearchGeocoder {

  private static final Logger log = LoggerFactory.getLogger(SearchGeocoderUsecase.class);

  private final GeocoderRegistry registry;
  private final AvailableGeocoders availableGeocoders;
  private final GeocodingRateLimiter rateLimiter;
  private final GeocodingSettings settings;

  public SearchGeocoderUsecase(
      GeocoderRegistry registry,
      IntegrationCatalogGateway catalogGateway,
      GeocodingRateLimiter rateLimiter,
      GeocodingSettings settings) {
    this.registry = registry;
    this.availableGeocoders = new AvailableGeocoders(registry, catalogGateway);
    this.rateLimiter = rateLimiter;
    this.settings = settings;
  }

  @Override
  public GeocodingSearchResult search(SearchGeocoderCommand command) {
    var query = validQuery(command);
    throttle(command);
    var geocoder = availableGeocoder(command);
    try {
      var answer = screened(command, geocoder.search(query), query.limit());
      return new GeocodingSearchResult(
          command.integrationId(), command.geocoderId(), answer.attribution(), answer.places());
    } catch (GeocodingException exception) {
      logFailure(command, exception.failure());
      throw new GeocodingRequestException(exceptionCode(exception.failure()));
    }
  }

  /** Runs after validation, so a malformed request costs the user nothing. */
  private void throttle(SearchGeocoderCommand command) {
    if (!rateLimiter.tryAcquire(command.internalUserId())) {
      throw new GeocodingRequestException(GeocodingExceptionCodes.TOO_MANY_REQUESTS);
    }
  }

  private static ScreenedAnswer screened(
      SearchGeocoderCommand command, GeocodingAnswer answer, int limit) {
    var screened =
        GeocodingResultPolicy.screen(answer, limit).orElseThrow(() -> incompleteAnswer(command));
    screened
        .defects()
        .forEach(
            defect ->
                log.warn(
                    "Geocoder {} of integration {} returned an unusable place: {}",
                    command.geocoderId(),
                    command.integrationId(),
                    defect));
    screened
        .defaults()
        .forEach(
            field ->
                log.debug(
                    "Geocoder {} of integration {} left {} null; using its default",
                    command.geocoderId(),
                    command.integrationId(),
                    field));
    return screened;
  }

  private static GeocodingRequestException incompleteAnswer(SearchGeocoderCommand command) {
    log.warn(
        "Geocoder {} of integration {} returned an incomplete answer",
        command.geocoderId(),
        command.integrationId());
    return new GeocodingRequestException(GeocodingExceptionCodes.PROVIDER_FAILED);
  }

  /**
   * A misconfigured geocoder fails every search until someone fixes the deployment, so it is logged
   * as an error; other failures pass on their own and stay at warning level.
   */
  private static void logFailure(SearchGeocoderCommand command, GeocodingFailure failure) {
    if (failure == GeocodingFailure.MISCONFIGURED) {
      log.error(
          "Geocoder {} of integration {} is misconfigured",
          command.geocoderId(),
          command.integrationId());
      return;
    }
    log.warn(
        "Geocoder {} of integration {} failed: {}",
        command.geocoderId(),
        command.integrationId(),
        failure);
  }

  private static GeocodingExceptionCodes exceptionCode(GeocodingFailure failure) {
    return switch (failure) {
      case TIMEOUT -> GeocodingExceptionCodes.PROVIDER_TIMED_OUT;
      case RATE_LIMITED -> GeocodingExceptionCodes.PROVIDER_RATE_LIMITED;
      case UNAVAILABLE, MISCONFIGURED, BAD_RESPONSE -> GeocodingExceptionCodes.PROVIDER_FAILED;
    };
  }

  private GeocodingSearchQuery validQuery(SearchGeocoderCommand command) {
    return new GeocodingSearchQuery(
        validText(command.text()),
        validLimit(command.limit()),
        validFocus(command),
        language(command));
  }

  private String validText(String text) {
    return GeocodingQueryPolicy.searchText(text, settings.maxTextLength())
        .orElseThrow(SearchGeocoderUsecase::invalidRequest);
  }

  private int validLimit(String limit) {
    if (limit == null) {
      return settings.defaultLimit();
    }
    return GeocodingQueryPolicy.limit(limit, settings.maxLimit())
        .orElseThrow(SearchGeocoderUsecase::invalidRequest);
  }

  private static Optional<GeoPoint> validFocus(SearchGeocoderCommand command) {
    if (requestsNoFocus(command)) {
      return Optional.empty();
    }
    return Optional.of(
        GeocodingQueryPolicy.focus(command.lat(), command.lon())
            .orElseThrow(SearchGeocoderUsecase::invalidRequest));
  }

  private static boolean requestsNoFocus(SearchGeocoderCommand command) {
    return command.lat() == null && command.lon() == null;
  }

  private Locale language(SearchGeocoderCommand command) {
    return GeocodingLanguagePolicy.resolve(
            command.lang(), command.preferredLanguages(), settings.defaultLanguage())
        .orElseThrow(SearchGeocoderUsecase::invalidRequest);
  }

  private static GeocodingRequestException invalidRequest() {
    return new GeocodingRequestException(GeocodingExceptionCodes.INVALID_REQUEST);
  }

  private ForwardGeocoding availableGeocoder(SearchGeocoderCommand command) {
    return registry
        .findForwardGeocoding(command.integrationId(), command.geocoderId())
        .filter(availableGeocoders::isAvailable)
        .orElseThrow(
            () -> new GeocodingRequestException(GeocodingExceptionCodes.GEOCODER_NOT_FOUND));
  }
}
