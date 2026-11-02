// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.opencop.elements;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A point drawn as an image rather than a MIL-STD-2525 symbol, of type {@link
 * ElementTypes#MEDIA_ICON}.
 *
 * <p>{@link #HREF} holds the image's location in one of these forms:
 *
 * <ul>
 *   <li>{@code opencop:url:/v1/layers/{layerId}/media/{mediaId}#WxH} — an image stored by Core,
 *       with its pixel size as a URL fragment the client reads before the image loads;
 *   <li>{@code https://…}, {@code http://…} or {@code data:…} — an external or inline image, kept
 *       as imported;
 *   <li>any other value — an import-relative path that matched no bundled image. There is nothing
 *       to fetch, so the client draws its placeholder for an image it cannot load.
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaIconElement extends Element {

  public static final String HREF = "href";

  public MediaIconElement() {
    super(ElementTypes.MEDIA_ICON.getType());
  }

  private MediaIconElement(Builder builder) {
    super(builder);
  }

  public String getHref() {
    return (String) get(HREF);
  }

  public void setHref(String href) {
    put(HREF, href);
  }

  public static class Builder extends Element.Builder {
    public Builder() {
      type(ElementTypes.MEDIA_ICON);
    }

    public Builder href(String href) {
      return (Builder) put(HREF, href);
    }

    @Override
    public MediaIconElement build() {
      return new MediaIconElement(this);
    }
  }
}
