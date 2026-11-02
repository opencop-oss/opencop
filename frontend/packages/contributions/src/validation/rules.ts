// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    ELEMENT_STORE_ID,
    productionError,
    SESSION_STORE_ID,
    styleError,
    type ToolDescriptor,
} from "@opencop/tools";
import type { Element } from "@opencop/elements";
import type { ElementMapper } from "../element-mapper.ts";
import { operators, type FilterField } from "@opencop/ui";
import {
    attributeNameOf,
    attributeNameOfId,
    HOST_FILTER_FIELD_IDS,
    HOST_FILTER_FIELD_PATHS,
    isFilterablePath,
} from "./filter-paths.ts";
import type {
    DetailsEditFormContribution,
    DetailsHeaderContribution,
    DetailsInfoSectionContribution,
    DetailsPositionContribution,
} from "../details.ts";
import type { ContextMenuItemContribution } from "../context-menu.ts";
import type { ValidationEnvironment } from "./environment.ts";
import { problemCollector, type Problem } from "./problem.ts";

/*
 * The rules one contribution must meet whoever contributes it — a native plugin or a
 * runtime plugin — because they depend on nothing but the contribution and facts the
 * host states once (the filterable paths, the host's own field ids, the store ids). What
 * depends on what else is installed (an id another owner holds) is not here: only the
 * registry holding the others can see it.
 *
 * Each returns every problem it finds. It stops within the item only where going on
 * would name the item wrongly or repeat itself: no identity, or a check whose input an
 * earlier check refused.
 */

/** Whether `value` can be rendered as a component: a function, or a `memo` / `forwardRef` object. */
export function isComponent(value: unknown): boolean {
    return typeof value === "function" || (typeof value === "object" && value !== null);
}

const isNonEmptyString = (value: unknown): value is string =>
    typeof value === "string" && value.length > 0;

export function validateTool(tool: ToolDescriptor): Problem[] {
    const { problems, report } = problemCollector("tools");
    if (!isNonEmptyString(tool?.id)) {
        report("tools.id-missing", "tool.id missing");
        return problems;
    }
    const production = productionError(tool.produces) ?? styleError(tool.produces, tool.style);
    if (production) report("tools.production-invalid", `tool "${tool.id}" ${production}`, tool.id);
    const mapProduct = mapProductError(tool);
    if (mapProduct)
        report("tools.map-product-misplaced", `tool "${tool.id}" ${mapProduct}`, tool.id);
    return problems;
}

/** Why a tool's `mapProduct` is misplaced, or null: only a product the session holds is drawn by its tool. */
function mapProductError({ mapProduct, produces }: ToolDescriptor): string | null {
    if (mapProduct === undefined) return null;
    if (typeof mapProduct !== "function") return "mapProduct is not a function";
    return produces?.store === SESSION_STORE_ID
        ? null
        : "mapProduct is only for a product the session store holds";
}

/**
 * A mapper's shape, read as untrusted — a plugin's mapper reaches the host unchecked by the
 * compiler. Whether its type is already drawn is the registry's to refuse.
 */
export function validateElementMapper<S>(mapper: ElementMapper<Element, S>): Problem[] {
    const { problems, report } = problemCollector("elementMappers");
    const { type, map, selector, preview, textSetting } = (mapper ?? {}) as {
        readonly type?: unknown;
        readonly map?: unknown;
        readonly preview?: unknown;
        readonly selector?: { readonly select?: unknown } | null;
        readonly textSetting?: { readonly labelKey?: unknown; readonly order?: unknown } | null;
    };
    if (!isNonEmptyString(type)) {
        report("elementMappers.type-missing", "elementMapper.type missing");
        return problems;
    }
    const name = `elementMapper "${type}"`;
    if (typeof map !== "function")
        report("elementMappers.map-missing", `${name} map missing`, type);
    if (selector != null && typeof selector.select !== "function")
        report("elementMappers.selector-invalid", `${name} selector.select missing`, type);
    if (preview !== undefined && typeof preview !== "function")
        report("elementMappers.preview-not-function", `${name} preview is not a function`, type);
    if (textSetting !== undefined) {
        if (!isNonEmptyString(textSetting?.labelKey))
            report(
                "elementMappers.text-label-key-missing",
                `${name} textSetting.labelKey missing`,
                type,
            );
        if (textSetting?.order !== undefined && typeof textSetting.order !== "number")
            report(
                "elementMappers.text-order-not-number",
                `${name} textSetting.order is not a number`,
                type,
            );
    }
    return problems;
}

