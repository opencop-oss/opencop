// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.scim.user;

import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "opencop_user",
    // Only the indexes actually created by Flyway (V1) are declared here; ddl-auto=validate does
    // not create indexes, so annotating ones the schema lacks would misrepresent it (R-05).
    indexes = {
      @Index(name = "idx_opencop_user_email", columnList = "email"),
      @Index(name = "idx_opencop_user_active", columnList = "active")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_user_idp_issuer_idp_user",
          columnNames = {"idp_issuer", "idp_user_id"}),
      @UniqueConstraint(
          name = "uk_user_idp_issuer_username",
          columnNames = {"idp_issuer", "idp_username"})
    })
public class InternalUserEntity {

  @Id
  @GeneratedValue
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "idp_user_id", nullable = false)
  private String idpUserId; // externalId (IDP UUID as string)

  @Column(name = "idp_username", nullable = false)
  private String idpUsername; // SCIM userName

  @Column(name = "idp_issuer", nullable = false)
  private String idpIssuer;

  @Column(name = "email")
  private String email;

  @Column(name = "username")
  private String username;

  @Column(name = "first_name")
  private String firstName;

  @Column(name = "last_name")
  private String lastName;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "administrator", nullable = false)
  private boolean administrator = false;

  @Column(name = "session_generation", nullable = false)
  private long sessionGeneration;

  @Column(name = "account_access_revision", nullable = false)
  private long accountAccessRevision;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @ManyToMany
  @JoinTable(
      name = "opencop_user_groups",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "group_id"))
  private List<InternalGroupEntity> groups;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getIdpUserId() {
    return idpUserId;
  }

  public void setIdpUserId(String idpUserId) {
    this.idpUserId = idpUserId;
  }

  public String getIdpUsername() {
    return idpUsername;
  }

  public void setIdpUsername(String idpUsername) {
    this.idpUsername = idpUsername;
  }

  public String getIdpIssuer() {
    return idpIssuer;
  }

  public void setIdpIssuer(String idpIssuer) {
    this.idpIssuer = idpIssuer;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public boolean isAdministrator() {
    return administrator;
  }

  public void setAdministrator(boolean administrator) {
    this.administrator = administrator;
  }

  public long getSessionGeneration() {
    return sessionGeneration;
  }

  public void setSessionGeneration(long sessionGeneration) {
    this.sessionGeneration = sessionGeneration;
  }

  public long getAccountAccessRevision() {
    return accountAccessRevision;
  }

  public void setAccountAccessRevision(long accountAccessRevision) {
    this.accountAccessRevision = accountAccessRevision;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }

  public List<InternalGroupEntity> getGroups() {
    return groups;
  }

  public void setGroups(List<InternalGroupEntity> groups) {
    this.groups = groups;
  }
}
