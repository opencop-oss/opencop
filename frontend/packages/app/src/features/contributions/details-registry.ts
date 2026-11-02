// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo, useSyncExternalStore } from "react";
import type { Element } from "@opencop/elements";
import type {
    DetailsEditFormContribution,
    DetailsInfoSectionContribution,
    DetailsPosition,
} from "@opencop/contributions";
import {
    createDetailsRegistry,
    type DetailsSnapshot,
    type ResolvedDetailsHeader,
} from "@opencop/contributions/host";
import { hostContainment } from "~/features/contributions/contribution-guard";

/**
 * The app's details-panel registries: the one instance of `createDetailsRegistry`, on
 * the host's containment, and the hooks the panel reads it through.
 */
export const detailsRegistry = createDetailsRegistry({ containment: hostContainment });
const details = detailsRegistry;

export type { ResolvedDetailsHeader };

/** Contribute a header presentation, owned by `owner`. */
export const registerDetailsHeader = details.registerHeader;
/** Contribute a details position, owned by `owner`. */
export const registerDetailsPosition = details.registerPosition;
/** Contribute edit-form inputs, owned by `owner`. */
export const registerDetailsEditForm = details.registerEditForm;
/** Contribute an information section, owned by `owner`. */
export const registerDetailsInfoSection = details.registerInfoSection;

export const resolveDetailsHeader = (type: string, snapshot?: DetailsSnapshot) =>
    details.resolveHeader(type, snapshot);

/**
 * The contributed position for `element`: `undefined` when no registration
 * applies to its type (the host's default then decides), `null` when one applies
 * and shows no row.
 */
export function resolveDetailsPosition(element: Element): DetailsPosition | null | undefined {
    return details.resolvePosition(element.type)?.position(element);
}

/** The type's edit forms, by `order`. */
export const resolveDetailsEditForms = (type: string, snapshot?: DetailsSnapshot) =>
    details.resolveEditForms(type, snapshot);
/** The type's information sections, by `order`. */
export const resolveDetailsInfoSections = (type: string, snapshot?: DetailsSnapshot) =>
    details.resolveInfoSections(type, snapshot);

function useDetailsSnapshot(): DetailsSnapshot {
    return useSyncExternalStore(details.subscribe, details.getSnapshot);
}

/** The header presentation for `element`'s type. */
export function useDetailsHeader(element: Element): ResolvedDetailsHeader {
    const all = useDetailsSnapshot();
    const { type } = element;
    return useMemo(() => details.resolveHeader(type, all), [all, type]);
}

/**
 * The position the Coordinates row shows for `element`: a contributed one, `null`
 * for no row, or `undefined` where the host's default applies. Computed on every
 * render, because the element's geometry changes in place.
 */
export function useDetailsPosition(
    element: Element | undefined,
): DetailsPosition | null | undefined {
    const all = useDetailsSnapshot();
    const type = element?.type;
    const contribution = useMemo(
        () => (type === undefined ? undefined : details.resolvePosition(type, all)),
        [all, type],
    );
    return element ? contribution?.position(element) : undefined;
}

/** The edit forms for `element`'s type, by order. */
export function useDetailsEditForms(element: Element): readonly DetailsEditFormContribution[] {
    const all = useDetailsSnapshot();
    const { type } = element;
    return useMemo(() => details.resolveEditForms(type, all), [all, type]);
}

/** The information sections for `element`'s type, by order; none without an element. */
export function useDetailsInfoSections(
    element: Element | undefined,
): readonly DetailsInfoSectionContribution[] {
    const all = useDetailsSnapshot();
    const type = element?.type;
    return useMemo(
        () => (type === undefined ? [] : details.resolveInfoSections(type, all)),
        [all, type],
    );
}

/** Every registration across the surfaces, by identity, for tests that check what a disposal removed. */
export function getDetailsSurfaceEntries(): readonly object[] {
    return [
        ...details.getSnapshot().headers,
        ...details.getSnapshot().positions,
        ...details.getSnapshot().editForms,
        ...details.getSnapshot().infoSections,
    ];
}

/** Every registration, for tests that assert disposal empties the surfaces. */
export function getDetailsSurfaceCounts(): Record<
    "headers" | "positions" | "editForms" | "infoSections",
    number
> {
    return {
        headers: details.getSnapshot().headers.length,
        positions: details.getSnapshot().positions.length,
        editForms: details.getSnapshot().editForms.length,
        infoSections: details.getSnapshot().infoSections.length,
    };
}
