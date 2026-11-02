// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.banner;

import com.opencop.core.api.general.technical.model.banner.BannerFormat;
import com.opencop.core.api.general.technical.model.banner.BannerKind;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "banner")
public class BannerEntity {

  @Id @UuidGenerator private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  // Where/how the banner is used in the app (HEADER)
  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, columnDefinition = "banner_kind")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private BannerKind kind = BannerKind.HEADER;

  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  // Optional: when the banner should start being visible
  @Column(name = "starts_at")
  private Instant startsAt;

  // Optional: when the banner should stop being visible
  @Column(name = "ends_at")
  private Instant endsAt;

  // Styling hint for frontend ("#FFAA00" or "warning")
  @Column(name = "color")
  private String color;

  // Text color hint for frontend (defaults to white hex)
  @Column(name = "text_color")
  private String textColor = "#FFFFFF";

  // How content should be rendered (plain, markdown, html)
  @Enumerated(EnumType.STRING)
  @Column(name = "format", nullable = false, columnDefinition = "banner_format")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private BannerFormat format = BannerFormat.PLAIN;

  // Whether users can dismiss/close the banner
  @Column(name = "dismissible", nullable = false)
  private boolean dismissible = true;

  // Optional: clicking banner opens this URL
  @Column(name = "link_url")
  private String linkUrl;

  // If multiple banners are active, higher priority shows first
  @Column(name = "priority", nullable = false)
  private int priority = 0;

  // Created timestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  // Updated timestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  // i18n translations for this banner
  @OneToMany(
      mappedBy = "banner",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<BannerTranslationEntity> translations = new ArrayList<>();

  public BannerEntity() {}

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  // --- getters/setters ---
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public BannerKind getKind() {
    return kind;
  }

  public void setKind(BannerKind kind) {
    this.kind = kind;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(Instant startsAt) {
    this.startsAt = startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public void setEndsAt(Instant endsAt) {
    this.endsAt = endsAt;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public String getTextColor() {
    return textColor;
  }

  public void setTextColor(String textColor) {
    this.textColor = textColor;
  }

  public BannerFormat getFormat() {
    return format;
  }

  public void setFormat(BannerFormat format) {
    this.format = format;
  }

  public boolean isDismissible() {
    return dismissible;
  }

  public void setDismissible(boolean dismissible) {
    this.dismissible = dismissible;
  }

  public String getLinkUrl() {
    return linkUrl;
  }

  public void setLinkUrl(String linkUrl) {
    this.linkUrl = linkUrl;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public List<BannerTranslationEntity> getTranslations() {
    return translations;
  }

  public void setTranslations(List<BannerTranslationEntity> translations) {
    this.translations = translations;
  }

  // Convenience: keep both sides in sync
  public void addTranslation(BannerTranslationEntity t) {
    translations.add(t);
    t.setBanner(this);
  }

  public void removeTranslation(BannerTranslationEntity t) {
    translations.remove(t);
    t.setBanner(null);
  }
}