export function validateDetailsHeader(contribution: DetailsHeaderContribution): Problem[] {
    const { problems, report } = problemCollector("detailsHeaders");
    if (!isNonEmptyString(contribution?.type)) {
        report("detailsHeaders.type-missing", "detailsHeader.type missing");
        return problems;
    }
    const { type, typeIcon, iconFrame } = contribution;
    if (typeIcon !== undefined && !isComponent(typeIcon))
        report(
            "detailsHeaders.type-icon-not-component",
            `detailsHeader "${type}" typeIcon is not a component`,
            type,
        );
    if (iconFrame !== undefined && iconFrame !== "active" && iconFrame !== "none")
        report(
            "detailsHeaders.icon-frame-unknown",
            `detailsHeader "${type}" iconFrame "${String(iconFrame)}" unknown`,
            type,
        );
    return problems;
}

export function validateDetailsPosition(contribution: DetailsPositionContribution): Problem[] {
    const { problems, report } = problemCollector("detailsPositions");
    if (!isNonEmptyString(contribution?.type)) {
        report("detailsPositions.type-missing", "detailsPosition.type missing");
        return problems;
    }
    if (typeof contribution.position !== "function")
        report(
            "detailsPositions.position-not-function",
            `detailsPosition "${contribution.type}" position is not a function`,
            contribution.type,
        );
    return problems;
}

/** The rules an edit form and an information section share: a listed component for one type. */
function validateOrdered(
    kind: "detailsEditForms" | "detailsInfoSections",
    noun: string,
    contribution: DetailsEditFormContribution | DetailsInfoSectionContribution,
): Problem[] {
    const { problems, report } = problemCollector(kind);
    if (!isNonEmptyString(contribution?.type)) {
        report(`${kind}.type-missing`, `${noun}.type missing`);
        return problems;
    }
    if (!isNonEmptyString(contribution.id)) {
        report(`${kind}.id-missing`, `${noun} for "${contribution.type}": id missing`);
        return problems;
    }
    const { id, type } = contribution;
    const name = `${noun} "${id}" for "${type}"`;
    if (typeof contribution.order !== "number")
        report(`${kind}.order-not-number`, `${name} order is not a number`, id);
    if (!isComponent(contribution.Component))
        report(`${kind}.component-not-component`, `${name} Component is not a component`, id);
    return problems;
}

export function validateDetailsEditForm(contribution: DetailsEditFormContribution): Problem[] {
    return validateOrdered("detailsEditForms", "detailsEditForm", contribution);
}

export function validateDetailsInfoSection(
    contribution: DetailsInfoSectionContribution,
): Problem[] {
    return validateOrdered("detailsInfoSections", "detailsInfoSection", contribution);
}

