// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ContributionContext } from "@opencop/contributions";
import { contributionContextOn, type HostRegistries } from "@opencop/contributions/host";
import { hostContainment, nativeOwner } from "~/features/contributions/contribution-guard";
import { toolRegistry } from "~/features/contributions/tool-registry";
import { elementMapperRegistry } from "~/features/contributions/element-mapper-registry";
import { pickResponseRegistry } from "~/features/map/interaction-runtime/arbitration/pick-responses";
import { detailsRegistry } from "~/features/contributions/details-registry";
import { contextMenuRegistry } from "~/features/contributions/context-menu-registry";
import { filterFieldRegistry } from "~/features/contributions/filter-field-registry";

/** The app's registries, as one host's set: what every native subject's context writes to. */
const APP_REGISTRIES: HostRegistries = {
    containment: hostContainment,
    tools: toolRegistry,
    elementMappers: elementMapperRegistry,
    pickResponses: pickResponseRegistry,
    details: detailsRegistry,
    filterFields: filterFieldRegistry,
    contextMenu: contextMenuRegistry,
};

const contexts = new Map<string, ContributionContext>();

/**
 * The live context for one native subject, on the app's registries — the shared
 * `contributionContextOn`, so a test host's native registers by the same code. The
 * native install path wraps it to journal each registration.
 *
 * One context per subject id, and the same object every time for the same id. The id
 * is a namespaced owner id (`opencop.shapes`); it is not a member of the context, so a
 * subject reads nothing from it.
 */
export function contributionContextFor(subjectId: string): ContributionContext {
    let context = contexts.get(subjectId);
    if (!context) {
        context = contributionContextOn(APP_REGISTRIES, nativeOwner(subjectId));
        contexts.set(subjectId, context);
    }
    return context;
}
