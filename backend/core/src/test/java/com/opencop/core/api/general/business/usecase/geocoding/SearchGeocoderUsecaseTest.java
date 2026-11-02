// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.api.general.business.model.geocoding.DefaultedField;
import com.opencop.core.api.general.business.model.geocoding.GeocodingSettings;
import com.opencop.core.api.general.business.model.geocoding.PlaceDefect;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.geocoding.GeocodingExceptionCodes;
import com.opencop.core.api.general.ports.usecase.geocoding.GeocodingSearchResult;
import com.opencop.core.api.general.ports.usecase.geocoding.SearchGeocoderCommand;
import com.opencop.core.api.general.technical.gateway.geocoding.InMemoryGeocodingRateLimiter;
import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.geocoding.GeoPoint;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodedPlace;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocoderDescriptor;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingAnswer;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingSearchQuery;
import com.opencop.core.sdk.business.model.integration.geocoding.PlaceType;
import com.opencop.core.sdk.business.registry.integration.geocoding.GeocoderRegistry;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.integration.geocoding.ForwardGeocoding;
import com.opencop.core.sdk.ports.integration.geocoding.GeocoderCapability;
import com.opencop.core.sdk.ports.integration.geocoding.GeocodingException;
import com.opencop.core.sdk.ports.integration.geocoding.GeocodingFailure;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

class SearchGeocoderUsecaseTest {

  private static final String PROVIDER_DETAIL = "Provider detail";
  private static final UUID USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
  private static final Integration INTEGRATION =
      integration("11111111-1111-1111-1111-111111111111");
  private static final Integration OTHER_INTEGRATION =
      integration("22222222-2222-2222-2222-222222222222");
  private static final UUID GEOCODER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID OTHER_USER_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
  private static final GeocodingSettings SETTINGS =
      new GeocodingSettings(Locale.ENGLISH, 10, 20, 200);
  private static final int REQUESTS_PER_SECOND = 10;
  private static final int BURST = 20;
  private static final Duration ONE_TOKEN = Duration.ofSeconds(1).dividedBy(REQUESTS_PER_SECOND);
  private static final String CREDIT = "© Example contributors";
  private static final GeocodedPlace PLACE =
      new GeocodedPlace(
          "R123",
          "Riverside",
          "Riverside, Example County",
          PlaceType.LOCALITY,
          Optional.empty(),
          new GeoPoint(10.5, 20.25),
          Optional.empty(),
          List.of(CREDIT));

  private final ForwardGeocoding geocoder = forwardGeocoding(INTEGRATION, GEOCODER_ID);
  private final IntegrationCatalogGateway catalog = mock(IntegrationCatalogGateway.class);
  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-05T08:00:00Z"));
  private final InMemoryGeocodingRateLimiter rateLimiter =
      new InMemoryGeocodingRateLimiter(REQUESTS_PER_SECOND, BURST, clock);
  private final Logger logger = (Logger) LoggerFactory.getLogger(SearchGeocoderUsecase.class);
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

  private Level previousLevel;

