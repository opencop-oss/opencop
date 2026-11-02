// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.integrationcatalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.opencop.core.api.general.business.model.integrationcatalog.CanonicalUuidOrder;
import com.opencop.core.api.general.business.model.integrationcatalog.ConnectionFraming;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetup;
import com.opencop.core.api.general.business.model.integrationcatalog.ProtocolConnectionSetup;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorFieldKind;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.FileField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.FileListField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.SecretField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.TextField;
import com.opencop.core.sdk.business.model.integration.connection.mapping.EgressMapperRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.FramingRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingPair;
import com.opencop.core.sdk.business.model.profile.DelimiterModes;
import com.opencop.core.sdk.model.datasource.AccessMode;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Wire shape of one integration schema version's connection setup, one entry per protocol a
 * connection can be set up over.
 *
 * <p>Every name here is provider-neutral: a client renders this without hardcoded knowledge of any
 * integration, so no field, branch or type may name one.
 */
@Schema(
    name = "IntegrationConnectionSetupResponse",
    description =
        """
        Everything needed to render a connection-setup form for one integration schema \
        version, without hardcoded knowledge of the integration.

        Pick the entry of `protocols` whose `kind` is the transport being set up; each kind \
        carries only what that transport chooses. Render one form per entry in its \
        `extractors`, letting the operator pick which extractor they are supplying input for, \
        then render that extractor's `fields` in the order given. Choose the form component for \
        each field from its `kind`; see the field schema.

        `ingress` and `egress` are a choice set, not a decision. No mapper is resolved and no \
        version is pinned here, and the submitted choice is still checked against mapper \
        availability, archival state and access-mode eligibility.""")
