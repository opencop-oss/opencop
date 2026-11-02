// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useSyncExternalStore } from "react";
import type { Element } from "@opencop/elements";
import type { ElementMapper } from "@opencop/contributions";
import { createElementMapperRegistry, type ElementMapperEntry } from "@opencop/contributions/host";
import { hostContainment } from "~/features/contributions/contribution-guard";

export type { ElementMapperEntry };

/**
 * The app's element-mapper registry — the one instance of `createElementMapperRegistry`,
 * on the host's containment — and what the scene and the UI read from it.
 */
export const elementMapperRegistry = createElementMapperRegistry({ containment: hostContainment });

/** Register how `mapper.type` is drawn, owned by `owner`. @returns The removal. */
export const registerElementMapper = elementMapperRegistry.register;

/** Every registration, with its owner. */
export const getElementMapperEntries = elementMapperRegistry.getEntries;

const mappersCache = new WeakMap<
    readonly ElementMapperEntry[],
    readonly ElementMapper<Element, unknown>[]
>();

/** Every registered mapper, contained. A stable array between registry changes. */
export function getElementMappers(): readonly ElementMapper<Element, unknown>[] {
    const entries = elementMapperRegistry.getEntries();
    let mappers = mappersCache.get(entries);
    if (!mappers) {
        mappers = entries.map((entry) => entry.mapper);
        mappersCache.set(entries, mappers);
    }
    return mappers;
}

/** Hear a mapper registered or removed. Returns the unsubscribe. */
export const subscribeElementMappers = elementMapperRegistry.subscribe;

/** The mapper drawing `type`, or `undefined` when nothing does. */
export function mapperFor(type: string): ElementMapper<Element, unknown> | undefined {
    return elementMapperRegistry.getEntries().find((entry) => entry.mapper.type === type)?.mapper;
}

/** The element's thumbnail from its type's mapper, or null for none. */
export function resolveElementPreview(element: Element): CanvasImageSource | null {
    return mapperFor(element.type)?.preview?.(element) ?? null;
}

export interface TextSetting {
    readonly type: string;
    readonly labelKey: string;
    readonly order: number;
}

const textSettingsCache = new WeakMap<readonly ElementMapperEntry[], readonly TextSetting[]>();

/**
 * The element types whose text the operator can toggle, from their mappers'
 * `textSetting`, listed by declared order. A stable array between registry changes.
 */
export function getTextSettings(): readonly TextSetting[] {
    const entries = elementMapperRegistry.getEntries();
    const cached = textSettingsCache.get(entries);
    if (cached) return cached;
    const settings = entries
        .flatMap(({ mapper: { type, textSetting } }) =>
            textSetting
                ? [{ type, labelKey: textSetting.labelKey, order: textSetting.order ?? Infinity }]
                : [],
        )
        .sort((a, b) => a.order - b.order || a.type.localeCompare(b.type));
    textSettingsCache.set(entries, settings);
    return settings;
}

export function useTextSettings(): readonly TextSetting[] {
    return useSyncExternalStore(subscribeElementMappers, getTextSettings);
}
