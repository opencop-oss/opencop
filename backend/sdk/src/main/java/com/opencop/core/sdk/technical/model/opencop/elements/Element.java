// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.opencop.elements;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
    fieldVisibility = JsonAutoDetect.Visibility.NONE)
public class Element {
  private final String type;
  private final Map<String, Object> fields;

  @JsonCreator
  public Element(@JsonProperty("type") String type) {
    this.type = type;
    fields = new HashMap<>();
  }

  public Element(Builder builder) {
    this.type = builder.type;
    this.fields = builder.fields;
  }

  public String getType() {
    return this.type;
  }

  public Object get(String key) {
    return fields.get(key);
  }

  @JsonAnyGetter
  public Map<String, Object> getFields() {
    Map<String, Object> out = new HashMap<>(fields);
    out.put("type", type);
    return out;
  }

  @JsonAnySetter
  protected void put(String key, Object value) {
    if ("type".equals(key)) {
      throw new ElementException("CANNOT SET TYPE.");
    }
    if (value instanceof Number num) {
      fields.put(key, num.doubleValue());
    } else fields.put(key, value);
  }

  /**
   * Public mutator for element properties. Exists because {@code put()} is protected (reserved for
   * {@code @JsonAnySetter} deserialization).
   *
   * <p>Used by Processor to update symbol values at the raw-mapped stage, where elements are base
   * {@code Element} (not {@code PointElement}).
   *
   * <p>Note: Element lives in sdk/technical/ due to Jackson coupling but is effectively a domain
   * model — existing architectural debt.
   */
  public void set(String key, Object value) {
    put(key, value);
  }

  public void remove(String key) {
    fields.remove(key);
  }

  public List<Map.Entry<String, Object>> getEntries() {
    return fields.entrySet().stream().toList();
  }

  public String getRemarks() {
    return (String) get("remarks");
  }

  public void setRemarks(String remarks) {
    put("remarks", remarks);
  }

  public void setSpeed(Double speed) {
    put("speed", speed);
  }

  public Double getSpeed() {
    return (Double) get("speed");
  }

  public void setCourse(Double course) {
    put("course", course);
  }

  public Double getCourse() {
    return (Double) get("course");
  }

  public void setHeading(Double heading) {
    put("heading", heading);
  }

  public Double getHeading() {
    return (Double) get("heading");
  }

  public void setHae(Double hae) {
    put("hae", hae);
  }

  public Double getHae() {
    return (Double) get("hae");
  }

  public static class Builder {
    protected String type;
    private final Map<String, Object> fields = new HashMap<>();

    public Builder type(ElementTypes type) {
      this.type = type.getType();
      return this;
    }

    public Builder remarks(String remarks) {
      put("remarks", remarks);
      return this;
    }

    public Builder speed(Double speed) {
      put("speed", speed);
      return this;
    }

    public Builder course(Double course) {
      put("course", course);
      return this;
    }

    public Builder heading(Double heading) {
      put("heading", heading);
      return this;
    }

    public Builder hae(Double hae) {
      put("hae", hae);
      return this;
    }

    public Builder put(String key, Object value) {
      this.fields.put(key, value);
      return this;
    }

    public Element build() {
      return new Element(this);
    }
  }
}
