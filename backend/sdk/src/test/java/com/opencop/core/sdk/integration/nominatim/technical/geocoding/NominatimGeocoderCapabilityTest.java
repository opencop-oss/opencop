// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.nominatim.technical.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.business.model.integration.geocoding.BoundingBox;
import com.opencop.core.sdk.business.model.integration.geocoding.GeoPoint;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodedPlace;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingSearchQuery;
import com.opencop.core.sdk.business.model.integration.geocoding.PlaceType;
import com.opencop.core.sdk.integration.nominatim.business.model.NominatimIntegration;
import com.opencop.core.sdk.integration.nominatim.technical.config.NominatimGeocoderProperties;
import com.opencop.core.sdk.ports.integration.geocoding.GeocodingException;
import com.opencop.core.sdk.ports.integration.geocoding.GeocodingFailure;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

class NominatimGeocoderCapabilityTest {

  private static final String FIXTURES = "/integration/nominatim/responses/";
  private static final String SECRET_TEXT = "Secret Street 42";
  private static final String PASSWORD = "p4ssw0rd-secret";
  private static final String BODY_MARKER = "BODY-MARKER-detail";
  private static final Duration SHORT_READ_TIMEOUT = Duration.ofMillis(200);
  private static final Duration SLOWER_THAN_READ_TIMEOUT = SHORT_READ_TIMEOUT.multipliedBy(7);

  private HttpServer server;
  private int port;
  private final AtomicReference<CapturedRequest> captured = new AtomicReference<>();
  private volatile int responseStatus;
  private volatile byte[] responseBody;
  private volatile Duration responseDelay;
  private volatile Duration bodyDelay;
  private volatile boolean chunked;
  private final AtomicInteger requestCount = new AtomicInteger();
  private Logger logger;
  private Level previousLevel;
  private ListAppender<ILoggingEvent> logs;

  record CapturedRequest(
      String method, String path, Map<String, String> query, String userAgent, String auth) {}

  @BeforeEach
  void startServer() throws IOException {
    responseStatus = 200;
    responseBody = "[]".getBytes(StandardCharsets.UTF_8);
    responseDelay = Duration.ZERO;
    bodyDelay = Duration.ZERO;
    chunked = false;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    port = server.getAddress().getPort();
    server.createContext(
        "/",
        exchange -> {
          requestCount.incrementAndGet();
          pause(responseDelay);
          var headers = exchange.getRequestHeaders();
          captured.set(
              new CapturedRequest(
                  exchange.getRequestMethod(),
                  exchange.getRequestURI().getPath(),
                  parseQuery(exchange.getRequestURI().getRawQuery()),
                  headers.getFirst("User-Agent"),
                  headers.getFirst("Authorization")));
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(responseStatus, chunked ? 0 : responseBody.length);
          exchange.getResponseBody().flush();
          pause(bodyDelay);
          exchange.getResponseBody().write(responseBody);
          exchange.close();
        });
    server.start();
  }

  @BeforeEach
  void captureLogs() {
    logger = (Logger) LoggerFactory.getLogger(NominatimGeocoderCapability.class);
    previousLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    logs = new ListAppender<>();
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @AfterEach
  void releaseLogs() {
    logger.detachAppender(logs);
    logs.stop();
    logger.setLevel(previousLevel);
  }

  @ParameterizedTest
  @CsvSource({
    "401, MISCONFIGURED",
    "403, MISCONFIGURED",
    "404, MISCONFIGURED",
    "429, RATE_LIMITED",
    "500, UNAVAILABLE",
    "502, UNAVAILABLE",
    "503, UNAVAILABLE",
    "400, BAD_RESPONSE",
    "422, BAD_RESPONSE"
  })
  void classifiesAnErrorStatusOnceWithoutLeakingRequestDetail(
      int status, GeocodingFailure failure) {
    responseStatus = status;
    respondWithJson("{\"error\":\"" + BODY_MARKER + "\"}");

    assertFailsOnceWith(failure);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not json " + BODY_MARKER,
        "{\"error\":\"" + BODY_MARKER + "\"}",
        "\"" + BODY_MARKER + "\"",
        "[\"" + BODY_MARKER + "\"]",
        "[null]",
        "null",
        ""
      })
  void reportsAnUnreadableBodyAsABadResponse(String body) {
    respondWithJson(body);

    assertFailsOnceWith(GeocodingFailure.BAD_RESPONSE);
  }

