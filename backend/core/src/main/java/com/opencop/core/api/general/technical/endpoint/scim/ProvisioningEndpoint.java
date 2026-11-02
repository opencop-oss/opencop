// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.scim;

import com.opencop.core.api.general.business.usecase.user.ScimUserMutationUsecase;
import com.opencop.core.api.general.ports.gateway.repository.UserPreferenceRepository;
import com.opencop.core.api.general.technical.gateway.scim.ScimGroupService;
import com.opencop.core.api.general.technical.gateway.scim.ScimUserService;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.types.AttributeDefinition;
import com.unboundid.scim2.common.types.AuthenticationScheme;
import com.unboundid.scim2.common.types.BulkConfig;
import com.unboundid.scim2.common.types.ChangePasswordConfig;
import com.unboundid.scim2.common.types.ETagConfig;
import com.unboundid.scim2.common.types.FilterConfig;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.types.Meta;
import com.unboundid.scim2.common.types.PatchConfig;
import com.unboundid.scim2.common.types.ResourceTypeResource;
import com.unboundid.scim2.common.types.SchemaResource;
import com.unboundid.scim2.common.types.ServiceProviderConfigResource;
import com.unboundid.scim2.common.types.SortConfig;
import com.unboundid.scim2.common.types.UserResource;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/scim/v1/provisioning")
public class ProvisioningEndpoint {

  public static final String SCIM_JSON = "application/scim+json";

  private final ScimUserService scimUser;
  private final ScimUserMutationUsecase scimUserMutation;
  private final ScimGroupService scimGroup;
  private final UserPreferenceRepository preferenceRepository;

  ProvisioningEndpoint(
      ScimUserService scimUser,
      ScimUserMutationUsecase scimUserMutation,
      ScimGroupService scimGroup,
      UserPreferenceRepository preferenceRepository) {
    this.scimUser = scimUser;
    this.scimUserMutation = scimUserMutation;
    this.scimGroup = scimGroup;
    this.preferenceRepository = preferenceRepository;
  }

  @PostMapping(value = "/Users", consumes = SCIM_JSON, produces = SCIM_JSON)
  public ResponseEntity<UserResource> createUser(@RequestBody UserResource body) {
    var created = Objects.requireNonNull(withUserMeta(scimUserMutation.create(body)));
    var userId = UUID.fromString(created.getId());
    if (preferenceRepository.findByUserId(userId).locale() == null) {
      preferenceRepository.create(userId);
    }

    return ResponseEntity.created(created.getMeta().getLocation())
        .contentType(MediaType.valueOf(SCIM_JSON))
        .body(created);
  }

  @PostMapping(value = "/Groups", consumes = SCIM_JSON, produces = SCIM_JSON)
  public ResponseEntity<GroupResource> createGroup(@RequestBody GroupResource group) {
    GroupResource createdGroup = withGroupMeta(scimGroup.createGroup(group));

    return ResponseEntity.created(createdGroup.getMeta().getLocation())
        .contentType(MediaType.valueOf(SCIM_JSON))
        .body(createdGroup);
  }

  @GetMapping(path = "/Users/{id}", produces = SCIM_JSON)
  public ResponseEntity<UserResource> getUser(@PathVariable("id") UUID id) {
    return ResponseEntity.ok()
        .contentType(MediaType.valueOf(SCIM_JSON))
        .body(withUserMeta(scimUser.getById(id)));
  }

  @GetMapping(path = "/Groups/{id}", produces = SCIM_JSON)
  public ResponseEntity<GroupResource> getGroup(@PathVariable("id") UUID id) {
    return ResponseEntity.ok()
        .contentType(MediaType.valueOf(SCIM_JSON))
        .body(withGroupMeta(scimGroup.getGroup(id)));
  }

