// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.runtimefeature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RuntimeFeatureAudienceTest {

  @Test
  void audienceIsExactlyNoneAllOrUserGroups() {
    assertThat(RuntimeFeatureAudience.class.getPermittedSubclasses())
        .containsExactlyInAnyOrder(
            RuntimeFeatureAudience.None.class,
            RuntimeFeatureAudience.All.class,
            RuntimeFeatureAudience.UserGroups.class);
    assertThat(new RuntimeFeatureAudience.None()).isEqualTo(new RuntimeFeatureAudience.None());
    assertThat(new RuntimeFeatureAudience.All()).isEqualTo(new RuntimeFeatureAudience.All());
  }

  @Test
  void userGroupsDefensivelyCopiesSortsAndProtectsItsIds() {
    var first = UUID.fromString("00000000-0000-4000-8000-000000000201");
    var second = UUID.fromString("00000000-0000-4000-8000-000000000202");
    var laterMutation = UUID.fromString("00000000-0000-4000-8000-000000000203");
    var mutableIds = new TreeSet<UUID>(Comparator.reverseOrder());
    mutableIds.add(second);
    mutableIds.add(first);

    var audience = new RuntimeFeatureAudience.UserGroups(mutableIds);
    mutableIds.add(laterMutation);

    assertThat(audience.userGroupIds()).containsExactly(first, second);
    assertThat(audience)
        .isEqualTo(new RuntimeFeatureAudience.UserGroups(new TreeSet<>(audience.userGroupIds())));
    assertThatThrownBy(() -> audience.userGroupIds().add(laterMutation))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void userGroupsRejectsNullEmptyAndNullElementInputs() {
    assertThatThrownBy(() -> new RuntimeFeatureAudience.UserGroups((SortedSet<UUID>) null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RuntimeFeatureAudience.UserGroups(new TreeSet<>()))
        .isInstanceOf(IllegalArgumentException.class);

    SortedSet<UUID> idsWithNull = new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));
    idsWithNull.add(null);
    assertThatThrownBy(() -> new RuntimeFeatureAudience.UserGroups(idsWithNull))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void userGroupsAcceptsAtMostOneHundredTargets() {
    var maximumAudience =
        new RuntimeFeatureAudience.UserGroups(
            userGroupIds(RuntimeFeatureAudience.UserGroups.MAX_USER_GROUP_IDS));

    assertThat(maximumAudience.userGroupIds())
        .hasSize(RuntimeFeatureAudience.UserGroups.MAX_USER_GROUP_IDS);
    assertThatThrownBy(
            () ->
                new RuntimeFeatureAudience.UserGroups(
                    userGroupIds(RuntimeFeatureAudience.UserGroups.MAX_USER_GROUP_IDS + 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1 and 100");
  }

  @Test
  void invalidNeutralValuesAreRejected() {
    assertThatThrownBy(
            () ->
                new RuntimeFeatureState(
                    " ",
                    RuntimeFeatureConsumerClass.BACKEND_ENFORCED,
                    new RuntimeFeatureAudience.None(),
                    0,
                    Instant.EPOCH))
        .isInstanceOf(IllegalArgumentException.class);
    var actor =
        new RuntimeFeatureActor(
            UUID.randomUUID(),
            Optional.of("  Ada\u0000 Lovelace  "),
            Optional.of("  ada@example.test\n"),
            true);

    assertThat(actor.fullName()).contains("Ada Lovelace");
    assertThat(actor.email()).contains("ada@example.test");
    assertThat(actor.globalAdmin()).isTrue();
    assertThat(
            new RuntimeFeatureActor(
                UUID.randomUUID(), Optional.of(" \n "), Optional.of("\t"), false))
        .satisfies(
            sanitized -> {
              assertThat(sanitized.fullName()).isEmpty();
              assertThat(sanitized.email()).isEmpty();
            });
  }

  @Test
  void backendFeatureEnumContainsOnlyWatchbox() {
    assertThat(BackendRuntimeFeature.values()).containsExactly(BackendRuntimeFeature.WATCHBOX);
    assertThat(BackendRuntimeFeature.WATCHBOX.wireName()).isEqualTo("watchbox");
  }

  private static SortedSet<UUID> userGroupIds(int size) {
    return IntStream.range(0, size)
        .mapToObj(index -> new UUID(0, index))
        .collect(Collectors.toCollection(TreeSet::new));
  }
}
