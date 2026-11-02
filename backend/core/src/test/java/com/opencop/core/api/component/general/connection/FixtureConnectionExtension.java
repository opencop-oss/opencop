// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.connection;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.connection.extension.AccessLevel;
import com.opencop.core.sdk.business.model.integration.connection.extension.ConnectionRecord;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExpectedRevision;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionBody;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionMethod;
import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionCapability;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionContext;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionException;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionFailure;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionOperation;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * A test-only extension on the generic test integration, standing in for an integration's own: it
 * echoes what the host handed it, keeps records through the host's store, runs the test's script,
 * and reports its delete hook to a probe, so a test sees the request reach the extension intact,
 * its records stay confined, and its connection's deletion released.
 */
record FixtureConnectionExtension(
    IntegrationConnectionSchema schema,
    String id,
    DeleteHookProbe probe,
    ExtensionScript script,
    boolean failsOnDelete)
    implements ConnectionExtensionCapability<NoConnectionSettings> {

  static final String ID = "fixture-sync";
  static final String OTHER_ID = "fixture-other";
  static final String MISSING_ITEM = "missing";
  static final String MISSING_ITEM_CODE = "FIXTURE_ITEM_NOT_FOUND";
  static final String MISSING_RECORD_CODE = "FIXTURE_RECORD_NOT_FOUND";
  static final String COLLECTION = "entries";
  static final String REVISION_PARAMETER = "revision";

  @Override
  public Class<NoConnectionSettings> settingsType() {
    return NoConnectionSettings.class;
  }

  @Override
  public ExtensionDescriptor descriptor() {
    return new ExtensionDescriptor("Fixture sync", "Test-only extension");
  }

  @Override
  public List<ExtensionOperation<NoConnectionSettings, ?, ?>> operations() {
    return Stream.of(itemOperations(), recordOperations(), secretOperations(), scriptOperations())
        .flatMap(List::stream)
        .toList();
  }

  /** Echo what the host handed them, or fail as the extension would. */
  private List<ExtensionOperation<NoConnectionSettings, ?, ?>> itemOperations() {
    return List.of(
        ExtensionOperation.<NoConnectionSettings, Void, Item>of(
            ExtensionMethod.GET,
            "/items/{name}",
            AccessLevel.READ,
            ExtensionBody.none(),
            ExtensionBody.json(Item.class),
            (context, request) -> {
              var name = request.pathVariable("name");
              if (MISSING_ITEM.equals(name)) {
                throw new ConnectionExtensionException(
                    ExtensionFailure.NOT_FOUND, MISSING_ITEM_CODE, "No item " + name);
              }
              return new Item(name, null, context.sourceId(), context.actingUserId());
            }),
        ExtensionOperation.<NoConnectionSettings, ItemChange, Item>of(
            ExtensionMethod.PUT,
            "/items/{name}",
            AccessLevel.MANAGE,
            ExtensionBody.json(ItemChange.class),
            ExtensionBody.json(Item.class),
            (context, request) ->
                new Item(
                    request.pathVariable("name"),
                    request.body().label(),
                    context.sourceId(),
                    context.actingUserId())),
        ExtensionOperation.<NoConnectionSettings, Void, Void>of(
            ExtensionMethod.DELETE,
            "/items/{name}",
            AccessLevel.MANAGE,
            ExtensionBody.none(),
            ExtensionBody.none(),
            (context, request) -> null));
  }

  /** Keep records through the host's store. */
  private List<ExtensionOperation<NoConnectionSettings, ?, ?>> recordOperations() {
    return List.of(
        ExtensionOperation.<NoConnectionSettings, Void, RecordView>of(
            ExtensionMethod.GET,
            "/records/{key}",
            AccessLevel.READ,
            ExtensionBody.none(),
            ExtensionBody.json(RecordView.class),
            (context, request) ->
                context
                    .records()
                    .get(COLLECTION, request.pathVariable("key"))
                    .map(RecordView::of)
                    .orElseThrow(
                        () ->
                            new ConnectionExtensionException(
                                ExtensionFailure.NOT_FOUND, MISSING_RECORD_CODE, "No record"))),
        ExtensionOperation.<NoConnectionSettings, Void, RecordList>of(
            ExtensionMethod.GET,
            "/records",
            AccessLevel.READ,
            ExtensionBody.none(),
            ExtensionBody.json(RecordList.class),
            (context, request) ->
                new RecordList(
                    context.records().list(COLLECTION).stream().map(RecordView::of).toList())),
        ExtensionOperation.<NoConnectionSettings, RecordWrite, RecordView>of(
            ExtensionMethod.PUT,
            "/records/{key}",
            AccessLevel.MANAGE,
            ExtensionBody.json(RecordWrite.class),
            ExtensionBody.json(RecordView.class),
            (context, request) ->
                RecordView.of(
                    context
                        .records()
                        .put(
                            COLLECTION,
                            request.pathVariable("key"),
                            request.body().document(),
                            request.body().expected()))),
        ExtensionOperation.<NoConnectionSettings, Void, Void>of(
            ExtensionMethod.DELETE,
            "/records/{key}",
            AccessLevel.MANAGE,
            ExtensionBody.none(),
            ExtensionBody.none(),
            (context, request) -> {
              context.records().delete(COLLECTION, request.pathVariable("key"), expected(request));
              return null;
            }));
  }

  /** Keep the extension's secret entry through the host. */
  private List<ExtensionOperation<NoConnectionSettings, ?, ?>> secretOperations() {
    return List.of(
        ExtensionOperation.<NoConnectionSettings, SecretWrite, Void>of(
            ExtensionMethod.PUT,
            "/secret",
            AccessLevel.MANAGE,
            ExtensionBody.json(SecretWrite.class),
            ExtensionBody.none(),
            (context, request) -> {
              context.secrets().entry().replace(request.body().secrets());
              return null;
            }),
        ExtensionOperation.<NoConnectionSettings, Void, Void>of(
            ExtensionMethod.DELETE,
            "/secret",
            AccessLevel.MANAGE,
            ExtensionBody.none(),
            ExtensionBody.none(),
            (context, request) -> {
              context.secrets().entry().delete();
              return null;
            }),
        ExtensionOperation.<NoConnectionSettings, Void, SecretKeys>of(
            ExtensionMethod.GET,
            "/secret",
            AccessLevel.READ,
            ExtensionBody.none(),
            ExtensionBody.json(SecretKeys.class),
            (context, request) ->
                new SecretKeys(
                    context.secrets().entry().read().keySet().stream().sorted().toList())));
  }

  /** Run the test's script. */
  private List<ExtensionOperation<NoConnectionSettings, ?, ?>> scriptOperations() {
    return List.of(
        ExtensionOperation.<NoConnectionSettings, Void, Void>of(
            ExtensionMethod.POST,
            "/script",
            AccessLevel.MANAGE,
            ExtensionBody.none(),
            ExtensionBody.none(),
            (context, request) -> {
              script.run(context);
              return null;
            }));
  }

  /** Records the call, then fails when this fixture stands for an extension whose release fails. */
  @Override
  public void afterConnectionDeleted(ConnectionExtensionContext<NoConnectionSettings> context)
      throws ConnectionExtensionException {
    probe.record(new DeleteHookProbe.Call(id, context.sourceId(), context.actingUserId()));
    if (failsOnDelete) {
      throw new ConnectionExtensionException(
          ExtensionFailure.UPSTREAM_FAILURE, "The remote system refused the release");
    }
  }

  private static ExpectedRevision expected(ExtensionRequest<?> request) {
    var revision = request.query().get(REVISION_PARAMETER);
    return revision == null
        ? ExpectedRevision.any()
        : ExpectedRevision.of(Long.parseLong(revision.getFirst()));
  }

  record ItemChange(String label) {}

  record Item(String name, String label, UUID sourceId, UUID actingUserId) {}

  /**
   * @param create the record must not exist yet
   * @param revision the revision the record must be at, or null for whatever it is
   */
  record RecordWrite(Map<String, Object> document, boolean create, Long revision) {

    ExpectedRevision expected() {
      if (create) {
        return ExpectedRevision.absent();
      }
      return revision == null ? ExpectedRevision.any() : ExpectedRevision.of(revision);
    }
  }

  record RecordView(
      String key, Map<String, Object> document, long revision, UUID createdBy, UUID updatedBy) {

    static RecordView of(ConnectionRecord record) {
      return new RecordView(
          record.key(),
          record.document(),
          record.revision(),
          record.createdBy(),
          record.updatedBy());
    }
  }

  record RecordList(List<RecordView> records) {}

  record SecretWrite(Map<String, String> secrets) {}

  /** Only the names: the fixture never hands a secret back. */
  record SecretKeys(List<String> keys) {}
}
