// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Layer, UpdateParameters } from "@deck.gl/core";

export type InvalidationSpec = {
    props: ReadonlyArray<string>;
    triggers: ReadonlyArray<string>;
};

export function needsRebuild<LayerT extends Layer>(
    params: UpdateParameters<LayerT>,
    spec: {
        props: readonly (keyof UpdateParameters<LayerT>["props"])[];
        triggers: readonly string[];
    },
) {
    const {
        oldProps,
        props,
        changeFlags: { dataChanged, updateTriggersChanged },
    } = params;

    return (
        dataChanged ||
        spec.props.some((prop) => oldProps[prop] !== props[prop]) ||
        (updateTriggersChanged &&
            (updateTriggersChanged.all ||
                spec.triggers.some((trigger) => updateTriggersChanged[trigger])))
    );
}

export function propsChanged<T extends Record<string, unknown>>(
    keys: (keyof T)[],
    props: T,
    oldProps: T,
): boolean {
    return keys.some((key) => props[key] !== oldProps[key]);
}