  @GetMapping(path = "/Users", produces = SCIM_JSON)
  public ResponseEntity<ListResponse<UserResource>> listUsers(
      @RequestParam(value = "filter", required = false) String filter,
      @RequestParam(value = "startIndex", required = false, defaultValue = "1") int startIndex,
      @RequestParam(value = "count", required = false, defaultValue = "100") int count) {

    int safeStartIndex = Math.max(1, startIndex);
    int safeCount = count <= 0 ? 100 : Math.min(count, 200);

    Page<UserResource> page = scimUser.list(filter, safeStartIndex, safeCount);

    List<UserResource> resources = page.getContent().stream().map(this::withUserMeta).toList();

    var resp =
        new ListResponse<>(
            (int) page.getTotalElements(), resources, safeStartIndex, page.getNumberOfElements());

    return ResponseEntity.ok().contentType(MediaType.valueOf(SCIM_JSON)).body(resp);
  }

  @GetMapping(path = "/Groups", produces = SCIM_JSON)
  public ResponseEntity<ListResponse<GroupResource>> listGroups(
      @RequestParam(value = "filter", required = false) String filter,
      @RequestParam(value = "startIndex", required = false, defaultValue = "1") int startIndex,
      @RequestParam(value = "count", required = false, defaultValue = "100") int count) {

    Page<GroupResource> page = scimGroup.listGroups(filter, startIndex, count);

    List<GroupResource> resources = page.getContent().stream().map(this::withGroupMeta).toList();

    var resp =
        new ListResponse<>(
            (int) page.getTotalElements(),
            resources,
            Math.max(1, startIndex),
            page.getNumberOfElements());

    return ResponseEntity.ok().contentType(MediaType.valueOf(SCIM_JSON)).body(resp);
  }

  @PutMapping(value = "/Users/{id}", consumes = SCIM_JSON, produces = SCIM_JSON)
  public ResponseEntity<UserResource> replaceUser(
      @PathVariable String id, @RequestBody UserResource request) {
    var replaced = scimUserMutation.replace(id, request);
    return ResponseEntity.ok()
        .contentType(MediaType.valueOf(SCIM_JSON))
        .body(withUserMeta(replaced));
  }

  @PutMapping(value = "/Groups/{id}", consumes = SCIM_JSON, produces = SCIM_JSON)
  public ResponseEntity<GroupResource> replaceGroup(
      @PathVariable String id, @RequestBody GroupResource group) {
    return ResponseEntity.ok()
        .contentType(MediaType.valueOf(SCIM_JSON))
        .body(withGroupMeta(scimGroup.updateGroup(id, group)));
  }

  @PatchMapping(path = "/Users/{id}", consumes = SCIM_JSON, produces = SCIM_JSON)
  public ResponseEntity<UserResource> patchUser(
      @PathVariable("id") UUID id, @RequestBody PatchRequest patch) {
    var patched = scimUserMutation.patch(id, patch);
    return ResponseEntity.ok()
        .contentType(MediaType.valueOf(SCIM_JSON))
        .body(withUserMeta(patched));
  }

  @PatchMapping(path = "/Groups/{id}", consumes = SCIM_JSON, produces = SCIM_JSON)
  public ResponseEntity<GroupResource> patchGroup(
      @PathVariable("id") UUID id, @RequestBody PatchRequest patch) {
    return ResponseEntity.ok()
        .contentType(MediaType.valueOf(SCIM_JSON))
        .body(withGroupMeta(scimGroup.patchGroup(id, patch)));
  }

