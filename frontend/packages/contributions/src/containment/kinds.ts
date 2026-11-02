// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { noop } from "lodash";
import type { Element } from "@opencop/elements";
import type { Primitive } from "@opencop/scene";
import type { ToolDescriptor } from "@opencop/tools";
import type { FilterField } from "@opencop/ui";
import type { ContextMenuItemContribution } from "../context-menu.ts";
import type {
    DetailsEditFormContribution,
    DetailsHeaderContribution,
    DetailsInfoSectionContribution,
    DetailsPositionContribution,
} from "../details.ts";
import type { ElementMapper, MapperState } from "../element-mapper.ts";
import type { PickResponse } from "../pick-response.ts";
import type { Containment } from "./containment.ts";
import { containMembers, type ContainmentPolicy } from "./members.ts";
import type { ContributionOrigin, ContributionOwner } from "./origin.ts";

/*
 * Each contribution type's containment: what every member holding contributed code
 * costs when it throws. The fallbacks are the host's — what the operator sees instead —
 * so they live here, beside the contracts, and are applied the same way by every host. A
 * plugin supplies none of them.
 */

/** The members of a tool that hold contributed code, by the phase a failure is attributed to. */
export type ToolPhase = "icon" | "content" | "editor" | "hints" | "mapProduct";

/**
 * Where a failure in one of a tool's members is attributed. One spelling for every place
 * that contains a tool's code — the registry for `mapProduct`, the host's render sites for
 * its components — so a log line and a test read the same origin.
 */
export function toolOrigin(
    owner: ContributionOwner | undefined,
    toolId: string,
    phase: ToolPhase,
): ContributionOrigin {
    return { owner, contribution: toolId, phase };
}

/**
 * A tool with `mapProduct` — its drawing of the session's product — wrapped once: a throw
 * draws nothing. Its components are contained where the host renders them, under
 * {@link toolOrigin}.
 */
export function containTool(containment: Containment, tool: ToolDescriptor): ToolDescriptor {
    const { mapProduct } = tool;
    if (!mapProduct) return tool;
    containment.rearmContributions(tool.id);
    return {
        ...tool,
        mapProduct: containment.guard(
            toolOrigin(undefined, tool.id, "mapProduct"),
            mapProduct,
            () => [],
        ),
    };
}

const FILTER_FIELD: ContainmentPolicy<FilterField> = {
    newDraft: { fallback: () => undefined },
    formatValue: { fallback: () => undefined },
    companions: { fallback: () => undefined },
    claims: { fallback: () => null },
    ValueEditor: { render: true },
};

/** A filter field with every contributed member wrapped once; a throw costs only that member's output. */
export function containFilterField(
    containment: Containment,
    owner: ContributionOwner,
    field: FilterField,
): FilterField {
    const contribution = `filter-field/${field.id}`;
    containment.rearmContributions(contribution);
    return containMembers(
        containment,
        field,
        (phase) => ({ owner, contribution, phase }),
        FILTER_FIELD,
    );
}

const DETAILS_HEADER: ContainmentPolicy<DetailsHeaderContribution> = { typeIcon: { render: true } };

export function containDetailsHeader(
    containment: Containment,
    owner: ContributionOwner,
    header: DetailsHeaderContribution,
): DetailsHeaderContribution {
    const contribution = `details-header/${header.type}`;
    containment.rearmContributions(contribution);
    return containMembers(
        containment,
        header,
        (phase) => ({ owner, contribution, phase }),
        DETAILS_HEADER,
    );
}

const DETAILS_POSITION: ContainmentPolicy<DetailsPositionContribution> = {
    position: { fallback: () => null },
};

export function containDetailsPosition(
    containment: Containment,
    owner: ContributionOwner,
    position: DetailsPositionContribution,
): DetailsPositionContribution {
    const contribution = `details-position/${position.type}`;
    containment.rearmContributions(contribution);
    return containMembers(
        containment,
        position,
        (phase) => ({ owner, contribution, phase }),
        DETAILS_POSITION,
    );
}

const DETAILS_EDIT_FORM: ContainmentPolicy<DetailsEditFormContribution> = {
    Component: { render: true },
};
const DETAILS_INFO_SECTION: ContainmentPolicy<DetailsInfoSectionContribution> = {
    Component: { render: true },
};

