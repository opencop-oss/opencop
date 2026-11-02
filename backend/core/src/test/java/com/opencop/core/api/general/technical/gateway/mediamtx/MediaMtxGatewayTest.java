// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.mediamtx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.api.general.technical.config.MediaMtxProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class MediaMtxGatewayTest {

  private static final String PATH_NAME = "att_6d063523-0461-439e-b025-ed3baf3b1d18";
  private static final String MEDIAMTX_PATH_NAME = "streams/" + PATH_NAME;
  private static final String RTSP_URL = "rtsp://camera.example.com:554/stream";
  private static final String SRT_URL = "srt://relay.example.com:9000?streamid=compass&latency=200";
  private static final String USERNAME = "admin";
  private static final String PASSWORD = "secret";

  private HttpServer server;
  private int port;
  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  record CapturedRequest(String method, String path, String authHeader, String body) {}

  private final AtomicReference<CapturedRequest> captured = new AtomicReference<>();
  private volatile int responseStatus = 200;

  @BeforeEach
  void startServer() throws IOException {
    captured.set(null);
    responseStatus = 200;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    port = server.getAddress().getPort();
    server.createContext(
        "/",
        exchange -> {
          var method = exchange.getRequestMethod();
          var path = exchange.getRequestURI().getPath();
          var auth = exchange.getRequestHeaders().getFirst("Authorization");
          var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          captured.set(new CapturedRequest(method, path, auth, body));
          exchange.sendResponseHeaders(responseStatus, -1);
          exchange.close();
        });
    server.start();

    logger = (Logger) LoggerFactory.getLogger(MediaMtxGateway.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void stopServer() {
    logger.detachAppender(appender);
    appender.stop();
    server.stop(0);
  }

  private MediaMtxGateway gateway() {
    return gatewayWithPublicBaseUrl("http://localhost/streams");
  }

  private MediaMtxGateway gatewayWithPublicBaseUrl(String publicBaseUrl) {
    var props = new MediaMtxProperties();
    props.getApi().setBaseUrl("http://127.0.0.1:" + port);
    props.getApi().setUsername(USERNAME);
    props.getApi().setPassword(PASSWORD);
    props.getApi().setConnectTimeout(Duration.ofSeconds(2));
    props.getApi().setReadTimeout(Duration.ofSeconds(5));
    props.getHls().setPublicBaseUrl(publicBaseUrl);
    return new MediaMtxGateway(props);
  }

  @Test
  void prepareStream_sendsCorrectPathAndMethod() {
    gateway().prepareStream(PATH_NAME, RTSP_URL);

    var req = captured.get();
    assertEquals("POST", req.method());
    assertEquals("/v3/config/paths/replace/" + MEDIAMTX_PATH_NAME, req.path());
  }

  @Test
  void prepareStream_sendsBasicAuth() {
    gateway().prepareStream(PATH_NAME, RTSP_URL);

    var expected =
        "Basic "
            + Base64.getEncoder()
                .encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
    assertEquals(expected, captured.get().authHeader());
  }

  @Test
  void prepareStream_sendsCorrectJsonBody() {
    gateway().prepareStream(PATH_NAME, RTSP_URL);

    var body = captured.get().body();
    var expectedB64 = Base64.getEncoder().encodeToString(RTSP_URL.getBytes(StandardCharsets.UTF_8));
    assertTrue(body.contains("\"source\": \"publisher\""), "body: " + body);
    assertTrue(body.contains(expectedB64), "body should contain base64 URL: " + body);
    assertFalse(body.contains(RTSP_URL), "raw RTSP URL must not appear in body: " + body);
    assertTrue(body.contains("\"runOnDemandStartTimeout\": \"90s\""), "body: " + body);
    assertTrue(body.contains("\"runOnDemandCloseAfter\": \"60s\""), "body: " + body);
    assertTrue(body.contains("\"runOnDemandRestart\": false"), "body: " + body);
  }

  @Test
  void prepareStream_forSrt_carriesTheExactUrlInTheBase64Argument() {
    gateway().prepareStream(PATH_NAME, SRT_URL);

    var body = captured.get().body();
    var expectedB64 = Base64.getEncoder().encodeToString(SRT_URL.getBytes(StandardCharsets.UTF_8));
    assertTrue(body.contains(expectedB64), "body should contain base64 URL: " + body);
    assertFalse(body.contains("streamid=compass"), "raw SRT URL must not appear in body: " + body);
  }

  @Test
  void prepareStream_on200_returnsHlsUrl() {
    var result = gateway().prepareStream(PATH_NAME, RTSP_URL);

    assertTrue(result.isPresent());
    assertEquals("http://localhost/streams/" + PATH_NAME + "/index.m3u8", result.get());
  }

  @Test
  void prepareStream_on200_publicBaseUrlWithTrailingSlash_doesNotDoubleSlash() {
    var result =
        gatewayWithPublicBaseUrl("http://localhost/streams/").prepareStream(PATH_NAME, RTSP_URL);

    assertTrue(result.isPresent());
    assertFalse(result.get().contains("//att_"), "double-slash in HLS URL: " + result.get());
  }

  @Test
  void prepareStream_on200_relativePublicBaseUrlReturnsTraefikStreamPath() {
    var result = gatewayWithPublicBaseUrl("/streams").prepareStream(PATH_NAME, RTSP_URL);

    assertTrue(result.isPresent());
    assertEquals("/streams/" + PATH_NAME + "/index.m3u8", result.get());
  }

  @Test
  void prepareStream_whenPublicBaseUrlIsBlank_returnsEmpty() {
    var result = gatewayWithPublicBaseUrl(" ").prepareStream(PATH_NAME, RTSP_URL);

    assertTrue(result.isEmpty());
  }

  @Test
  void prepareStream_onNon200_returnsEmpty() {
    responseStatus = 500;

    assertTrue(gateway().prepareStream(PATH_NAME, RTSP_URL).isEmpty());
  }

  @Test
  void prepareStream_onNon200LogDoesNotIncludePathUrlOrCredentials() {
    responseStatus = 500;

    gateway().prepareStream(PATH_NAME, RTSP_URL);

    assertFalse(appender.list.isEmpty());
    var message = appender.list.getFirst().getFormattedMessage();
    assertTrue(message.contains("MediaMTX path registration returned 500"));
    assertNoSensitiveStreamMaterial(message);
  }

  @Test
  void prepareStream_on404_returnsEmpty() {
    responseStatus = 404;

    assertTrue(gateway().prepareStream(PATH_NAME, RTSP_URL).isEmpty());
  }

  @Test
  void prepareStream_whenConnectionRefused_returnsEmpty() throws IOException {
    // Use the real port, then stop the server so the connection is refused.
    server.stop(0);

    assertTrue(gateway().prepareStream(PATH_NAME, RTSP_URL).isEmpty());

    // Restart so @AfterEach stop() doesn't error.
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
    server.start();
  }

  @Test
  void prepareStream_whenInterrupted_restoresInterruptFlagAndThrows() throws Exception {
    // Interrupt the calling thread before the request completes.
    var result = new AtomicReference<Throwable>();
    var thread =
        new Thread(
            () -> {
              Thread.currentThread().interrupt();
              try {
                gateway().prepareStream(PATH_NAME, RTSP_URL);
              } catch (Throwable t) {
                result.set(t);
              }
            });
    thread.start();
    thread.join(5_000);

    assertFalse(thread.isAlive(), "thread should have finished");
    assertTrue(
        thread.isInterrupted() || result.get() instanceof RuntimeException,
        "interrupt flag should be restored or RuntimeException thrown");
    assertTrue(
        result.get() instanceof RuntimeException,
        "expected RuntimeException, got: " + result.get());
  }

  private static void assertNoSensitiveStreamMaterial(String message) {
    assertFalse(message.contains(PATH_NAME));
    assertFalse(message.contains("att_"));
    assertFalse(message.contains(RTSP_URL));
    assertFalse(message.contains("rtsp://"));
    assertFalse(message.contains("srt://"));
    assertFalse(message.contains("streamid"));
    assertFalse(message.contains(USERNAME));
    assertFalse(message.contains(PASSWORD));
    assertFalse(message.contains("/streams/"));
  }
}