public record IntegrationConnectionSetupResponse(
    @Schema(description = "Identity of the integration these choices belong to") UUID integrationId,
    @Schema(description = "Schema version of the integration; part of its composite identity")
        int schemaVersion,
    @Schema(
            description =
                "Every transport a connection can be set up over, at least one, in the order TCP,"
                    + " UDP, RTSP, KAFKA. A transport the integration does not offer is absent.")
        List<ProtocolSetupResponse> protocols) {

  /** Sorts a rule that carries no identifier on that side ahead of one that does. */
  private static final String NO_IDENTIFIER = "";

  private static final String INGRESS_MAPPER_DESCRIPTION =
      "Ingress mapper identity. Mapper versions have an independent lifecycle, so no version is"
          + " pinned here.";
  private static final String EXTRACTORS_DESCRIPTION =
      "Every extractor this runtime registers for the transport, in a stable order that does not"
          + " change between requests or restarts. At least one.";
  private static final String ACCESS_MODES_DESCRIPTION =
      "Access modes a connection over this transport may take, at least one, in the order R, W,"
          + " RW. Offer only these: any other is rejected on submission.";
  private static final String MAPPER_INGRESS_DESCRIPTION =
      "Rules for the ingress mapper, the one incoming messages are decoded with. A mapper is"
          + " permitted when any one rule permits it. At least one.";

  /** Orders TCP rules by type in declaration order, then by the identifiers they carry. */
  private static final Comparator<TcpIngressRule> TCP_RULE_ORDER =
      Comparator.comparingInt(IntegrationConnectionSetupResponse::rank)
          .thenComparing(IntegrationConnectionSetupResponse::mapperKey)
          .thenComparing(IntegrationConnectionSetupResponse::framingKey);

  static IntegrationConnectionSetupResponse from(IntegrationConnectionSetup setup) {
    var integrationId = setup.schema().integrationId();
    return new IntegrationConnectionSetupResponse(
        integrationId,
        setup.schema().schemaVersion(),
        setup.protocols().stream()
            .map(protocol -> ProtocolSetupResponse.from(integrationId, protocol))
            .toList());
  }

  private static List<UUID> sorted(Collection<UUID> ids) {
    return ids.stream().sorted(CanonicalUuidOrder.BY_CANONICAL_TEXT).toList();
  }

  private static List<ExtractorResponse> extractorResponses(
      UUID integrationId, ProtocolConnectionSetup setup) {
    return setup.extractors().stream()
        .map(extractor -> ExtractorResponse.from(integrationId, extractor))
        .toList();
  }

  private static List<TcpIngressRule> tcpIngress(MapperFramingDeclaration rule) {
    return rule.rules().stream()
        .map(IntegrationConnectionSetupResponse::tcpIngress)
        .sorted(TCP_RULE_ORDER)
        .toList();
  }

  private static TcpIngressRule tcpIngress(FramingRule rule) {
    return switch (rule) {
      case MapperFramingPair pair ->
          new TcpIngressRule.ExactPair(pair.mapperId(), pair.framingId());
      case FramingRule.AnyMapperWith any -> new TcpIngressRule.AnyMapperWith(any.framingId());
      case FramingRule.AnyMapperCustomFraming ignored ->
          new TcpIngressRule.AnyMapperCustomFraming();
    };
  }

  /** An open rule permits every mapper, so the mappers it also lists add nothing to it. */
  private static List<MapperIngressRule> mapperIngress(MapperDeclaration rule) {
    if (rule.open()) {
      return List.of(new MapperIngressRule.AnyMapper());
    }
    return sorted(rule.allowedMappers()).stream()
        .<MapperIngressRule>map(MapperIngressRule.Mapper::new)
        .toList();
  }

  private static int rank(TcpIngressRule rule) {
    return switch (rule) {
      case TcpIngressRule.ExactPair ignored -> 0;
      case TcpIngressRule.AnyMapperWith ignored -> 1;
      case TcpIngressRule.AnyMapperCustomFraming ignored -> 2;
    };
  }

  private static String mapperKey(TcpIngressRule rule) {
    return switch (rule) {
      case TcpIngressRule.ExactPair pair -> pair.mapperId().toString();
      case TcpIngressRule.AnyMapperWith ignored -> NO_IDENTIFIER;
      case TcpIngressRule.AnyMapperCustomFraming ignored -> NO_IDENTIFIER;
    };
  }

  private static String framingKey(TcpIngressRule rule) {
    return switch (rule) {
      case TcpIngressRule.ExactPair pair -> pair.framingId().toString();
      case TcpIngressRule.AnyMapperWith any -> any.framingId().toString();
      case TcpIngressRule.AnyMapperCustomFraming ignored -> NO_IDENTIFIER;
    };
  }

  /**
   * One transport's setup, discriminated by {@code kind} like a connection creation. Each kind
   * carries only what that transport chooses: only a TCP stream is framed and only TCP writes.
   */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = ProtocolSetupResponse.Tcp.class, name = "TCP"),
    @JsonSubTypes.Type(value = ProtocolSetupResponse.Udp.class, name = "UDP"),
    @JsonSubTypes.Type(value = ProtocolSetupResponse.Rtsp.class, name = "RTSP"),
    @JsonSubTypes.Type(value = ProtocolSetupResponse.Kafka.class, name = "KAFKA")
  })
  @Schema(
      name = "ProtocolSetupResponse",
      description = "What the integration accepts over one transport, discriminated by kind.",
      discriminatorProperty = "kind",
      oneOf = {
        ProtocolSetupResponse.Tcp.class,
        ProtocolSetupResponse.Udp.class,
        ProtocolSetupResponse.Rtsp.class,
        ProtocolSetupResponse.Kafka.class
      })
  public sealed interface ProtocolSetupResponse {

    List<AccessMode> accessModes();

    List<ExtractorResponse> extractors();

    static ProtocolSetupResponse from(UUID integrationId, ProtocolConnectionSetup setup) {
      return switch (setup) {
        case ProtocolConnectionSetup.Tcp tcp ->
            new Tcp(
                tcp.accessModes(),
                extractorResponses(integrationId, tcp),
                tcpIngress(tcp.rule()),
                tcp.rule().egress().map(EgressRuleResponse::from).orElse(null),
                tcp.framings().stream().map(FramingResponse::from).toList());
        case ProtocolConnectionSetup.Udp udp ->
            new Udp(
                udp.accessModes(),
                extractorResponses(integrationId, udp),
                mapperIngress(udp.rule()));
        case ProtocolConnectionSetup.Rtsp rtsp ->
            new Rtsp(
                rtsp.accessModes(),
                extractorResponses(integrationId, rtsp),
                mapperIngress(rtsp.rule()));
        case ProtocolConnectionSetup.Kafka kafka ->
            new Kafka(
                kafka.accessModes(),
                extractorResponses(integrationId, kafka),
                mapperIngress(kafka.rule()));
      };
    }

    /**
     * A TCP setup. {@code egress} is serialized as an explicit {@code null} rather than omitted, so
     * a client reads "cannot write" from the value instead of from a missing key.
     */
    @Schema(name = "TcpConnectionSetupResponse")
    record Tcp(
        @ArraySchema(arraySchema = @Schema(description = ACCESS_MODES_DESCRIPTION), minItems = 1)
            List<AccessMode> accessModes,
        @Schema(description = EXTRACTORS_DESCRIPTION) List<ExtractorResponse> extractors,
        @Schema(
                description =
                    "Rules for the ingress mapper, the one incoming payloads are decoded with, and"
                        + " the framing that splits the stream. A choice is permitted when any one"
                        + " rule permits it; rules do not cross-combine to permit further ones. At"
                        + " least one, ordered by type then by identifiers.")
            List<TcpIngressRule> ingress,
        @Schema(
                description =
                    "Rule for the egress mapper, the one outgoing messages are encoded with. Null"
                        + " when the integration cannot write, which leaves only read-only"
                        + " access.",
                // OpenAPI 3.1 states nullability as a type: `nullable` is ignored there.
                types = {"object", "null"})
            EgressRuleResponse egress,
        @Schema(
                description =
                    "Every framing the ingress rules reference, resolved, ascending by"
                        + " identifier. An integration provides its framings in code and no"
                        + " catalog records them, so these are the only way to tell what a"
                        + " framing identifier means.")
            List<FramingResponse> framings)
        implements ProtocolSetupResponse {}

    @Schema(name = "UdpConnectionSetupResponse")
    record Udp(
        @ArraySchema(arraySchema = @Schema(description = ACCESS_MODES_DESCRIPTION), minItems = 1)
            List<AccessMode> accessModes,
        @Schema(description = EXTRACTORS_DESCRIPTION) List<ExtractorResponse> extractors,
        @Schema(description = MAPPER_INGRESS_DESCRIPTION) List<MapperIngressRule> ingress)
        implements ProtocolSetupResponse {}

    @Schema(name = "RtspConnectionSetupResponse")
    record Rtsp(
        @ArraySchema(arraySchema = @Schema(description = ACCESS_MODES_DESCRIPTION), minItems = 1)
            List<AccessMode> accessModes,
        @Schema(description = EXTRACTORS_DESCRIPTION) List<ExtractorResponse> extractors,
        @Schema(description = MAPPER_INGRESS_DESCRIPTION) List<MapperIngressRule> ingress)
        implements ProtocolSetupResponse {}

    @Schema(name = "KafkaConnectionSetupResponse")
    record Kafka(
        @ArraySchema(arraySchema = @Schema(description = ACCESS_MODES_DESCRIPTION), minItems = 1)
            List<AccessMode> accessModes,
        @Schema(description = EXTRACTORS_DESCRIPTION) List<ExtractorResponse> extractors,
        @Schema(description = MAPPER_INGRESS_DESCRIPTION) List<MapperIngressRule> ingress)
        implements ProtocolSetupResponse {}
  }

  /** One TCP ingress rule, discriminated by {@code type}. */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = TcpIngressRule.ExactPair.class, name = "EXACT_PAIR"),
    @JsonSubTypes.Type(value = TcpIngressRule.AnyMapperWith.class, name = "ANY_MAPPER_WITH"),
    @JsonSubTypes.Type(
        value = TcpIngressRule.AnyMapperCustomFraming.class,
        name = "ANY_MAPPER_CUSTOM_FRAMING")
  })
  @Schema(
      name = "TcpIngressRule",
      description =
          """
          One TCP ingress rule, discriminated by `type`.

          - `EXACT_PAIR`: the one mapper/framing combination given. Submit both together.
          - `ANY_MAPPER_WITH`: any eligible mapper with the framing given.
          - `ANY_MAPPER_CUSTOM_FRAMING`: any eligible mapper with a framing the operator \
            supplies, as a mode and a delimiter.""",
      discriminatorProperty = "type",
      oneOf = {
        TcpIngressRule.ExactPair.class,
        TcpIngressRule.AnyMapperWith.class,
        TcpIngressRule.AnyMapperCustomFraming.class
      })
  public sealed interface TcpIngressRule {

    @Schema(
        name = "ExactPairIngressRule",
        description = "One complete mapper/framing combination; neither half is meaningful alone.")
    record ExactPair(
        @Schema(description = INGRESS_MAPPER_DESCRIPTION) UUID mapperId,
        @Schema(description = "Framing identity, resolved in `framings`") UUID framingId)
        implements TcpIngressRule {}

    @Schema(
        name = "AnyMapperWithIngressRule",
        description = "Any eligible mapper with this framing. Widens only the mapper side.")
    record AnyMapperWith(
        @Schema(description = "Framing identity, resolved in `framings`") UUID framingId)
        implements TcpIngressRule {}

    @Schema(
        name = "AnyMapperCustomFramingIngressRule",
        description =
            "Any eligible mapper with a framing the operator supplies as a mode and a delimiter.")
    record AnyMapperCustomFraming() implements TcpIngressRule {}
  }

  /** One ingress rule of a transport that chooses a mapper alone, discriminated by {@code type}. */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = MapperIngressRule.AnyMapper.class, name = "ANY_MAPPER"),
    @JsonSubTypes.Type(value = MapperIngressRule.Mapper.class, name = "MAPPER")
  })
  @Schema(
      name = "MapperIngressRule",
      description =
          """
          One ingress rule of a transport that chooses a mapper alone, discriminated by `type`.

          - `ANY_MAPPER`: any eligible mapper. Never listed alongside `MAPPER` rules.
          - `MAPPER`: the mapper given, one rule per permitted mapper, ascending.""",
      discriminatorProperty = "type",
      oneOf = {MapperIngressRule.AnyMapper.class, MapperIngressRule.Mapper.class})
  public sealed interface MapperIngressRule {

    @Schema(name = "AnyMapperIngressRule", description = "Any eligible mapper.")
    record AnyMapper() implements MapperIngressRule {}

    @Schema(name = "ExactMapperIngressRule", description = "Exactly this mapper.")
    record Mapper(@Schema(description = INGRESS_MAPPER_DESCRIPTION) UUID mapperId)
        implements MapperIngressRule {}
  }

  @Schema(
      name = "EgressRuleResponse",
      description =
          "Which mapper may encode what a writable connection sends. Required for write and"
              + " read-write access.")
  public record EgressRuleResponse(
      @Schema(description = "Whether any eligible mapper is permitted") boolean anyMapper,
      @Schema(
              description =
                  "Mappers permitted by identity, ascending. Empty when `anyMapper` is true,"
                      + " which already permits every mapper.")
          List<UUID> allowedMappers) {

    static EgressRuleResponse from(EgressMapperRule rule) {
      return new EgressRuleResponse(
          rule.open(), rule.open() ? List.of() : sorted(rule.allowedMappers()));
    }
  }

  @Schema(
      name = "ExtractorResponse",
      description =
          """
          One way the integration accepts connection input, and therefore one form.

          Acceptance paths that differ in which fields exist are separate extractors, so a \
          client renders a chooser when more than one is returned and goes straight to the \
          single form when only one is.""")
  public record ExtractorResponse(
      @Schema(
              description =
                  "Opaque, stable identifier to send back when submitting input for this"
                      + " extractor. Not a display string: the label may be reworded without"
                      + " invalidating a stored selection.")
          UUID id,
      @Schema(description = "Display text for the form") String label,
      @Schema(description = "Translation key for `label`. Use `label` if there's no translation.")
          String labelKey,
      @Schema(description = "Input slots, in the order they should be rendered")
          List<ExtractorFieldResponse> fields) {

    static ExtractorResponse from(UUID integrationId, ExtractorDescriptor descriptor) {
      return new ExtractorResponse(
          descriptor.id(),
          descriptor.label(),
          IntegrationTextKeys.extractorLabel(integrationId, descriptor.id()),
          descriptor.fields().stream()
              .map(
                  field ->
                      ExtractorFieldResponse.from(
                          field,
                          IntegrationTextKeys.fieldLabel(
                              integrationId, descriptor.id(), field.name())))
              .toList());
    }
  }

  /**
   * One input slot. {@code extensions} and {@code mediaTypes} are absent for the scalar kinds,
   * which have no file format to declare, rather than rendered as empty lists; {@code standardKey}
   * is absent for a field that is not a standard one.
   */
  @Schema(
      name = "ExtractorFieldResponse",
      description =
          """
          One input slot, and the input a client uses to choose a form component for it.

          Select the component from `kind`:

          - `TEXT`: single-line text input, shown as typed.
          - `SECRET`: masked text input. Never echo the value back or log it.
          - `FILE`: file picker accepting exactly one file.
          - `FILE_LIST`: file picker accepting several files, all under this one name.

          Cardinality is not a separate attribute: `FILE_LIST` is the only multi-valued kind, \
          and whether the slot may be left empty is carried by `required`.

          For the file-bearing kinds, concatenate `extensions` and `mediaTypes` into a single \
          HTML `accept` list, which takes both forms in the same comma-separated value. They are \
          two flat sets rather than pairs because the mapping is not one-to-one: an extension \
          may be served under several media types, a media type may cover several extensions, \
          and some extensions have no registered media type at all. Both are omitted for the \
          scalar kinds rather than sent as empty lists.

          Everything declared here, formats included, is a **client hint only**. The \
          integration's own validation remains the sole authority on whether supplied input is \
          acceptable, and the checks it performs are not duplicated here. A field may therefore \
          be declared optional and still be rejected in combination with another, which no field \
          kind can express.""")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ExtractorFieldResponse(
      @Schema(
              description =
                  "Payload key the value is supplied under, which for the file-bearing kinds is"
                      + " also the multipart role. Case-sensitive.")
          String name,
      @Schema(description = "Display text for the field") String label,
      @Schema(
              description =
                  "Translation key for `label`. Use `label` if there's no translation. For a"
                      + " standard field, prefer the translation by `standardKey`.")
          String labelKey,
      @Schema(description = "Whether the field must be supplied") boolean required,
      @Schema(description = "What the field carries; selects the form component")
          ExtractorFieldKind kind,
      @Schema(
              description =
                  "Accepted filename extensions, lowercase and dot-prefixed. Present only for"
                      + " the file-bearing kinds, which always declare at least one.")
          Set<String> extensions,
      @Schema(
              description =
                  "Accepted media types. Absent when none are declared, which is always the case"
                      + " for the scalar kinds and may also be the case for a file-bearing one.")
          Set<String> mediaTypes,
      @Schema(
              description =
                  "Key of the shared field this is, such as `HOST` or `PORT`, present only for a"
                      + " standard field. A client localizes the field by this key and falls back"
                      + " to `label` without a translation.")
          StandardExtractorField standardKey) {

    /**
     * Switches over the sealed field hierarchy so adding a field kind fails to compile here rather
     * than silently serializing without its formats.
     */
    static ExtractorFieldResponse from(ExtractorField field, String labelKey) {
      return switch (field) {
        case TextField text -> scalar(text, labelKey);
        case SecretField secret -> scalar(secret, labelKey);
        case FileField file ->
            new ExtractorFieldResponse(
                file.name(),
                file.label(),
                labelKey,
                file.required(),
                file.kind(),
                file.extensions(),
                declared(file.mediaTypes()),
                file.standard().orElse(null));
        case FileListField files ->
            new ExtractorFieldResponse(
                files.name(),
                files.label(),
                labelKey,
                files.required(),
                files.kind(),
                files.extensions(),
                declared(files.mediaTypes()),
                files.standard().orElse(null));
      };
    }

    /**
     * Renders a declared-nothing set as absent rather than as an empty array, so a client reads one
     * rule: a missing key means no constraint. A file-bearing field may declare no media type at
     * all, and the scalar kinds have nowhere to declare one, so without this the same "nothing
     * declared" state would reach the client in two different shapes. Extensions need no such
     * treatment: a file-bearing field cannot be built without at least one.
     */
    private static Set<String> declared(Set<String> mediaTypes) {
      return mediaTypes.isEmpty() ? null : mediaTypes;
    }

    private static ExtractorFieldResponse scalar(ExtractorField field, String labelKey) {
      return new ExtractorFieldResponse(
          field.name(),
          field.label(),
          labelKey,
          field.required(),
          field.kind(),
          null,
          null,
          field.standard().orElse(null));
    }
  }

  @Schema(
      name = "FramingResponse",
      description =
          """
          How a TCP stream is split into messages.

          `mode` is what a client shows for the choice: `delimiterInclude` and `delimiterSplit` \
          split on a delimiter, keeping it or dropping it; `binary` reads length-prefixed \
          frames; `passthrough` treats what arrives as one message. The delimiter itself is not \
          returned: it is how the stream is split rather than something an operator picks.""")
  public record FramingResponse(
      @Schema(description = "The identifier an ingress rule references") UUID id,
      @Schema(description = "How the stream is split") DelimiterModes mode) {

    static FramingResponse from(ConnectionFraming framing) {
      return new FramingResponse(framing.id(), framing.mode());
    }
  }
}
