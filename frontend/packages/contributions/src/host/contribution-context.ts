// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ContributionContext } from "../contribution-context.ts";
import {
    containPickResponse,
    type Containment,
    type ContributionOwner,
} from "../containment/index.ts";
import type { ContextMenuRegistry } from "./context-menu.ts";
import type { DetailsRegistry } from "./details.ts";
import type { ElementMapperRegistry } from "./element-mappers.ts";
import type { FilterFieldRegistry } from "./filter-fields.ts";
import type { PickResponseRegistry } from "./pick-responses.ts";
import type { ToolRegistry } from "./tools.ts";

/** One host's registries, and the containment its pick responses are wrapped in. */
export interface HostRegistries {
    readonly containment: Containment;
    readonly tools: ToolRegistry;
    readonly elementMappers: ElementMapperRegistry;
    readonly pickResponses: PickResponseRegistry;
    readonly details: DetailsRegistry;
    readonly filterFields: FilterFieldRegistry;
    readonly contextMenu: ContextMenuRegistry;
}

/**
 * The context one native subject registers through, writing straight into `registries`
 * and recording every registration but a tool as owned by `owner`.
 */
export function contributionContextOn(
    registries: HostRegistries,
    owner: ContributionOwner,
): ContributionContext {
    const {
        containment,
        tools,
        elementMappers,
        pickResponses,
        details,
        filterFields,
        contextMenu,
    } = registries;
    return {
        registerTool: (tool) => tools.register(tool),
        registerElementMapper: (mapper) => elementMappers.register(owner, mapper),
        registerPickResponse: (id, response) =>
            pickResponses.register(id, containPickResponse(containment, owner, id, response)),
        registerDetailsHeader: (contribution) => details.registerHeader(owner, contribution),
        registerDetailsPosition: (contribution) => details.registerPosition(owner, contribution),
        registerDetailsEditForm: (contribution) => details.registerEditForm(owner, contribution),
        registerDetailsInfoSection: (contribution) =>
            details.registerInfoSection(owner, contribution),
        registerFilterField: (field) => filterFields.register(owner, field),
        registerContextMenuItem: (item) => contextMenu.register(owner, item),
    };
}