  @Test
  void reportsAResponseSlowerThanTheReadTimeoutAsATimeout() {
    responseDelay = SLOWER_THAN_READ_TIMEOUT;

    var properties = securedProperties();
    properties.setReadTimeout(SHORT_READ_TIMEOUT);

    assertFailsOnceWith(properties, GeocodingFailure.TIMEOUT);
  }

  @Test
  void logsAnErrorStatusAtWarningLevel() {
    responseStatus = 404;
    respondWithJson("{\"error\":\"" + BODY_MARKER + "\"}");

    searchFailure(securedProperties());

    assertWarned("HTTP 404");
  }

  @Test
  void logsTheKindOfConnectionFailureAtWarningLevel() throws IOException {
    var properties = properties();
    properties.setBaseUrl("http://127.0.0.1:" + closedPort());

    searchFailure(properties);

    assertWarned("ConnectException");
  }

  @Test
  void logsTheKindOfTimeoutAtWarningLevel() {
    responseDelay = SLOWER_THAN_READ_TIMEOUT;
    var properties = securedProperties();
    properties.setReadTimeout(SHORT_READ_TIMEOUT);

    searchFailure(properties);

    assertWarned("HttpTimeoutException");
  }

  @Test
  void logsAnUnreadableBodyAtWarningLevel() {
    respondWithJson("not json " + BODY_MARKER);

    searchFailure(securedProperties());

    assertWarned("unreadable body");
  }

  @Test
  void reportsABodyStalledAfterTheHeadersAsATimeout() {
    bodyDelay = SLOWER_THAN_READ_TIMEOUT;
    var properties = securedProperties();
    properties.setConnectTimeout(SHORT_READ_TIMEOUT);
    properties.setReadTimeout(SHORT_READ_TIMEOUT);

    var started = System.nanoTime();
    assertFailsOnceWith(properties, GeocodingFailure.TIMEOUT);

    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(SLOWER_THAN_READ_TIMEOUT);
  }

  @ParameterizedTest
  @CsvSource({"200, false", "200, true", "503, false", "503, true"})
  void reportsABodyLargerThanTheByteLimitAsUnavailable(int status, boolean chunkedBody) {
    responseStatus = status;
    chunked = chunkedBody;
    respondWithJsonOfSize(NominatimGeocoderCapability.MAX_RESPONSE_BYTES + 1);

    assertFailsOnceWith(GeocodingFailure.UNAVAILABLE);
  }

