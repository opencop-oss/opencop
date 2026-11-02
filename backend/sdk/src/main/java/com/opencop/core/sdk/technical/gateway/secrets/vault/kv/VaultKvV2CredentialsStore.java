// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.secrets.vault.kv;

import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

public final class VaultKvV2CredentialsStore implements SecretStore {

  private static final String VAULT_HEADER = "X-Vault-Token";
  private static final Logger log = LoggerFactory.getLogger(VaultKvV2CredentialsStore.class);

  private final VaultSecretsProperties props;
  private final RestClient client;
  private final ObjectMapper om;
  private static final String URL = "/v1/%s/data/%s";

  public VaultKvV2CredentialsStore(VaultSecretsProperties props, ObjectMapper objectMapper) {
    this.props = props;
    this.om = objectMapper;

    SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
    rf.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
    rf.setReadTimeout((int) Duration.ofSeconds(10).toMillis());

    this.client =
        RestClient.builder()
            .baseUrl(props.addr())
            .requestFactory(rf)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    log.info("VaultKvV2SecretStore created");
  }

  @Override
  public Map<String, String> read(String ref) {

    String path = URL.formatted(props.mount(), normalizeRef(ref));

    var req = client.get().uri(path);
    String token = readToken();

    if (token != null) {
      req.header(VAULT_HEADER, token);
    }
    try {
      String json = req.retrieve().body(String.class);

      if (json == null || json.isBlank()) {
        return Map.of();
      }

      Map<String, Object> root = readJsonMap(json);

      Object dataObj = root.get("data");
      if (!(dataObj instanceof Map<?, ?> dataLevel)) {
        return Map.of();
      }

      Object innerDataObj = dataLevel.get("data");
      if (!(innerDataObj instanceof Map<?, ?> innerData)) {
        return Map.of();
      }

      Map<String, String> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> e : innerData.entrySet()) {
        if (e.getKey() == null || e.getValue() == null) {
          continue;
        }
        out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
      }
      return out;

    } catch (HttpClientErrorException.NotFound _) {
      return Map.of();
    } catch (HttpClientErrorException | HttpServerErrorException e) {
      throw new IllegalStateException(
          "Vault read failed (status=" + e.getStatusCode().value() + ") for ref=" + ref, e);
    } catch (ResourceAccessException e) {
      throw new IllegalStateException("Vault read failed (I/O) for ref=" + ref, e);
    }
  }

  //  @Override
  public String read(String ref, String key) {
    var stored = this.read(ref);
    return stored.get(key);
  }

  @SuppressWarnings("unchecked")
  private AbstractMap.SimpleEntry<Map<String, String>, Long> readDataAndVersion(String ref) {
    String token = readToken();
    String path = URL.formatted(props.mount(), normalizeRef(ref));

    try {
      RestClient.RequestHeadersSpec<?> req = client.get().uri(path);
      if (token != null) req = req.header(VAULT_HEADER, token);

      String json = req.retrieve().body(String.class);

      if (json == null || json.isBlank()) {
        return new AbstractMap.SimpleEntry<>(Map.of(), -1L);
      }

      Map<String, Object> root = readJsonMap(json);

      Object dataObj = root.get("data");
      if (!(dataObj instanceof Map<?, ?> dataLevel)) {
        return new AbstractMap.SimpleEntry<>(Map.of(), -1L);
      }

      // extract inner data map
      Map<String, String> out = new LinkedHashMap<>();
      Object innerDataObj = dataLevel.get("data");
      if (innerDataObj instanceof Map<?, ?> innerData) {
        for (Map.Entry<?, ?> entry : innerData.entrySet()) {
          if (entry.getKey() == null || entry.getValue() == null) {
            continue;
          }
          out.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
      }

      // extract version
      long version = -1L;
      Object metaObj = dataLevel.get("metadata");
      if (metaObj instanceof Map<?, ?> meta) {
        Object v = meta.get("version");
        if (v instanceof Number n) {
          version = n.longValue();
        } else if (v != null) {
          try {
            version = Long.parseLong(String.valueOf(v));
          } catch (NumberFormatException _) {
          }
        }
      }

      return new java.util.AbstractMap.SimpleEntry<>(out, version);

    } catch (HttpClientErrorException.NotFound _) {
      // Missing secret -> treat as "create-only" baseline if you want.
      // For patch we can treat as version 0 meaning "doesn't exist yet".
      return new java.util.AbstractMap.SimpleEntry<>(Map.of(), 0L);

    } catch (HttpClientErrorException | HttpServerErrorException e) {
      throw new IllegalStateException(
          "Vault read failed (status=" + e.getStatusCode().value() + ") for ref=" + ref, e);
    } catch (ResourceAccessException e) {
      throw new IllegalStateException("Vault read failed (I/O) for ref=" + ref, e);
    }
  }

  //  @Override
  //  public void write(String ref, Map<String, String> values) {
  //    if (values == null || values.isEmpty()) {
  //      throw new IllegalArgumentException("values must not be empty");
  //    }
  //    String path = "/v1/" + props.mount() + "/data/" + normalizeRef(ref);
  //
  //
  //
  //    var req = client.post().uri(path);
  //    String token = readToken();
  //
  //    if(token != null) {
  //      req.header(VAULT_HEADER, token);
  //    }
  //
  //    Map<String, Object> body = Map.of("data", values);
  //    try {
  //      req
  //          .contentType(MediaType.APPLICATION_JSON)
  //          .body(body)
  //          .retrieve()
  //          .toBodilessEntity();
  //
  //    } catch (HttpClientErrorException | HttpServerErrorException e) {
  //      throw new IllegalStateException(
  //          "Vault write failed (status=" + e.getStatusCode().value() + ") for ref=" + ref, e);
  //    } catch (ResourceAccessException e) {
  //      throw new IllegalStateException("Vault write failed (I/O) for ref=" + ref, e);
  //    }
  //  }

  public Map<String, String> patch(
      String ref, Map<String, String> upserts, Set<String> removeKeys) {
    if ((upserts == null || upserts.isEmpty()) && (removeKeys == null || removeKeys.isEmpty())) {
      throw new IllegalArgumentException("patch must include upserts and/or removeKeys");
    }

    String token = readToken();
    String path = URL.formatted(props.mount(), normalizeRef(ref));

    int maxAttempts = 5;
    long backoffMs = 50;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      var versionedData = readDataAndVersion(ref);
      Map<String, String> current = versionedData.getKey();
      long version = versionedData.getValue();

      if (version < 0) {
        throw new IllegalStateException(
            "Vault response missing KV v2 version metadata for ref=" + ref);
      }

      Map<String, String> merged = getMergedMap(upserts, removeKeys, current);

      if (merged.isEmpty()) {
        throw new IllegalStateException(
            "Patch would result in empty secret document; refusing for ref=" + ref);
      }

      Map<String, Object> body = Map.of("data", merged, "options", Map.of("cas", version));

      try {
        RestClient.RequestBodySpec req =
            client.post().uri(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
          req = req.header(VAULT_HEADER, token);
        }

        req.body(body).retrieve().toBodilessEntity();
        return merged;

      } catch (HttpClientErrorException.Conflict conflict) {
        // 409: someone wrote a new version between read and write
        if (attempt == maxAttempts) {
          throw new IllegalStateException(
              "Vault patch failed due to repeated write conflicts for ref=" + ref, conflict);
        }
        try {
          Thread.sleep(backoffMs);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Vault patch interrupted for ref=" + ref, ie);
        }
        backoffMs = Math.min(backoffMs * 2, 500);

      } catch (HttpClientErrorException | HttpServerErrorException e) {
        throw new IllegalStateException(
            "Vault patch failed (status=" + e.getStatusCode().value() + ") for ref=" + ref, e);
      } catch (ResourceAccessException e) {
        throw new IllegalStateException("Vault patch failed (I/O) for ref=" + ref, e);
      }
    }

    throw new IllegalStateException("Vault patch failed for ref=" + ref);
  }

  private static @NonNull Map<String, String> getMergedMap(
      Map<String, String> upserts, Set<String> removeKeys, Map<String, String> current) {
    Map<String, String> merged = new LinkedHashMap<>(current);

    if (removeKeys != null) {
      for (String k : removeKeys) {
        if (k != null) {
          merged.remove(k);
        }
      }
    }

    if (upserts != null) {
      for (var e : upserts.entrySet()) {
        String k = e.getKey();
        String v = e.getValue();
        if (k == null) {
          continue;
        }

        // null means remove
        if (v == null) {
          merged.remove(k);
        } else {
          merged.put(k, v);
        }
      }
    }
    return merged;
  }

  @Override
  public void deleteAllVersions(String ref) {
    String path = "/v1/" + props.mount() + "/metadata/" + normalizeRef(ref);

    var req = client.delete().uri(path);
    String token = readToken();

    if (token != null) {
      req.header(VAULT_HEADER, token);
    }
    try {
      req.retrieve().toBodilessEntity();

    } catch (HttpClientErrorException.NotFound _) {
      // ok
    } catch (HttpClientErrorException | HttpServerErrorException e) {
      throw new IllegalStateException(
          "Vault delete failed (status=" + e.getStatusCode().value() + ") for ref=" + ref, e);
    } catch (ResourceAccessException e) {
      throw new IllegalStateException("Vault delete failed (I/O) for ref=" + ref, e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJsonMap(String json) {
    Map<String, Object> map = om.readValue(json, Map.class);
    return (map == null) ? Collections.emptyMap() : map;
  }

  private String readToken() {
    String tokenFile = props.tokenFile();
    if (StringUtils.isEmpty(tokenFile)) {
      return null;
    }
    try {
      String token = Files.readString(Path.of(tokenFile)).trim();
      if (token.isBlank()) {
        throw new IllegalStateException("Vault token file is empty: " + tokenFile);
      }
      return token;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read Vault token file: " + tokenFile, e);
    }
  }

  private static String normalizeRef(String reference) {
    String ref = reference.trim();
    while (ref.startsWith("/")) {
      ref = ref.substring(1);
    }
    if (ref.contains("..")) {
      throw new IllegalArgumentException("reference must not contain '..'");
    }
    return ref;
  }
}