  @DeleteMapping(path = "/Users/{id}", produces = SCIM_JSON)
  public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
    scimUserMutation.delete(id);
    return ResponseEntity.noContent().build(); // 204
  }

  @DeleteMapping(path = "/Groups/{id}", produces = SCIM_JSON)
  public ResponseEntity<Void> deleteGroup(@PathVariable("id") String id) {
    scimGroup.deleteGroup(id);
    return ResponseEntity.noContent().build(); // 204
  }

  @GetMapping(value = "/ServiceProviderConfig", produces = SCIM_JSON)
  public ResponseEntity<ServiceProviderConfigResource> serviceProviderConfig() {
    // Support PATCH semantics (recommended for SCIM)
    PatchConfig patch = new PatchConfig(true);
    BulkConfig bulk = new BulkConfig(false, 0, 0);
    FilterConfig filter = new FilterConfig(true, 200);
    ChangePasswordConfig changePassword = new ChangePasswordConfig(false);
    // If we start supporting Sorting, set to true
    SortConfig sort = new SortConfig(false);
    ETagConfig etag = new ETagConfig(false);

    // We are using a static bearer token, we should change this config in case we change the auth
    AuthenticationScheme bearer =
        new AuthenticationScheme(
            "httpbearer",
            "Authentication scheme using the HTTP Bearer token Authorization header",
            URI.create("https://www.rfc-editor.org/rfc/rfc6750"),
            null,
            "bearer",
            true);

    ServiceProviderConfigResource cfg =
        new ServiceProviderConfigResource(
            null, patch, bulk, filter, changePassword, sort, etag, null, List.of(bearer));

    return ResponseEntity.ok().contentType(MediaType.valueOf(SCIM_JSON)).body(cfg);
  }

  @GetMapping(value = "/ResourceTypes", produces = SCIM_JSON)
  public ResponseEntity<ListResponse<ResourceTypeResource>> resourceTypes() {
    var userRt =
        new ResourceTypeResource(
            "User",
            "User",
            "User Account",
            URI.create("/scim/v1/provisioning/Users"),
            URI.create("urn:ietf:params:scim:schemas:core:2.0:User"),
            List.of());
    userRt.setMeta(
        new Meta()
            .setResourceType("ResourceType")
            .setLocation(URI.create("/scim/v1/provisioning/ResourceTypes/User")));

    var groupRt =
        new ResourceTypeResource(
            "Group",
            "Group",
            "Group",
            URI.create("/scim/v1/provisioning/Groups"),
            URI.create("urn:ietf:params:scim:schemas:core:2.0:Group"),
            List.of());
    groupRt.setMeta(
        new Meta()
            .setResourceType("ResourceType")
            .setLocation(URI.create("/scim/v1/provisioning/ResourceTypes/Group")));

    var resp = new ListResponse<>(2, List.of(userRt, groupRt), 1, 2);

    return ResponseEntity.ok().contentType(MediaType.valueOf(SCIM_JSON)).body(resp);
  }

  @GetMapping(value = "/ResourceTypes/{id}", produces = SCIM_JSON)
  public ResponseEntity<ResourceTypeResource> resourceType(@PathVariable String id) {
    if ("User".equalsIgnoreCase(id)) {
      var userRt =
          new ResourceTypeResource(
              "User",
              "User",
              "User Account",
              URI.create("/scim/v1/provisioning/Users"),
              URI.create("urn:ietf:params:scim:schemas:core:2.0:User"),
              List.of());
      userRt.setMeta(
          new Meta()
              .setResourceType("ResourceType")
              .setLocation(URI.create("/scim/v1/provisioning/ResourceTypes/User")));
      return ResponseEntity.ok().contentType(MediaType.valueOf(SCIM_JSON)).body(userRt);
    }

    if ("Group".equalsIgnoreCase(id)) {
      var groupRt =
          new ResourceTypeResource(
              "Group",
              "Group",
              "Group",
              URI.create("/scim/v1/provisioning/Groups"),
              URI.create("urn:ietf:params:scim:schemas:core:2.0:Group"),
              List.of());
      groupRt.setMeta(
          new Meta()
              .setResourceType("ResourceType")
              .setLocation(URI.create("/scim/v1/provisioning/ResourceTypes/Group")));
      return ResponseEntity.ok().contentType(MediaType.valueOf(SCIM_JSON)).body(groupRt);
    }

    return ResponseEntity.notFound().build();
  }

  @GetMapping(value = "/Schemas", produces = SCIM_JSON)
  public ResponseEntity<ListResponse<SchemaResource>> schemas() {
    SchemaResource userSchema = userSchema();
    SchemaResource groupSchema = groupSchema();

    var resp = new ListResponse<>(2, List.of(userSchema, groupSchema), 1, 2);

    return ResponseEntity.ok().contentType(MediaType.valueOf(SCIM_JSON)).body(resp);
  }

  @GetMapping(value = "/Schemas/{id}", produces = SCIM_JSON)
  public ResponseEntity<SchemaResource> schemaById(@PathVariable String id) {
    boolean isUser =
        "urn:ietf:params:scim:schemas:core:2.0:User".equalsIgnoreCase(id)
            || "User".equalsIgnoreCase(id);

    boolean isGroup =
        "urn:ietf:params:scim:schemas:core:2.0:Group".equalsIgnoreCase(id)
            || "Group".equalsIgnoreCase(id);

    if (isUser) {
      return ResponseEntity.ok().contentType(MediaType.valueOf(SCIM_JSON)).body(userSchema());
    }
    if (isGroup) {
      return ResponseEntity.ok().contentType(MediaType.valueOf(SCIM_JSON)).body(groupSchema());
    }

    return ResponseEntity.notFound().build();
  }

  private SchemaResource userSchema() {
    var userName =
        new AttributeDefinition.Builder()
            .setName("userName")
            .setType(AttributeDefinition.Type.STRING)
            .setDescription("Unique identifier for the User (login).")
            .setRequired(true)
            .setCaseExact(false)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .setUniqueness(AttributeDefinition.Uniqueness.SERVER) // userName must be unique
            .build();

    var externalId =
        new AttributeDefinition.Builder()
            .setName("externalId")
            .setType(AttributeDefinition.Type.STRING)
            .setDescription(
                "Identifier of the resource as defined by the provisioning client (IdP).")
            .setRequired(false)
            .setCaseExact(true)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .setUniqueness(AttributeDefinition.Uniqueness.NONE)
            .build();

    var displayName =
        new AttributeDefinition.Builder()
            .setName("displayName")
            .setType(AttributeDefinition.Type.STRING)
            .setDescription("The name of the User, suitable for display.")
            .setRequired(false)
            .setCaseExact(false)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .build();

    var active =
        new AttributeDefinition.Builder()
            .setName("active")
            .setType(AttributeDefinition.Type.BOOLEAN)
            .setDescription("Indicates the User's administrative status.")
            .setRequired(false)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .build();

    // name (complex) with givenName/familyName
    var givenName =
        new AttributeDefinition.Builder()
            .setName("givenName")
            .setType(AttributeDefinition.Type.STRING)
            .setDescription("The given name of the User.")
            .setRequired(false)
            .setCaseExact(false)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .build();

    var familyName =
        new AttributeDefinition.Builder()
            .setName("familyName")
            .setType(AttributeDefinition.Type.STRING)
            .setDescription("The family name of the User.")
            .setRequired(false)
            .setCaseExact(false)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .build();

    var name =
        new AttributeDefinition.Builder()
            .setName("name")
            .setType(AttributeDefinition.Type.COMPLEX)
            .setDescription("The components of the user's real name.")
            .setRequired(false)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .addSubAttributes(givenName, familyName)
            .build();

    // emails (multi-valued complex): value, primary
    var emailValue =
        new AttributeDefinition.Builder()
            .setName("value")
            .setType(AttributeDefinition.Type.STRING)
            .setDescription("Email address.")
            .setRequired(false)
            .setCaseExact(false)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .build();

    var emailPrimary =
        new AttributeDefinition.Builder()
            .setName("primary")
            .setType(AttributeDefinition.Type.BOOLEAN)
            .setDescription("True if this is the primary email.")
            .setRequired(false)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .build();

    var emails =
        new AttributeDefinition.Builder()
            .setName("emails")
            .setType(AttributeDefinition.Type.COMPLEX)
            .setMultiValued(true)
            .setDescription("Email addresses for the user.")
            .setRequired(false)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .addSubAttributes(emailValue, emailPrimary)
            .build();

    var groups =
        new AttributeDefinition.Builder()
            .setName("groups")
            .setType(AttributeDefinition.Type.COMPLEX)
            .setMultiValued(true)
            .setDescription("The groups the user belongs to.")
            .setRequired(false)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .build();

    return new SchemaResource(
        "urn:ietf:params:scim:schemas:core:2.0:User",
        "User",
        "Core User",
        List.of(userName, externalId, displayName, active, name, emails, groups));
  }

  private SchemaResource groupSchema() {
    var displayName =
        new AttributeDefinition.Builder()
            .setName("displayName")
            .setType(AttributeDefinition.Type.STRING)
            .setDescription("A human-readable name for the Group.")
            .setRequired(true)
            .setCaseExact(false)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .build();

    var memberValue =
        new AttributeDefinition.Builder()
            .setName("value")
            .setType(AttributeDefinition.Type.STRING)
            .setDescription("Identifier of the member of this Group.")
            .setRequired(false)
            .setCaseExact(true)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .build();

    var memberRef =
        new AttributeDefinition.Builder()
            .setName("$ref")
            .setType(AttributeDefinition.Type.REFERENCE)
            .setDescription("URI of the member resource.")
            .setRequired(false)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .build();

    var memberDisplay =
        new AttributeDefinition.Builder()
            .setName("display")
            .setType(AttributeDefinition.Type.STRING)
            .setDescription("Display name of the member.")
            .setRequired(false)
            .setCaseExact(false)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .build();

    var memberType =
        new AttributeDefinition.Builder()
            .setName("type")
            .setType(AttributeDefinition.Type.STRING)
            .setDescription("Type of the member resource (e.g., 'User').")
            .setRequired(false)
            .setCaseExact(false)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .build();

    var members =
        new AttributeDefinition.Builder()
            .setName("members")
            .setType(AttributeDefinition.Type.COMPLEX)
            .setMultiValued(true)
            .setDescription("Members of the Group.")
            .setRequired(false)
            .setMutability(AttributeDefinition.Mutability.READ_WRITE)
            .setReturned(AttributeDefinition.Returned.DEFAULT)
            .addSubAttributes(memberValue, memberRef, memberDisplay, memberType)
            .build();

    return new SchemaResource(
        "urn:ietf:params:scim:schemas:core:2.0:Group",
        "Group",
        "Core Group",
        List.of(displayName, members));
  }

  private URI userLocation(String id) {
    return ServletUriComponentsBuilder.fromCurrentContextPath()
        .path("/scim/v1/provisioning/Users/{id}")
        .buildAndExpand(id)
        .toUri();
  }

  private URI groupLocation(String id) {
    return ServletUriComponentsBuilder.fromCurrentContextPath()
        .path("/scim/v1/provisioning/Groups/{id}")
        .buildAndExpand(id)
        .toUri();
  }

  private UserResource withUserMeta(UserResource userResource) {
    if (userResource == null || userResource.getId() == null) {
      return userResource;
    }

    Meta meta = userResource.getMeta() != null ? userResource.getMeta() : new Meta();
    meta.setResourceType("User");
    meta.setLocation(userLocation(userResource.getId()));
    userResource.setMeta(meta);

    return userResource;
  }

  private GroupResource withGroupMeta(GroupResource groupResource) {
    if (groupResource == null || groupResource.getId() == null) {
      return groupResource;
    }

    Meta meta = groupResource.getMeta() != null ? groupResource.getMeta() : new Meta();
    meta.setResourceType("Group");
    meta.setLocation(groupLocation(groupResource.getId()));
    groupResource.setMeta(meta);

    return groupResource;
  }
}
