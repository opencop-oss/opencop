// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.opencop;

import com.opencop.core.sdk.technical.model.opencop.elements.Element;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public class OpenCopProperties implements OCPropertiesExtension {

  /** Attribute key carrying an event's own video stream URL, for auto-attachment on persist. */
  public static final String VIDEO_URL_ATTRIBUTE = "video-url";

  private UUID id;
  private String version;
  private String objectId;
  private String kind;
  private UUID importId;
  private UUID source;
  private String displayName;
  private Contact author;
  private List<Contact> contacts;
  private Element element;

  private Timestampz timestampz;

  // attributes as a free-form key/value map
  private Map<String, String> attributes;
  private List<EventExtension> extensions;

  public OpenCopProperties() {}

  public OpenCopProperties(Builder builder) {
    this.version = builder.version;
    this.id = builder.id;
    this.objectId = builder.objectId;
    this.kind = builder.kind;
    this.source = builder.source;
    this.importId = builder.importId;
    this.displayName = builder.displayName;
    this.author = builder.author;
    this.contacts = builder.contacts;
    this.timestampz = builder.timestampz;
    this.attributes = builder.attributes;
    this.extensions = builder.extensions;
    this.element = builder.element;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getObjectId() {
    return objectId;
  }

  public void setObjectId(String objectId) {
    this.objectId = objectId;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public UUID getSource() {
    return source;
  }

  public void setSource(UUID source) {
    this.source = source;
  }

  public UUID getImportId() {
    return importId;
  }

  public void setImportId(UUID importId) {
    this.importId = importId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public Contact getAuthor() {
    return author;
  }

  public void setAuthor(Contact author) {
    this.author = author;
  }

  public List<Contact> getContacts() {
    return contacts;
  }

  public void setContacts(List<Contact> contacts) {
    this.contacts = contacts;
  }

  public Timestampz getTimestampz() {
    return timestampz;
  }

  public void setTimestampz(Timestampz timestampz) {
    this.timestampz = timestampz;
  }

  public Map<String, String> getAttributes() {
    return attributes;
  }

  public void setAttributes(Map<String, String> attributes) {
    this.attributes = attributes;
  }

  public List<EventExtension> getExtensions() {
    return extensions;
  }

  public void setExtensions(List<EventExtension> extensions) {
    this.extensions = extensions;
  }

  public Element getElement() {
    return element;
  }

  public void setElement(Element element) {
    this.element = element;
  }

  public static class Builder {

    private String version;
    private UUID id;
    private String objectId;
    private String kind;
    private UUID source;
    private UUID importId;
    private String displayName;
    private Contact author;
    private final List<Contact> contacts = new ArrayList<>();
    private Timestampz timestampz;
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final List<EventExtension> extensions = new ArrayList<>();
    private Element element;

    public Builder id(UUID v) {
      this.id = v;
      return this;
    }

    public Builder version(String v) {
      this.version = v;
      return this;
    }

    public Builder objectId(String v) {
      this.objectId = v;
      return this;
    }

    public Builder kind(String v) {
      this.kind = v;
      return this;
    }

    public Builder source(UUID v) {
      this.source = v;
      return this;
    }

    public Builder importId(UUID importId) {
      this.importId = importId;
      return this;
    }

    public Builder displayName(String v) {
      this.displayName = v;
      return this;
    }

    public Builder author(Contact author) {
      this.author = author;
      return this;
    }

    public Builder contacts(List<Contact> list) {
      this.contacts.clear();
      if (list != null) this.contacts.addAll(list);
      return this;
    }

    public Builder addContact(Contact c) {
      if (c != null) this.contacts.add(c);
      return this;
    }

    public Builder addContactPhone(String phone) {
      this.contacts.add(new Contact.Builder().withPhone(phone).build());
      return this;
    }

    public Builder timestampz(Timestampz t) {
      this.timestampz = t;
      return this;
    }

    public Builder timestampz(long time, long start, long stale) {
      this.timestampz = new Timestampz(time, start, stale);
      return this;
    }

    public Builder attributes(Map<String, String> map) {
      this.attributes.clear();
      if (map != null) this.attributes.putAll(map);
      return this;
    }

    public Builder putAttribute(String key, String value) {
      if (key != null) this.attributes.put(key, value);
      return this;
    }

    public Builder extensions(List<EventExtension> list) {
      this.extensions.clear();
      if (list != null) this.extensions.addAll(list);
      return this;
    }

    public Builder addExtension(EventExtension e) {
      if (e != null) this.extensions.add(e);
      return this;
    }

    public Builder addExtension(String namespace, String schema, JsonNode content) {
      this.extensions.add(new EventExtension(namespace, schema, content));
      return this;
    }

    public Builder element(Element element) {
      this.element = element;
      return this;
    }

    public OpenCopProperties build() {
      return new OpenCopProperties(this);
    }
  }
}