  @BeforeEach
  void captureLogs() {
    previousLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void releaseLogs() {
    logger.detachAppender(logs);
    logs.stop();
    logger.setLevel(previousLevel);
  }

  @Test
  void returnsTheSelectedGeocodersAnswer() throws GeocodingException {
    activate(INTEGRATION);
    when(geocoder.search(any())).thenReturn(new GeocodingAnswer(List.of(PLACE)));

    var result = usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside"));

    assertThat(result)
        .isEqualTo(
            new GeocodingSearchResult(
                INTEGRATION.id(), GEOCODER_ID, List.of(CREDIT), List.of(PLACE)));
  }

  @Test
  void returnsTheValidPlacesAndWarnsAboutAMalformedOne() throws GeocodingException {
    activate(INTEGRATION);
    var unplaced = place("R456", null);
    answer(new GeocodingAnswer(List.of(unplaced, PLACE)));

    var result = usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Secret Street"));

    assertThat(result.places()).containsExactly(PLACE);
    assertThat(logs.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage())
                  .contains(INTEGRATION.id().toString())
                  .contains(GEOCODER_ID.toString())
                  .contains(PlaceDefect.INVALID_POSITION.name())
                  .doesNotContain("Secret Street");
            });
  }

  @Test
  void dropsAPlaceWithoutAnId() throws GeocodingException {
    activate(INTEGRATION);
    answer(new GeocodingAnswer(List.of(place(" ", new GeoPoint(10.5, 20.25)), PLACE)));

    var result = usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside"));

    assertThat(result.places()).containsExactly(PLACE);
  }

  @ParameterizedTest
  @MethodSource("malformedPlaces")
  void dropsAMalformedPlaceAndLogsWhy(GeocodedPlace malformed, PlaceDefect defect)
      throws GeocodingException {
    activate(INTEGRATION);
    answer(new GeocodingAnswer(List.of(malformed, PLACE)));

    var result = usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside"));

    assertThat(result.places()).containsExactly(PLACE);
    assertThat(logs.list)
        .singleElement()
        .extracting(ILoggingEvent::getFormattedMessage)
        .asString()
        .endsWith(defect.name());
  }

  private static Stream<Arguments> malformedPlaces() {
    var position = PLACE.position();
    return Stream.of(
        Arguments.of(
            new GeocodedPlace(
                "R1",
                " ",
                "Label",
                PlaceType.LOCALITY,
                Optional.empty(),
                position,
                Optional.empty(),
                List.of(CREDIT)),
            PlaceDefect.BLANK_NAME),
        Arguments.of(
            new GeocodedPlace(
                "R1",
                "Name",
                "",
                PlaceType.LOCALITY,
                Optional.empty(),
                position,
                Optional.empty(),
                List.of(CREDIT)),
            PlaceDefect.BLANK_LABEL),
        Arguments.of(withAttribution(null), PlaceDefect.INVALID_ATTRIBUTION),
        Arguments.of(withAttribution(Arrays.asList(CREDIT, null)), PlaceDefect.INVALID_ATTRIBUTION),
        Arguments.of(withAttribution(List.of(CREDIT, " ")), PlaceDefect.INVALID_ATTRIBUTION));
  }

  @Test
  void keepsAPlaceWithMissingOptionalFieldsUsingTheirDefaults() throws GeocodingException {
    activate(INTEGRATION);
    var incomplete =
        new GeocodedPlace("R1", "Name", "Label", null, null, PLACE.position(), null, List.of());
    answer(new GeocodingAnswer(List.of(incomplete)));

    var result = usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside"));

    assertThat(result.places())
        .containsExactly(
            new GeocodedPlace(
                "R1",
                "Name",
                "Label",
                PlaceType.OTHER,
                Optional.empty(),
                PLACE.position(),
                Optional.empty(),
                List.of()));
    assertThat(logs.list).extracting(ILoggingEvent::getLevel).containsOnly(Level.DEBUG);
    assertThat(logs.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains(DefaultedField.PLACE_TYPE.name()))
        .anySatisfy(message -> assertThat(message).contains(DefaultedField.CATEGORY.name()))
        .anySatisfy(message -> assertThat(message).contains(DefaultedField.BBOX.name()));
  }

  @Test
  void dropsMalformedPlacesBeforeRemovingDuplicatesAndTruncating() throws GeocodingException {
    activate(INTEGRATION);
    var malformedFirst = place("A", null);
    var first = place("A", new GeoPoint(1, 1));
    var duplicate = place("A", new GeoPoint(2, 2));
    var second = place("B", new GeoPoint(3, 3));
    var third = place("C", new GeoPoint(4, 4));
    answer(new GeocodingAnswer(List.of(malformedFirst, first, duplicate, second, third)));

    var result = usecase().search(request().limit("2").command());

    assertThat(result.places()).containsExactly(first, second);
  }

  @Test
  void keepsTheFirstOfDuplicatePlacesInProviderOrder() throws GeocodingException {
    activate(INTEGRATION);
    var first = place("A", new GeoPoint(1, 1));
    var other = place("B", new GeoPoint(2, 2));
    var duplicate = place("A", new GeoPoint(3, 3));
    answer(new GeocodingAnswer(List.of(first, other, duplicate)));

    var result = usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside"));

    assertThat(result.places()).containsExactly(first, other);
  }

  @Test
  void truncatesPlacesToTheLimit() throws GeocodingException {
    activate(INTEGRATION);
    var places = List.of(place("A", PLACE.position()), place("B", PLACE.position()), PLACE);
    answer(new GeocodingAnswer(places));

    var result = usecase().search(request().limit("2").command());

    assertThat(result.places()).containsExactlyElementsOf(places.subList(0, 2));
  }

  @Test
  void answersWithNoPlacesWhenEveryPlaceIsMalformed() throws GeocodingException {
    activate(INTEGRATION);
    answer(new GeocodingAnswer(List.of(place("A", null), place("", PLACE.position()))));

    var result = usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside"));

    assertThat(result.places()).isEmpty();
  }

  @Test
  void acceptsAPlaceThatRequiresNoAttribution() throws GeocodingException {
    activate(INTEGRATION);
    var uncredited = withAttribution(List.of());
    answer(new GeocodingAnswer(List.of(uncredited)));

    var result = usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside"));

    assertThat(result.attribution()).isEmpty();
    assertThat(result.places()).containsExactly(uncredited);
  }

  @Test
  void listsEachReturnedPlacesCreditOnceInFirstSeenOrder() throws GeocodingException {
    activate(INTEGRATION);
    answer(
        new GeocodingAnswer(
            List.of(
                placeCredited("A", "© Second source", CREDIT),
                placeCredited("B", CREDIT),
                placeCredited("C", "© Third source", "© Second source"))));

    var result = usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside"));

    assertThat(result.attribution()).containsExactly("© Second source", CREDIT, "© Third source");
  }

  @Test
  void leavesOutTheCreditsOfDroppedAndTruncatedPlaces() throws GeocodingException {
    activate(INTEGRATION);
    var dropped =
        new GeocodedPlace(
            "A",
            "",
            "Label",
            PlaceType.LOCALITY,
            Optional.empty(),
            PLACE.position(),
            Optional.empty(),
            List.of("© Dropped source"));
    answer(
        new GeocodingAnswer(
            List.of(
                dropped, placeCredited("B", CREDIT), placeCredited("C", "© Truncated source"))));

    var result = usecase().search(request().limit("1").command());

    assertThat(result.attribution()).containsExactly(CREDIT);
  }

  @Test
  void leavesOutTheCreditsOfADuplicatePlace() throws GeocodingException {
    activate(INTEGRATION);
    answer(
        new GeocodingAnswer(
            List.of(placeCredited("A", CREDIT), placeCredited("A", "© Duplicate source"))));

    var result = usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside"));

    assertThat(result.attribution()).containsExactly(CREDIT);
  }

  @Test
  void keepsTheCreditsAPlaceHadWhenScreened() throws GeocodingException {
    activate(INTEGRATION);
    var credits = new ArrayList<>(List.of(CREDIT));
    answer(new GeocodingAnswer(List.of(withAttribution(credits))));

    var result = usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside"));
    credits.add(" ");

    assertThat(result.places().getFirst().attribution()).containsExactly(CREDIT);
  }

  @Test
  void failsWhenTheGeocoderReturnsNoAnswer() throws GeocodingException {
    assertIncomplete(null);
  }

  @Test
  void failsWhenTheAnswerHasNoPlaces() throws GeocodingException {
    assertIncomplete(new GeocodingAnswer(null));
  }

  @Test
  void sendsTheTrimmedTextWithTheDefaultLimitAndLanguageAndNoFocus() throws GeocodingException {
    activate(INTEGRATION);
    when(geocoder.search(any())).thenReturn(new GeocodingAnswer(List.of()));

    usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "  Riverside \t"));

    var query = ArgumentCaptor.forClass(GeocodingSearchQuery.class);
    verify(geocoder).search(query.capture());
    assertThat(query.getValue())
        .isEqualTo(new GeocodingSearchQuery("Riverside", 10, Optional.empty(), Locale.ENGLISH));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 7, 20})
  void sendsTheRequestedLimit(int limit) throws GeocodingException {
    assertThat(sentQuery(request().limit(Integer.toString(limit))).limit()).isEqualTo(limit);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "21", "-1", "ten", "", "1.5"})
  void rejectsAnInvalidLimit(String limit) throws GeocodingException {
    assertRejected(request().limit(limit));
  }

  @ParameterizedTest
  @CsvSource({"39.9, 116.4", "-90, 180", "90, -180", "0, 0"})
  void sendsTheFocusPoint(String lat, String lon) throws GeocodingException {
    assertThat(sentQuery(request().focus(lat, lon)).focus())
        .contains(new GeoPoint(Double.parseDouble(lat), Double.parseDouble(lon)));
  }

  @Test
  void rejectsHalfAFocusPoint() throws GeocodingException {
    assertRejected(request().focus("39.9", null));
    assertRejected(request().focus(null, "116.4"));
  }

  @ParameterizedTest
  @CsvSource({
    "91, 0",
    "-90.5, 0",
    "0, -181",
    "0, 180.5",
    "NaN, 0",
    "0, NaN",
    "Infinity, 0",
    "north, 0",
    "'', 0"
  })
  void rejectsAnInvalidFocusPoint(String lat, String lon) throws GeocodingException {
    assertRejected(request().focus(lat, lon));
  }

  @Test
  void sendsTheRequestedLanguageWhateverTheClientPrefers() throws GeocodingException {
    var query = sentQuery(request().lang("zh-CN").preferring(Locale.GERMANY, Locale.GERMAN));

    assertThat(query.language()).isEqualTo(Locale.forLanguageTag("zh-CN"));
  }

  @ParameterizedTest
  @CsvSource({
    "' zh-Hant-TW ', zh-Hant-TW",
    "de-DE-u-co-phonebk, de-DE-u-co-phonebk",
    "i-klingon, tlh"
  })
  void sendsAWellFormedRequestedLanguage(String lang, String expectedTag)
      throws GeocodingException {
    var query = sentQuery(request().lang(lang));

    assertThat(query.language().toLanguageTag()).isEqualTo(expectedTag);
  }

  @Test
  void sendsTheClientsPreferredLanguageWhenNoLanguageIsRequested() throws GeocodingException {
    var query = sentQuery(request().preferring(Locale.GERMANY, Locale.GERMAN));

    assertThat(query.language()).isEqualTo(Locale.GERMANY);
  }

  @Test
  void skipsAPreferredLanguageThatNamesNoLanguage() throws GeocodingException {
    var query =
        sentQuery(request().preferring(Locale.forLanguageTag("x-private"), Locale.JAPANESE));

    assertThat(query.language()).isEqualTo(Locale.JAPANESE);
  }

  @Test
  void sendsTheDefaultLanguageWhenTheClientPrefersNone() throws GeocodingException {
    assertThat(sentQuery(request()).language()).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void sendsTheDefaultLanguageWhenNoPreferredLanguageIsUsable() throws GeocodingException {
    assertThat(sentQuery(request().preferring(Locale.ROOT)).language()).isEqualTo(Locale.ENGLISH);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not a tag!", "", "  ", "zh-CN-!", "en--US", "en-US-u", "en-", "en-@"})
  void rejectsAnInvalidLanguage(String lang) throws GeocodingException {
    assertRejected(request().lang(lang).preferring(Locale.GERMANY));
  }

  @Test
  void acceptsTextAtTheMaximumLength() throws GeocodingException {
    activate(INTEGRATION);
    when(geocoder.search(any())).thenReturn(new GeocodingAnswer(List.of()));

    usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "a".repeat(SETTINGS.maxTextLength())));

    verify(geocoder).search(any());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "\t\n"})
  void rejectsMissingText(String text) throws GeocodingException {
    activate(INTEGRATION);

    assertFailsWith(
        GeocodingExceptionCodes.INVALID_REQUEST,
        () -> usecase().search(command(INTEGRATION.id(), GEOCODER_ID, text)));
    verify(geocoder, never()).search(any());
  }

  @Test
  void rejectsTextOverTheMaximumLength() throws GeocodingException {
    activate(INTEGRATION);

    assertFailsWith(
        GeocodingExceptionCodes.INVALID_REQUEST,
        () ->
            usecase()
                .search(
                    command(
                        INTEGRATION.id(), GEOCODER_ID, "a".repeat(SETTINGS.maxTextLength() + 1))));
    verify(geocoder, never()).search(any());
  }

  @Test
  void countsCharactersNotUtf16Units() throws GeocodingException {
    activate(INTEGRATION);
    when(geocoder.search(any())).thenReturn(new GeocodingAnswer(List.of()));

    usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "😀".repeat(SETTINGS.maxTextLength())));

    verify(geocoder).search(any());
  }

  @Test
  void doesNotFindAGeocoderWhenNoneIsRegistered() {
    var usecase =
        new SearchGeocoderUsecase(
            new GeocoderRegistry(List.of(), List.of()), catalog, rateLimiter, SETTINGS);

    assertFailsWith(
        GeocodingExceptionCodes.GEOCODER_NOT_FOUND,
        () -> usecase.search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside")));
  }

  @Test
  void doesNotFindAnUnknownGeocoder() {
    activate(INTEGRATION);

    assertFailsWith(
        GeocodingExceptionCodes.GEOCODER_NOT_FOUND,
        () -> usecase().search(command(INTEGRATION.id(), UUID.randomUUID(), "Riverside")));
  }

  @Test
  void doesNotFindAGeocoderUnderAnUnknownIntegration() {
    assertFailsWith(
        GeocodingExceptionCodes.GEOCODER_NOT_FOUND,
        () -> usecase().search(command(UUID.randomUUID(), GEOCODER_ID, "Riverside")));
  }

  @Test
  void doesNotFindAGeocoderPairedWithAnotherIntegration() throws GeocodingException {
    activate(OTHER_INTEGRATION);

    assertFailsWith(
        GeocodingExceptionCodes.GEOCODER_NOT_FOUND,
        () -> usecase().search(command(OTHER_INTEGRATION.id(), GEOCODER_ID, "Riverside")));
    verify(geocoder, never()).search(any());
  }

  @Test
  void doesNotFindAGeocoderOfAnInactiveIntegration() throws GeocodingException {
    when(catalog.findIntegration(INTEGRATION.id()))
        .thenReturn(Optional.of(identity(INTEGRATION, false)));

    assertFailsWith(
        GeocodingExceptionCodes.GEOCODER_NOT_FOUND,
        () -> usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside")));
    verify(geocoder, never()).search(any());
  }

  @Test
  void doesNotFindAGeocoderWhoseIntegrationIsMissingFromTheCatalog() throws GeocodingException {
    when(catalog.findIntegration(INTEGRATION.id())).thenReturn(Optional.empty());

    assertFailsWith(
        GeocodingExceptionCodes.GEOCODER_NOT_FOUND,
        () -> usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside")));
    verify(geocoder, never()).search(any());
  }

  @Test
  void reportsAProviderFailureAsABadGatewayWithoutProviderDetail() throws GeocodingException {
    activate(INTEGRATION);
    when(geocoder.search(any()))
        .thenThrow(
            new GeocodingException(GeocodingFailure.UNAVAILABLE, "Provider returned HTTP 500"));

    assertThatThrownBy(() -> usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside")))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode()).isEqualTo("GEOCODING-1002");
              assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
              assertThat(exception.getMessage())
                  .isEqualTo(GeocodingExceptionCodes.PROVIDER_FAILED.description())
                  .doesNotContain("HTTP 500");
            });
  }

  @ParameterizedTest
  @CsvSource({
    "TIMEOUT, PROVIDER_TIMED_OUT",
    "RATE_LIMITED, PROVIDER_RATE_LIMITED",
    "UNAVAILABLE, PROVIDER_FAILED",
    "MISCONFIGURED, PROVIDER_FAILED",
    "BAD_RESPONSE, PROVIDER_FAILED"
  })
  void mapsEachProviderFailureToAStatus(GeocodingFailure failure, GeocodingExceptionCodes code)
      throws GeocodingException {
    activate(INTEGRATION);
    when(geocoder.search(any())).thenThrow(new GeocodingException(failure, PROVIDER_DETAIL));

    assertThatThrownBy(() -> usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside")))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode()).isEqualTo(code.code());
              assertThat(exception.getHttpStatus()).isEqualTo(code.httpStatus());
              assertThat(exception.getMessage()).doesNotContain(PROVIDER_DETAIL);
            });
  }

  @Test
  void documentsTheProviderFailureStatuses() {
    assertThat(GeocodingExceptionCodes.PROVIDER_TIMED_OUT.code()).isEqualTo("GEOCODING-1003");
    assertThat(GeocodingExceptionCodes.PROVIDER_TIMED_OUT.httpStatus())
        .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    assertThat(GeocodingExceptionCodes.PROVIDER_RATE_LIMITED.code()).isEqualTo("GEOCODING-1004");
    assertThat(GeocodingExceptionCodes.PROVIDER_RATE_LIMITED.httpStatus())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void logsAMisconfiguredGeocoderAsAnErrorNamingIt() throws GeocodingException {
    activate(INTEGRATION);
    when(geocoder.search(any()))
        .thenThrow(new GeocodingException(GeocodingFailure.MISCONFIGURED, PROVIDER_DETAIL));

    assertFailsWith(
        GeocodingExceptionCodes.PROVIDER_FAILED,
        () -> usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Secret Street")));

    assertThat(logs.list)
        .filteredOn(event -> event.getLevel() == Level.ERROR)
        .singleElement()
        .extracting(ILoggingEvent::getFormattedMessage)
        .satisfies(
            message ->
                assertThat(message)
                    .contains(INTEGRATION.id().toString())
                    .contains(GEOCODER_ID.toString())
                    .doesNotContain("Secret Street")
                    .doesNotContain(PROVIDER_DETAIL));
  }

  @ParameterizedTest
  @EnumSource(
      value = GeocodingFailure.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = "MISCONFIGURED")
  void logsAPassingProviderFailureAsAWarning(GeocodingFailure failure) throws GeocodingException {
    activate(INTEGRATION);
    when(geocoder.search(any())).thenThrow(new GeocodingException(failure, PROVIDER_DETAIL));

    assertThatThrownBy(() -> usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside")));

    assertThat(logs.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN);
  }

  @Test
  void refusesSearchesBeyondTheBurstWithoutCallingTheGeocoder() throws GeocodingException {
    activate(INTEGRATION);
    answer(new GeocodingAnswer(List.of()));
    var usecase = usecase();
    searchRepeatedly(usecase, USER_ID, BURST);

    assertFailsWith(
        GeocodingExceptionCodes.TOO_MANY_REQUESTS, () -> usecase.search(commandBy(USER_ID)));
    verify(geocoder, times(BURST)).search(any());
  }

  @Test
  void throttlesEachUserOnTheirOwnBudget() throws GeocodingException {
    activate(INTEGRATION);
    answer(new GeocodingAnswer(List.of()));
    var usecase = usecase();
    searchRepeatedly(usecase, USER_ID, BURST);

    searchRepeatedly(usecase, OTHER_USER_ID, BURST);

    verify(geocoder, times(2 * BURST)).search(any());
    assertFailsWith(
        GeocodingExceptionCodes.TOO_MANY_REQUESTS, () -> usecase.search(commandBy(USER_ID)));
  }

  @Test
  void letsAThrottledUserSearchAgainOnceTokensRefill() throws GeocodingException {
    activate(INTEGRATION);
    answer(new GeocodingAnswer(List.of()));
    var usecase = usecase();
    searchRepeatedly(usecase, USER_ID, BURST);

    clock.advance(ONE_TOKEN);
    usecase.search(commandBy(USER_ID));

    assertFailsWith(
        GeocodingExceptionCodes.TOO_MANY_REQUESTS, () -> usecase.search(commandBy(USER_ID)));
    verify(geocoder, times(BURST + 1)).search(any());
  }

  @Test
  void refillsNoMoreThanTheBurst() throws GeocodingException {
    activate(INTEGRATION);
    answer(new GeocodingAnswer(List.of()));
    var usecase = usecase();

    clock.advance(Duration.ofHours(1));
    searchRepeatedly(usecase, USER_ID, BURST);

    assertFailsWith(
        GeocodingExceptionCodes.TOO_MANY_REQUESTS, () -> usecase.search(commandBy(USER_ID)));
  }

  @Test
  void spendsNoTokenOnAnInvalidRequest() throws GeocodingException {
    activate(INTEGRATION);
    answer(new GeocodingAnswer(List.of()));
    var usecase = usecase();
    for (int i = 0; i < BURST; i++) {
      assertFailsWith(
          GeocodingExceptionCodes.INVALID_REQUEST,
          () -> usecase.search(command(INTEGRATION.id(), GEOCODER_ID, " ")));
    }

    searchRepeatedly(usecase, USER_ID, BURST);

    verify(geocoder, times(BURST)).search(any());
  }

  @Test
  void rejectsAnInvalidRequestFromAThrottledUserAsInvalid() throws GeocodingException {
    activate(INTEGRATION);
    answer(new GeocodingAnswer(List.of()));
    var usecase = usecase();
    searchRepeatedly(usecase, USER_ID, BURST);

    assertFailsWith(
        GeocodingExceptionCodes.INVALID_REQUEST,
        () -> usecase.search(command(INTEGRATION.id(), GEOCODER_ID, " ")));
  }

  private SearchGeocoderUsecase usecase() {
    var registry =
        new GeocoderRegistry(
            List.<GeocoderCapability>of(geocoder), List.of(INTEGRATION, OTHER_INTEGRATION));
    return new SearchGeocoderUsecase(registry, catalog, rateLimiter, SETTINGS);
  }

  private void activate(Integration integration) {
    when(catalog.findIntegration(integration.id()))
        .thenReturn(Optional.of(identity(integration, true)));
  }

  private void answer(GeocodingAnswer answer) throws GeocodingException {
    when(geocoder.search(any())).thenReturn(answer);
  }

  private void assertIncomplete(GeocodingAnswer answer) throws GeocodingException {
    activate(INTEGRATION);
    answer(answer);

    assertFailsWith(
        GeocodingExceptionCodes.PROVIDER_FAILED,
        () -> usecase().search(command(INTEGRATION.id(), GEOCODER_ID, "Riverside")));
  }

  private static GeocodedPlace place(String id, GeoPoint position) {
    return new GeocodedPlace(
        id,
        "Riverside",
        "Riverside, Example County",
        PlaceType.LOCALITY,
        Optional.empty(),
        position,
        Optional.empty(),
        List.of(CREDIT));
  }

  private static GeocodedPlace placeCredited(String id, String... credits) {
    return withAttribution(id, List.of(credits));
  }

  private static GeocodedPlace withAttribution(List<String> attribution) {
    return withAttribution(PLACE.id(), attribution);
  }

  private static GeocodedPlace withAttribution(String id, List<String> attribution) {
    return new GeocodedPlace(
        id,
        PLACE.name(),
        PLACE.label(),
        PLACE.placeType(),
        PLACE.category(),
        PLACE.position(),
        PLACE.bbox(),
        attribution);
  }

  private GeocodingSearchQuery sentQuery(Request request) throws GeocodingException {
    activate(INTEGRATION);
    when(geocoder.search(any())).thenReturn(new GeocodingAnswer(List.of()));

    usecase().search(request.command());

    var query = ArgumentCaptor.forClass(GeocodingSearchQuery.class);
    verify(geocoder).search(query.capture());
    return query.getValue();
  }

  private void assertRejected(Request request) throws GeocodingException {
    activate(INTEGRATION);

    assertFailsWith(
        GeocodingExceptionCodes.INVALID_REQUEST, () -> usecase().search(request.command()));
    verify(geocoder, never()).search(any());
  }

  private static Request request() {
    return new Request(null, null, null, null, List.of());
  }

  private record Request(
      String limit, String lat, String lon, String lang, List<Locale> preferredLanguages) {

    Request limit(String limit) {
      return new Request(limit, lat, lon, lang, preferredLanguages);
    }

    Request focus(String lat, String lon) {
      return new Request(limit, lat, lon, lang, preferredLanguages);
    }

    Request lang(String lang) {
      return new Request(limit, lat, lon, lang, preferredLanguages);
    }

    Request preferring(Locale... preferredLanguages) {
      return new Request(limit, lat, lon, lang, List.of(preferredLanguages));
    }

    SearchGeocoderCommand command() {
      return new SearchGeocoderCommand(
          USER_ID,
          INTEGRATION.id(),
          GEOCODER_ID,
          "Riverside",
          limit,
          lat,
          lon,
          lang,
          preferredLanguages);
    }
  }

  private static void assertFailsWith(GeocodingExceptionCodes code, ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode()).isEqualTo(code.code());
              assertThat(exception.getHttpStatus()).isEqualTo(code.httpStatus());
            });
  }

  private static SearchGeocoderCommand command(UUID integrationId, UUID geocoderId, String text) {
    return new SearchGeocoderCommand(
        USER_ID, integrationId, geocoderId, text, null, null, null, null, List.of());
  }

  private static void searchRepeatedly(SearchGeocoderUsecase usecase, UUID userId, int times) {
    for (int i = 0; i < times; i++) {
      usecase.search(commandBy(userId));
    }
  }

  private static SearchGeocoderCommand commandBy(UUID userId) {
    return new SearchGeocoderCommand(
        userId, INTEGRATION.id(), GEOCODER_ID, "Riverside", null, null, null, null, List.of());
  }

  private static IntegrationIdentity identity(Integration integration, boolean active) {
    return new IntegrationIdentity(
        integration.id(), integration.name(), integration.description(), active);
  }

  private static ForwardGeocoding forwardGeocoding(Integration integration, UUID id) {
    var geocoder = mock(ForwardGeocoding.class);
    when(geocoder.integration()).thenReturn(integration);
    when(geocoder.id()).thenReturn(id);
    when(geocoder.descriptor())
        .thenReturn(new GeocoderDescriptor(id, "Geocoder", "A test geocoder."));
    return geocoder;
  }

  private static Integration integration(String id) {
    var uuid = UUID.fromString(id);
    return new Integration() {
      @Override
      public UUID id() {
        return uuid;
      }

      @Override
      public String name() {
        return "Integration " + id;
      }

      @Override
      public String description() {
        return "A test integration.";
      }
    };
  }

  private static final class MutableClock extends Clock {

    private Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    private void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
