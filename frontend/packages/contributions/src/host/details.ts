// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ComponentType } from "react";
import type {
    DetailsEditFormContribution,
    DetailsEditFormProps,
    DetailsHeaderContribution,
    DetailsInfoSectionContribution,
    DetailsInfoSectionProps,
    DetailsPositionContribution,
    DetailsTypeIconProps,
} from "../details.ts";
import {
    containDetailsEditForm,
    containDetailsHeader,
    containDetailsInfoSection,
    containDetailsPosition,
    type Containment,
    type ContributionOwner,
} from "../containment/index.ts";
import {
    validateDetailsEditForm,
    validateDetailsHeader,
    validateDetailsInfoSection,
    validateDetailsPosition,
    type Problem,
} from "../validation/index.ts";
import { refuseProblems } from "./refusal.ts";
import { Registry } from "./registry.ts";

/** A registration, with its owner and its contributed code contained. */
export interface DetailsEntry<C> extends ContributionOwner {
    readonly contribution: C;
}

/** What the panel resolves from. A new object whenever any details surface changes. */
export interface DetailsSnapshot {
    readonly headers: readonly DetailsEntry<DetailsHeaderContribution>[];
    readonly positions: readonly DetailsEntry<DetailsPositionContribution>[];
    readonly editForms: readonly DetailsEntry<DetailsEditFormContribution>[];
    readonly infoSections: readonly DetailsEntry<DetailsInfoSectionContribution>[];
}

/** The header presentation for a type; a field is `undefined` where the host's default applies. */
export interface ResolvedDetailsHeader {
    readonly typeIcon?: ComponentType<DetailsTypeIconProps>;
    readonly iconFrame?: "active" | "none";
}

export interface DetailsRegistry {
    registerHeader(owner: ContributionOwner, contribution: DetailsHeaderContribution): () => void;
    registerPosition(
        owner: ContributionOwner,
        contribution: DetailsPositionContribution,
    ): () => void;
    registerEditForm(
        owner: ContributionOwner,
        contribution: DetailsEditFormContribution,
    ): () => void;
    registerInfoSection(
        owner: ContributionOwner,
        contribution: DetailsInfoSectionContribution,
    ): () => void;
    resolveHeader(type: string, snapshot?: DetailsSnapshot): ResolvedDetailsHeader;
    /** The contributed position for a type, or `undefined` where the host's default applies. */
    resolvePosition(
        type: string,
        snapshot?: DetailsSnapshot,
    ): DetailsPositionContribution | undefined;
    resolveEditForms(
        type: string,
        snapshot?: DetailsSnapshot,
    ): readonly DetailsEditFormContribution[];
    resolveInfoSections(
        type: string,
        snapshot?: DetailsSnapshot,
    ): readonly DetailsInfoSectionContribution[];
    readonly getSnapshot: () => DetailsSnapshot;
    readonly subscribe: (listener: () => void) => () => void;
}

/** A one-value-per-type surface: register refuses a type another registration holds. */
function singleValue<C extends { readonly type: string }>(surface: string) {
    const registry = new Registry<DetailsEntry<C>>();
    function register(owner: ContributionOwner, contribution: C): () => void {
        const holder = registry
            .getAll()
            .find((entry) => entry.contribution.type === contribution.type);
        if (holder)
            throw new Error(
                `[${surface}]: refused: "${contribution.type}" is already held by "${holder.ownerId}".`,
            );
        const entry: DetailsEntry<C> = { contribution, ...owner };
        registry.add(entry);
        return () => registry.remove((candidate) => candidate === entry);
    }
    const resolve = (type: string, all: readonly DetailsEntry<C>[]) =>
        all.find((entry) => entry.contribution.type === type)?.contribution;
    return { registry, register, resolve };
}

/** A many-valued surface's contribution: a component listed by `order` for its type. */
interface OrderedContribution<P> {
    readonly id: string;
    readonly type: string;
    readonly order: number;
    readonly Component: ComponentType<P>;
}