/** An edit form or information section, attributed to `<surface>/<type>/<id>`. */
function containListed<C extends DetailsEditFormContribution | DetailsInfoSectionContribution>(
    containment: Containment,
    owner: ContributionOwner,
    surface: string,
    contribution: C,
    policy: ContainmentPolicy<C>,
): C {
    const name = `${surface}/${contribution.type}/${contribution.id}`;
    containment.rearmContributions(name);
    return containMembers(
        containment,
        contribution,
        (phase) => ({ owner, contribution: name, phase }),
        policy,
    );
}

export function containDetailsEditForm(
    containment: Containment,
    owner: ContributionOwner,
    form: DetailsEditFormContribution,
): DetailsEditFormContribution {
    return containListed(containment, owner, "details-edit-form", form, DETAILS_EDIT_FORM);
}

export function containDetailsInfoSection(
    containment: Containment,
    owner: ContributionOwner,
    section: DetailsInfoSectionContribution,
): DetailsInfoSectionContribution {
    return containListed(containment, owner, "details-info-section", section, DETAILS_INFO_SECTION);
}

const CONTEXT_MENU_ITEM: ContainmentPolicy<ContextMenuItemContribution> = {
    Content: { render: true },
};

/** A menu item with its rendered body contained; an action item holds no code and passes through. */
export function containContextMenuItem(
    containment: Containment,
    owner: ContributionOwner,
    item: ContextMenuItemContribution,
): ContextMenuItemContribution {
    if (!("Content" in item)) return item;
    const contribution = `context-menu/${item.id}`;
    containment.rearmContributions(contribution);
    return containMembers(
        containment,
        item,
        (phase) => ({ owner, contribution, phase }),
        CONTEXT_MENU_ITEM,
    );
}

const PICK_RESPONSE: ContainmentPolicy<PickResponse> = { onClick: { fallback: noop } };

/** A pick response whose `onClick` throw is logged against its owner; the click goes on as unanswered. */
export function containPickResponse(
    containment: Containment,
    owner: ContributionOwner,
    id: string,
    response: PickResponse,
): PickResponse {
    const contribution = `pick-response/${id}`;
    containment.rearmContributions(contribution);
    return containMembers(
        containment,
        response,
        (phase) => ({ owner, contribution, phase }),
        PICK_RESPONSE,
    );
}

/** The part of the host's state a mapper may read, whatever else `state` carries. */
function toMapperState({ units, icons, text, view }: MapperState): MapperState {
    return { units, icons, text: { size: text.size }, view };
}

/**
 * A mapper with every call wrapped once: a throw draws, previews or selects nothing. Hand
 * written rather than a {@link ContainmentPolicy}: `select` and `equalityFn` sit inside
 * `selector`, and a selector is handed only {@link MapperState}, never the rest of the host's.
 */
export function containElementMapper(
    containment: Containment,
    owner: ContributionOwner,
    mapper: ElementMapper<Element, unknown>,
): ElementMapper<Element, unknown> {
    const contribution = `element-mapper/${mapper.type}`;
    containment.rearmContributions(contribution);
    const origin = (phase: string): ContributionOrigin => ({ owner, contribution, phase });
    const { selector, preview, textSetting } = mapper;
    const select = selector && selector.select.bind(selector);
    const equalityFn = selector?.equalityFn?.bind(selector);
    return {
        type: mapper.type,
        textSetting,
        textIsContent: mapper.textIsContent,
        selector: select && {
            debounce: selector.debounce,
            select: containment.guard(
                origin("select"),
                (state: MapperState) => select(toMapperState(state)),
                () => undefined,
            ),
            equalityFn:
                equalityFn && containment.guard(origin("equalityFn"), equalityFn, () => false),
        },
        map: containment.guard<[Element, unknown], Primitive[]>(
            origin("map"),
            mapper.map.bind(mapper),
            () => [],
        ),
        preview:
            preview &&
            containment.guard<[Element], CanvasImageSource | null>(
                origin("preview"),
                preview.bind(mapper),
                () => null,
            ),
    };
}
