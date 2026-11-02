// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.connection.mapping.FramingRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingPair;
import com.opencop.core.sdk.business.model.integration.connection.setup.ResolvedTcpFraming;
import com.opencop.core.sdk.business.model.profile.DelimiterModes;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.model.connection.spec.transport.CustomTcpFraming;
import com.opencop.core.sdk.model.connection.spec.transport.IntegrationTcpFraming;
import com.opencop.core.sdk.model.connection.spec.transport.TcpFraming;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TcpMapperFramingPolicyTest {
  private static final MapperFramingPair FIRST =
      new MapperFramingPair(UUID.randomUUID(), UUID.randomUUID());
  private static final MapperFramingPair SECOND =
      new MapperFramingPair(UUID.randomUUID(), UUID.randomUUID());
  private static final MapperFramingDeclaration DECLARATION = MapperFramingDeclaration.of(FIRST);
  private static final TcpFraming CUSTOM =
      new CustomTcpFraming(DelimiterModes.DELIMITER_INCLUDE, "</event>");
  private static final ResolvedTcpFraming RESOLVED_CUSTOM =
      new ResolvedTcpFraming.Custom(DelimiterModes.DELIMITER_INCLUDE, "</event>");

  @Test
  void rejectsUnavailableOrArchivedCatalogSelectionsWithStableReasons() {
    var framing = framing(FIRST);

    assertReason(
        "MAPPER_NOT_FOUND",
        () -> TcpMapperFramingPolicy.assertValid(DECLARATION, FIRST.mapperId(), null, framing));
    assertReason(
        "MAPPER_ARCHIVED",
        () ->
            TcpMapperFramingPolicy.assertValid(
                DECLARATION, FIRST.mapperId(), mapper(FIRST.mapperId(), true), framing));
  }

  @Test
  void acceptsOnlyADeclaredPairOfMapperAndIntegrationFramingForANewSelection() {
    TcpMapperFramingPolicy.assertValid(
        DECLARATION, FIRST.mapperId(), mapper(FIRST.mapperId(), false), framing(FIRST));

    assertReason(
        "PAIR_NOT_ALLOWED",
        () ->
            TcpMapperFramingPolicy.assertValid(
                DECLARATION, SECOND.mapperId(), mapper(SECOND.mapperId(), false), framing(SECOND)));
    assertReason(
        "PAIR_NOT_ALLOWED",
        () ->
            TcpMapperFramingPolicy.assertValid(
                DECLARATION, FIRST.mapperId(), mapper(FIRST.mapperId(), false), CUSTOM));
  }

  @Test
  void reportsWhyAnExistingPairNoLongerMatchesItsDeclaration() {
    assertThat(
            TcpMapperFramingPolicy.existingMismatch(
                Optional.empty(), FIRST.mapperId(), framing(FIRST)))
        .hasToString("INTEGRATION_DECLARATION_NOT_FOUND");
    assertThat(
            TcpMapperFramingPolicy.existingMismatch(
                Optional.of(DECLARATION), SECOND.mapperId(), framing(SECOND)))
        .hasToString("PAIR_NOT_ALLOWED");
    assertThat(
            TcpMapperFramingPolicy.existingMismatch(
                Optional.of(DECLARATION), FIRST.mapperId(), CUSTOM))
        .hasToString("PAIR_NOT_ALLOWED");
    assertThat(
            TcpMapperFramingPolicy.existingMismatch(
                Optional.of(DECLARATION), FIRST.mapperId(), null))
        .hasToString("PAIR_NOT_ALLOWED");
    assertThat(
            TcpMapperFramingPolicy.existingMismatch(
                Optional.of(DECLARATION), FIRST.mapperId(), framing(FIRST)))
        .isNull();
  }

  /** An integration with no framing of its own lets the connection supply its delimiter. */
  @Test
  void acceptsCustomFramingOnlyWhereTheDeclarationPermitsIt() {
    var customFraming = MapperFramingDeclaration.of(new FramingRule.AnyMapperCustomFraming());

    TcpMapperFramingPolicy.assertPermits(customFraming, SECOND.mapperId(), RESOLVED_CUSTOM);
    assertThat(
            TcpMapperFramingPolicy.existingMismatch(
                Optional.of(customFraming), SECOND.mapperId(), CUSTOM))
        .isNull();
    assertReason(
        "PAIR_NOT_ALLOWED",
        () ->
            TcpMapperFramingPolicy.assertPermits(customFraming, FIRST.mapperId(), resolved(FIRST)));
    assertReason(
        "PAIR_NOT_ALLOWED",
        () -> TcpMapperFramingPolicy.assertPermits(DECLARATION, FIRST.mapperId(), RESOLVED_CUSTOM));
  }

  /** A connection pairing no mapper still frames only as its integration permits. */
  @Test
  void holdsAFramingWithoutAMapperToTheDeclarationsFramings() {
    var customFraming = MapperFramingDeclaration.of(new FramingRule.AnyMapperCustomFraming());

    TcpMapperFramingPolicy.assertFramingPermitted(DECLARATION, resolved(FIRST));
    TcpMapperFramingPolicy.assertFramingPermitted(customFraming, RESOLVED_CUSTOM);
    assertReason(
        "FRAMING_NOT_ALLOWED",
        () -> TcpMapperFramingPolicy.assertFramingPermitted(DECLARATION, RESOLVED_CUSTOM));
    assertReason(
        "FRAMING_NOT_ALLOWED",
        () -> TcpMapperFramingPolicy.assertFramingPermitted(DECLARATION, resolved(SECOND)));
    assertReason(
        "FRAMING_NOT_ALLOWED",
        () -> TcpMapperFramingPolicy.assertFramingPermitted(customFraming, resolved(FIRST)));
  }

  /** A writing connection that stores no framing selects no pair, so nothing is checked. */
  @Test
  void acceptsASelectionWithoutAnIngressMapperOrAFraming() {
    TcpMapperFramingPolicy.assertSelection(DECLARATION, Optional.empty(), Optional.empty());
  }

  @Test
  void holdsTheFramingOfASelectionWithoutAnIngressMapperToTheDeclarationsFramings() {
    TcpMapperFramingPolicy.assertSelection(
        DECLARATION, Optional.empty(), Optional.of(resolved(FIRST)));
    assertReason(
        "FRAMING_NOT_ALLOWED",
        () ->
            TcpMapperFramingPolicy.assertSelection(
                DECLARATION, Optional.empty(), Optional.of(resolved(SECOND))));
  }

  @Test
  void refusesAnIngressMapperToASelectionWithoutAFraming() {
    assertReason(
        "PAIR_NOT_ALLOWED",
        () ->
            TcpMapperFramingPolicy.assertSelection(
                DECLARATION, Optional.of(FIRST.mapperId()), Optional.empty()));
  }

  @Test
  void acceptsAnIngressMapperOnlyPairedWithAFramingTheDeclarationPermits() {
    TcpMapperFramingPolicy.assertSelection(
        DECLARATION, Optional.of(FIRST.mapperId()), Optional.of(resolved(FIRST)));
    assertReason(
        "PAIR_NOT_ALLOWED",
        () ->
            TcpMapperFramingPolicy.assertSelection(
                DECLARATION, Optional.of(FIRST.mapperId()), Optional.of(RESOLVED_CUSTOM)));
  }

  private static ResolvedTcpFraming resolved(MapperFramingPair pair) {
    return new ResolvedTcpFraming.Integration(pair.framingId());
  }

  private static TcpFraming framing(MapperFramingPair pair) {
    return new IntegrationTcpFraming(pair.framingId());
  }

  private static MapperProfile mapper(UUID id, boolean archived) {
    return MapperProfile.builder().withId(id).withArchived(archived).build();
  }

  private static void assertReason(String code, Runnable validation) {
    assertThatThrownBy(validation::run)
        .isInstanceOfSatisfying(
            ConnectionManagementException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(code));
  }
}
