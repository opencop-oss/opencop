// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.scim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.types.UserResource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;

class ScimHttpMessageConverterTest {

  private static final MediaType SCIM_JSON = MediaType.valueOf("application/scim+json");

  private final ScimHttpMessageConverter converter = new ScimHttpMessageConverter();

  @Test
  void supportsSameMediaTypesAsPreviousScimConverter() {
    assertTrue(converter.canRead(PatchRequest.class, SCIM_JSON));
    assertTrue(converter.canWrite(PatchRequest.class, SCIM_JSON));
    assertTrue(converter.canRead(PatchRequest.class, MediaType.APPLICATION_JSON));
    assertTrue(converter.canWrite(PatchRequest.class, MediaType.APPLICATION_JSON));
    assertFalse(converter.canRead(PatchRequest.class, MediaType.TEXT_PLAIN));
    assertFalse(converter.canWrite(PatchRequest.class, MediaType.TEXT_PLAIN));
  }

  @Test
  void readsValidScimUserResourceBody() throws Exception {
    String body =
        """
        {
          "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
          "userName": "jdoe",
          "displayName": "Jane Doe"
        }
        """;

    Object result = converter.readInternal(UserResource.class, input(body));

    assertTrue(result instanceof UserResource);
    UserResource user = (UserResource) result;
    assertEquals("jdoe", user.getUserName());
    assertEquals("Jane Doe", user.getDisplayName());
  }

  @Test
  void unknownCoreAttributeSurfacesHttpMessageNotReadableException() {
    String body =
        """
        {
          "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
          "userName": "jdoe",
          "actve": false
        }
        """;

    assertThrows(
        HttpMessageNotReadableException.class,
        () -> converter.readInternal(UserResource.class, input(body)));
  }

  @Test
  void writesUserResourceAsReadableScimJson() throws Exception {
    UserResource user = new UserResource().setUserName("jdoe");
    var output = new CapturingOutputMessage();

    converter.writeInternal(user, output);

    UserResource roundTripped =
        (UserResource) converter.readInternal(UserResource.class, input(output.body()));
    assertEquals("jdoe", roundTripped.getUserName());
  }

  @Test
  void malformedBodySurfacesHttpMessageNotReadableException() {
    String malformed = "{ \"userName\": ";

    assertThrows(
        HttpMessageNotReadableException.class,
        () -> converter.readInternal(UserResource.class, input(malformed)));
  }

  private static HttpInputMessage input(String body) {
    InputStream stream = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    return new HttpInputMessage() {
      @Override
      public InputStream getBody() {
        return stream;
      }

      @Override
      public HttpHeaders getHeaders() {
        return new HttpHeaders();
      }
    };
  }

  private static final class CapturingOutputMessage implements HttpOutputMessage {
    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();
    private final HttpHeaders headers = new HttpHeaders();

    @Override
    public OutputStream getBody() {
      return stream;
    }

    @Override
    public HttpHeaders getHeaders() {
      return headers;
    }

    String body() {
      return stream.toString(StandardCharsets.UTF_8);
    }
  }
}