export function validateContextMenuItem(item: ContextMenuItemContribution): Problem[] {
    const { problems, report } = problemCollector("contextMenuItems");
    if (!isNonEmptyString(item?.id)) {
        report("contextMenuItems.id-missing", "contextMenuItem.id missing");
        return problems;
    }
    const { id } = item;
    const name = `contextMenuItem "${id}"`;
    if (item.order !== undefined && typeof item.order !== "number")
        report("contextMenuItems.order-not-number", `${name} order is not a number`, id);
    const target = item.target as { kind?: unknown; store?: unknown; type?: unknown } | undefined;
    if (target?.kind !== "ground" && target?.kind !== "object")
        report("contextMenuItems.target-kind-unknown", `${name} target.kind unknown`, id);
    else if (target.kind === "object") {
        if (!isNonEmptyString(target.type))
            report("contextMenuItems.target-type-missing", `${name} target.type missing`, id);
        // Only element-store objects open the menu; an item for another store would never show.
        else if (target.store !== undefined && target.store !== ELEMENT_STORE_ID)
            report(
                "contextMenuItems.target-store-closed",
                `${name} target.store "${String(target.store)}" does not open the menu`,
                id,
            );
    }
    if ("Content" in item) {
        if (!isComponent(item.Content))
            report(
                "contextMenuItems.content-not-component",
                `${name} Content is not a component`,
                id,
            );
        return problems;
    }
    if (!isNonEmptyString(item.labelKey))
        report("contextMenuItems.label-key-missing", `${name} labelKey missing`, id);
    if (!isNonEmptyString(item.activate?.toolId))
        report("contextMenuItems.activate-tool-missing", `${name} activate.toolId missing`, id);
    return problems;
}

const FILTER_FIELD_MEMBERS = ["newDraft", "formatValue", "companions", "claims"] as const;

export function validateFilterField(
    field: FilterField,
    environment: ValidationEnvironment,
): Problem[] {
    const { problems, report } = problemCollector("filterFields");
    const id: unknown = field?.id;
    if (!isNonEmptyString(id)) {
        report("filterFields.id-missing", "filterField.id missing");
        return problems;
    }
    const name = `filterField "${id}"`;
    if (HOST_FILTER_FIELD_IDS.has(id) || attributeNameOfId(id) !== undefined)
        report("filterFields.id-reserved", `${name} id is reserved by the host`, id);
    if (!isNonEmptyString(field.labelKey))
        report("filterFields.label-key-missing", `${name} labelKey missing`, id);
    else if (!environment.translationExists(field.labelKey))
        report(
            "filterFields.label-key-unresolved",
            `${name} labelKey "${field.labelKey}" does not resolve`,
            id,
        );
    if (field.noticeKey !== undefined && !environment.translationExists(field.noticeKey))
        report(
            "filterFields.notice-key-unresolved",
            `${name} noticeKey "${field.noticeKey}" does not resolve`,
            id,
        );
    if (!isNonEmptyString(field.path))
        report("filterFields.path-missing", `${name} path missing`, id);
    else if (!isFilterablePath(field.path))
        report(
            "filterFields.path-not-filterable",
            `${name} path "${field.path}" is not a filterable property`,
            id,
        );
    // A field without `claims` is matched by path, so on a path the host's own fields
    // hold it would take their saved conditions.
    else if (
        field.claims === undefined &&
        (HOST_FILTER_FIELD_PATHS.has(field.path) || attributeNameOf(field.path))
    )
        report(
            "filterFields.host-path-needs-claims",
            `${name} path "${field.path}" is the host's; a field on it must declare claims`,
            id,
        );
    if (typeof field.order !== "number" || !Number.isFinite(field.order))
        report("filterFields.order-missing", `${name} order missing`, id);
    if (field.operators !== undefined) {
        if (!Array.isArray(field.operators) || field.operators.length === 0)
            report(
                "filterFields.operators-empty",
                `${name} operators must list at least one operator`,
                id,
            );
        else {
            // `in` would also accept inherited keys such as "toString".
            for (const operator of field.operators) {
                if (!Object.hasOwn(operators, operator))
                    report(
                        "filterFields.operator-unknown",
                        `${name} operator "${String(operator)}" unknown`,
                        id,
                    );
            }
        }
    }
    if (field.ValueEditor !== undefined && !isComponent(field.ValueEditor))
        report(
            "filterFields.value-editor-not-component",
            `${name} ValueEditor is not a component`,
            id,
        );
    for (const member of FILTER_FIELD_MEMBERS) {
        if (field[member] !== undefined && typeof field[member] !== "function")
            report("filterFields.member-not-function", `${name} ${member} is not a function`, id);
    }
    return problems;
}