  @Test
  void logsTheKindOfOversizedBodyFailureAtWarningLevel() {
    respondWithJsonOfSize(NominatimGeocoderCapability.MAX_RESPONSE_BYTES + 1);

    searchFailure(securedProperties());

    assertWarned("IOException");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void readsABodyOfExactlyTheByteLimit(boolean chunkedBody) throws GeocodingException {
    chunked = chunkedBody;
    respondWithJsonOfSize(NominatimGeocoderCapability.MAX_RESPONSE_BYTES);

    assertThat(capability().search(query("place")).places()).isEmpty();
  }

  @Test
  void logsTheOutcomeAndDurationOfACall() throws GeocodingException {
    capability(securedProperties()).search(query(SECRET_TEXT));

    assertThat(logs.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("Nominatim").contains("200"));
    assertNoLeak(null);
  }

  @Test
  void sendsTheSearchRequest() throws GeocodingException {
    capability().search(query("Main Street"));

    var request = captured.get();
    assertThat(request.method()).isEqualTo("GET");
    assertThat(request.path()).isEqualTo("/search");
    assertThat(request.query())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "q", "Main Street",
                "format", "jsonv2",
                "limit", "10",
                "accept-language", "en",
                "dedupe", "1",
                "addressdetails", "1",
                "namedetails", "0"));
    assertThat(request.userAgent()).isEqualTo("OpenCOP");
    assertThat(request.auth()).isNull();
  }

  @Test
  void sendsTheLimitAndLanguage() throws GeocodingException {
    capability()
        .search(
            new GeocodingSearchQuery("朝阳区", 3, Optional.empty(), Locale.forLanguageTag("zh-CN")));

    assertThat(captured.get().query())
        .containsEntry("limit", "3")
        .containsEntry("accept-language", "zh-CN");
  }

  @Test
  void sendsNoViewboxWithoutAFocusPoint() throws GeocodingException {
    capability().search(query("Main Street"));

    assertThat(captured.get().query()).doesNotContainKeys("viewbox", "bounded");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "39.9 | 116.4 | 116.35,39.85,116.45,39.95",
        "0 | 0 | -0.05,-0.05,0.05,0.05",
        "89.98 | 10 | 9.95,89.93,10.05,90",
        "-90 | 10 | 9.95,-90,10.05,-89.95",
        "10 | 179.98 | 179.93,9.95,180,10.05",
        "10 | -180 | -180,9.95,-179.95,10.05"
      })
  void biasesTowardsTheFocusPointWithAClampedViewbox(double lat, double lon, String viewbox)
      throws GeocodingException {
    capability()
        .search(
            new GeocodingSearchQuery(
                "Main Street", 10, Optional.of(new GeoPoint(lat, lon)), Locale.ENGLISH));

    assertThat(captured.get().query())
        .containsEntry("viewbox", viewbox)
        .containsEntry("bounded", "0");
  }

  @Test
  void sizesTheViewboxFromConfiguration() throws GeocodingException {
    var properties = properties();
    properties.setFocusHalfSizeDegrees(0.25);

    capability(properties)
        .search(
            new GeocodingSearchQuery(
                "Main Street", 10, Optional.of(new GeoPoint(39.9, 116.4)), Locale.ENGLISH));

    assertThat(captured.get().query()).containsEntry("viewbox", "116.15,39.65,116.65,40.15");
  }

  @Test
  void encodesReservedCharactersInTheSearchText() throws GeocodingException {
    capability().search(query("a&b=c #d"));

    assertThat(captured.get().query()).containsEntry("q", "a&b=c #d");
  }

  @Test
  void keepsThePathOfABaseUrlBehindAReverseProxy() throws GeocodingException {
    var properties = properties();
    properties.setBaseUrl("http://127.0.0.1:" + port + "/nominatim/");

    capability(properties).search(query("Main Street"));

    assertThat(captured.get().path()).isEqualTo("/nominatim/search");
  }

  @Test
  void sendsBasicCredentialsWhenConfigured() throws GeocodingException {
    var properties = properties();
    properties.setUsername("opencop");
    properties.setPassword("s3cret:pass");

    capability(properties).search(query("Main Street"));

    var expected =
        "Basic "
            + Base64.getEncoder()
                .encodeToString("opencop:s3cret:pass".getBytes(StandardCharsets.UTF_8));
    assertThat(captured.get().auth()).isEqualTo(expected);
  }

  @Test
  void mapsAPlace() throws GeocodingException {
    respondWithJson(
        """
        [{"place_id":42,"osm_type":"relation","osm_id":123,"lat":"10.5","lon":"20.25",
          "category":"boundary","type":"administrative","addresstype":"city","name":"Riverside",
          "display_name":"Riverside, Example County",
          "boundingbox":["10.0","11.0","20.0","21.0"]}]
        """);

    var answer = capability().search(query("Riverside"));

    assertThat(answer.places())
        .containsExactly(
            new GeocodedPlace(
                "R123",
                "Riverside",
                "Riverside, Example County",
                PlaceType.LOCALITY,
                Optional.empty(),
                new GeoPoint(10.5, 20.25),
                Optional.of(new BoundingBox(10.0, 20.0, 11.0, 21.0)),
                List.of("© OpenStreetMap contributors")));
  }

  @Test
  void mapsEveryRecordedResultInTheProvidersOrder() throws GeocodingException {
    respondWith("search-mixed-venues.json");

    var places = capability().search(query("station")).places();

    assertThat(places)
        .extracting(GeocodedPlace::id, GeocodedPlace::placeType)
        .containsExactly(
            tuple("W900101", PlaceType.VENUE),
            tuple("W900102", PlaceType.VENUE),
            tuple("N900103", PlaceType.OTHER),
            tuple("N900104", PlaceType.VENUE),
            tuple("N900105", PlaceType.VENUE),
            tuple("N900106", PlaceType.VENUE));
    assertThat(places).allSatisfy(place -> assertThat(place.bbox()).isPresent());
    assertThat(places)
        .allSatisfy(
            place ->
                assertThat(place.attribution()).containsExactly("© OpenStreetMap contributors"));
  }

  @Test
  void mapsRecordedAdministrativeLevels() throws GeocodingException {
    assertThat(placeTypesIn("search-district.json")).containsExactly(PlaceType.LOCALITY);
    assertThat(placeTypesIn("search-city.json")).containsExactly(PlaceType.LOCALITY);
    assertThat(placeTypesIn("search-suburb.json")).containsExactly(PlaceType.NEIGHBOURHOOD);
    assertThat(placeTypesIn("search-towns.json").subList(0, 2))
        .containsExactly(PlaceType.LOCALITY, PlaceType.LOCALITY);
  }

  @Test
  void returnsNoPlacesForAnEmptyResult() throws GeocodingException {
    respondWith("search-empty.json");

    var answer = capability().search(query("nothing here"));

    assertThat(answer.places()).isEmpty();
  }

  @Test
  void identifiesAComputedPostcodeByItsCountryAndPostcode() throws GeocodingException {
    respondWith("search-postcode.json");

    var place = capability().search(query("postcode")).places().getFirst();

    assertThat(place.id()).isEqualTo("P:xx:900000");
    assertThat(place.placeType()).isEqualTo(PlaceType.POSTALCODE);
  }

  @Test
  void leavesTheIdBlankForAPostcodeWithoutACountry() throws GeocodingException {
    respondWithJson(
        """
        [{"lat":"10.5","lon":"20.25","category":"place","type":"postcode","addresstype":"postcode",
          "name":"900000","display_name":"900000","address":{"postcode":"900000"}}]
        """);

    var place = capability().search(query("postcode")).places().getFirst();

    assertThat(place.id()).isEmpty();
  }

  @Test
  void leavesTheIdBlankForAnyOtherResultWithoutAnOpenStreetMapReference()
      throws GeocodingException {
    respondWithJson(
        """
        [{"lat":"10.5","lon":"20.25","category":"place","type":"city","addresstype":"city",
          "name":"Riverside","display_name":"Riverside",
          "address":{"country_code":"xx","postcode":"900000"}}]
        """);

    var place = capability().search(query("Riverside")).places().getFirst();

    assertThat(place.id()).isEmpty();
  }

  @Test
  void namesAnUnnamedResultAfterTheFirstSegmentOfItsLabel() throws GeocodingException {
    respondWithJson(
        """
        [{"osm_type":"way","osm_id":7,"lat":"10.5","lon":"20.25","addresstype":"road",
          "name":"","display_name":"Main Street, Riverside"}]
        """);

    var place = capability().search(query("Main Street")).places().getFirst();

    assertThat(place.name()).isEqualTo("Main Street");
    assertThat(place.bbox()).isEmpty();
  }

  @Test
  void reportsAnUnreachableInstanceAsUnavailable() throws IOException {
    var properties = securedProperties();
    properties.setBaseUrl("http://127.0.0.1:" + closedPort());

    var exception = searchFailure(properties);

    assertThat(exception.failure()).isEqualTo(GeocodingFailure.UNAVAILABLE);
    assertNoLeak(exception);
  }

  @Test
  void identifiesItselfAsTheNominatimGeocoder() {
    var capability = capability();

    assertThat(capability.integration()).isEqualTo(new NominatimIntegration());
    assertThat(capability.id()).isEqualTo(NominatimGeocoderCapability.GEOCODER_ID);
    assertThat(capability.descriptor().id()).isEqualTo(NominatimGeocoderCapability.GEOCODER_ID);
    assertThat(capability.descriptor().label()).isEqualTo("Nominatim");
  }

  private void assertFailsOnceWith(GeocodingFailure failure) {
    assertFailsOnceWith(securedProperties(), failure);
  }

  private void assertFailsOnceWith(
      NominatimGeocoderProperties properties, GeocodingFailure failure) {
    var exception = searchFailure(properties);

    assertThat(exception.failure()).isEqualTo(failure);
    assertThat(requestCount).hasValue(1);
    assertNoLeak(exception);
  }

  private static GeocodingException searchFailure(NominatimGeocoderProperties properties) {
    return catchThrowableOfType(
        GeocodingException.class, () -> capability(properties).search(query(SECRET_TEXT)));
  }

  private void assertWarned(String detail) {
    assertThat(logs.list)
        .filteredOn(event -> event.getLevel() == Level.WARN)
        .singleElement()
        .extracting(ILoggingEvent::getFormattedMessage)
        .satisfies(message -> assertThat(message).contains("Nominatim").contains(detail));
    assertNoLeak(null);
  }

  /**
   * Asserts that neither the exception, its causes nor any captured log line carries the request
   * URL, the search text, the response body or the password.
   */
  private void assertNoLeak(GeocodingException exception) {
    var texts = new ArrayList<String>();
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      texts.add(String.valueOf(cause.getMessage()));
    }
    for (var event : logs.list) {
      texts.add(event.getFormattedMessage());
      for (var thrown = event.getThrowableProxy(); thrown != null; thrown = thrown.getCause()) {
        texts.add(String.valueOf(thrown.getMessage()));
      }
    }
    var encodedText = URLEncoder.encode(SECRET_TEXT, StandardCharsets.UTF_8);
    assertThat(texts)
        .allSatisfy(
            text ->
                assertThat(text)
                    .doesNotContain("127.0.0.1")
                    .doesNotContain("/search")
                    .doesNotContain("q=")
                    .doesNotContain(SECRET_TEXT)
                    .doesNotContain(encodedText)
                    .doesNotContain(BODY_MARKER)
                    .doesNotContain(PASSWORD));
  }

  private NominatimGeocoderProperties securedProperties() {
    var properties = properties();
    properties.setUsername("opencop");
    properties.setPassword(PASSWORD);
    return properties;
  }

  private static void pause(Duration delay) {
    if (delay.isZero()) {
      return;
    }
    try {
      Thread.sleep(delay);
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }

  private List<PlaceType> placeTypesIn(String fixture) throws GeocodingException {
    respondWith(fixture);
    return capability().search(query("place")).places().stream()
        .map(GeocodedPlace::placeType)
        .toList();
  }

  private void respondWithJson(String json) {
    responseBody = json.getBytes(StandardCharsets.UTF_8);
  }

  /** An empty result list padded with leading whitespace to exactly {@code size} bytes. */
  private void respondWithJsonOfSize(long size) {
    var body = new byte[Math.toIntExact(size)];
    Arrays.fill(body, (byte) ' ');
    body[body.length - 2] = '[';
    body[body.length - 1] = ']';
    responseBody = body;
  }

  private void respondWith(String fixture) {
    try (var stream = getClass().getResourceAsStream(FIXTURES + fixture)) {
      responseBody = Objects.requireNonNull(stream, fixture).readAllBytes();
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private NominatimGeocoderCapability capability() {
    return capability(properties());
  }

  private static NominatimGeocoderCapability capability(NominatimGeocoderProperties properties) {
    return new NominatimGeocoderCapability(new NominatimIntegration(), properties);
  }

  private NominatimGeocoderProperties properties() {
    var properties = new NominatimGeocoderProperties();
    properties.setEnabled(true);
    properties.setBaseUrl("http://127.0.0.1:" + port);
    properties.setReadTimeout(Duration.ofSeconds(2));
    return properties;
  }

  private static GeocodingSearchQuery query(String text) {
    return new GeocodingSearchQuery(text, 10, Optional.empty(), Locale.ENGLISH);
  }

  private static int closedPort() throws IOException {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static Map<String, String> parseQuery(String rawQuery) {
    var parameters = new LinkedHashMap<String, String>();
    if (rawQuery == null) {
      return parameters;
    }
    for (var pair : rawQuery.split("&")) {
      var separator = pair.indexOf('=');
      parameters.put(
          URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
          URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
    }
    return parameters;
  }
}