/** A many-valued surface: contributions by type and `id`, listed by `order`. */
function ordered<P extends object, C extends OrderedContribution<P>>(
    surface: string,
    validate: (contribution: C) => readonly Problem[],
    contain: (owner: ContributionOwner, contribution: C) => C,
) {
    const registry = new Registry<DetailsEntry<C>>();
    function register(owner: ContributionOwner, contribution: C): () => void {
        refuseProblems(surface, validate(contribution));
        if (
            registry
                .getAll()
                .some(
                    ({ contribution: held }) =>
                        held.type === contribution.type && held.id === contribution.id,
                )
        )
            throw new Error(
                `[${surface}]: refused: id "${contribution.id}" is already registered for "${contribution.type}".`,
            );
        const entry: DetailsEntry<C> = { ...owner, contribution: contain(owner, contribution) };
        registry.add(entry);
        return () => registry.remove((candidate) => candidate === entry);
    }
    const resolve = (type: string, all: readonly DetailsEntry<C>[]): readonly C[] =>
        all
            .filter((entry) => entry.contribution.type === type)
            .map((entry) => entry.contribution)
            .sort((a, b) => a.order - b.order);
    return { registry, register, resolve };
}

/**
 * The registries of the details panel's surfaces: the header, the Coordinates row's
 * position, the edit form's inputs and the Information tab's sections. Every
 * contribution names one element type, and the panel resolves by the selected
 * element's `type`. Contributed code is contained once, at registration, by each
 * surface's own containment. One instance per host.
 */
export function createDetailsRegistry({
    containment,
}: {
    readonly containment: Containment;
}): DetailsRegistry {
    const headers = singleValue<DetailsHeaderContribution>("details-header");
    const positions = singleValue<DetailsPositionContribution>("details-position");
    const editForms = ordered<DetailsEditFormProps, DetailsEditFormContribution>(
        "details-edit-form",
        validateDetailsEditForm,
        (owner, form) => containDetailsEditForm(containment, owner, form),
    );
    const infoSections = ordered<DetailsInfoSectionProps, DetailsInfoSectionContribution>(
        "details-info-section",
        validateDetailsInfoSection,
        (owner, section) => containDetailsInfoSection(containment, owner, section),
    );

    const take = (): DetailsSnapshot => ({
        headers: headers.registry.getAll(),
        positions: positions.registry.getAll(),
        editForms: editForms.registry.getAll(),
        infoSections: infoSections.registry.getAll(),
    });
    let snapshot = take();
    const listeners = new Set<() => void>();
    for (const surface of [headers, positions, editForms, infoSections])
        surface.registry.subscribe(() => {
            snapshot = take();
            for (const listener of listeners) listener();
        });

    return {
        registerHeader(owner, contribution) {
            refuseProblems("details-header", validateDetailsHeader(contribution));
            return headers.register(owner, containDetailsHeader(containment, owner, contribution));
        },
        registerPosition(owner, contribution) {
            refuseProblems("details-position", validateDetailsPosition(contribution));
            return positions.register(
                owner,
                containDetailsPosition(containment, owner, contribution),
            );
        },
        registerEditForm: editForms.register,
        registerInfoSection: infoSections.register,
        resolveHeader(type, all = snapshot) {
            const { typeIcon, iconFrame } = headers.resolve(type, all.headers) ?? {};
            return { typeIcon, iconFrame };
        },
        resolvePosition: (type, all = snapshot) => positions.resolve(type, all.positions),
        resolveEditForms: (type, all = snapshot) => editForms.resolve(type, all.editForms),
        resolveInfoSections: (type, all = snapshot) => infoSections.resolve(type, all.infoSections),
        getSnapshot: () => snapshot,
        subscribe(listener) {
            listeners.add(listener);
            return () => listeners.delete(listener);
        },
    };
}
