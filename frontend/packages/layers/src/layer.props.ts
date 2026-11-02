// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Accessor } from "@deck.gl/core";

export type SubLayerAccessorWrapper<DataT = unknown> = <T>(
    accessor: Accessor<DataT, T>,
) => Accessor<DataT, T>;
type Forwarded<I, M extends Partial<Record<keyof I, string>>> = {
    [K in keyof I as M[K] extends string ? M[K] : never]: I[K];
};

export function forward<const I, const M extends Partial<Record<keyof I, string>>>(
    props: I,
    map: M,
    wrapper?: SubLayerAccessorWrapper,
): Forwarded<I, M> {
    const out = {} as Record<string, unknown>;
    for (const ourKey in map) {
        const theirKey = map[ourKey];
        if (theirKey === undefined) continue;
        const value = props[ourKey as unknown as keyof I];
        const shouldWrap = wrapper && ourKey.startsWith("get");
        out[theirKey] = shouldWrap ? wrapper(value as Accessor<unknown, unknown>) : value;
    }
    return out as Forwarded<I, M>;
}

type FilteredAccessorKeys<M> = {
    [K in keyof M as K extends `get${string}` ? K : never]: M[K];
};

export function filterAccessorKeys<const M extends Record<string, string>>(
    map: M,
): FilteredAccessorKeys<M> {
    return Object.fromEntries(
        Object.entries(map).filter(([ourKey]) => ourKey.startsWith("get")),
    ) as FilteredAccessorKeys<M>;
}

export function compose<P>(...pieces: Partial<P>[]): Partial<P> {
    return Object.assign({}, ...pieces);
}

/** Configuration for font. */
export type FontSettings = {
    /** CSS font family
     * @default 'Monaco, monospace'
     */
    fontFamily?: string;
    /** CSS font weight
     * @default 'normal'
     */
    fontWeight?: string | number;
    /** Font size in pixels. This option is only applied for generating `fontAtlas`, it does not impact the size of displayed text labels. Larger `fontSize` will give you a sharper look when rendering text labels with very large font sizes. But larger `fontSize` requires more time and space to generate the `fontAtlas`.
     * @default 64
     */
    fontSize?: number;
    /** Whitespace buffer around each side of the character. In general, bigger `fontSize` requires bigger `buffer`. Increase `buffer` will add more space between each character when layout `characterSet` in `fontAtlas`. This option could be tuned to provide sufficient space for drawing each character and avoiding overlapping of neighboring characters.
     * @default 4
     */
    buffer?: number;
    /** Flag to enable / disable `sdf`. [`sdf` (Signed Distance Fields)](http://cs.brown.edu/people/pfelzens/papers/dt-final.pdf) will provide a sharper look when rendering with very large or small font sizes. `TextLayer` integrates with [`TinySDF`](https://github.com/mapbox/tiny-sdf) which implements the `sdf` algorithm.
     * @default false
     */
    sdf?: boolean;
    /** How much of the radius (relative) is used for the inside part the glyph. Bigger `cutoff` makes character thinner. Smaller `cutoff` makes character look thicker. Only applies when `sdf: true`.
     * @default 0.25
     */
    cutoff?: number;
    /** How many pixels around the glyph shape to use for encoding distance. Bigger radius yields higher quality outcome. Only applies when `sdf: true`.
     * @default 12
     */
    radius?: number;
    /** How much smoothing to apply to the text edges. Only applies when `sdf: true`.
     * @default 0.1
     */
    smoothing?: number;
};

export const defaultFontSettings = {
    fontFamily: "Monaco, monospace",
    fontWeight: "normal",
    fontSize: 64,
    buffer: 4,
    sdf: false,
    cutoff: 0.25,
    radius: 12,
    smoothing: 0.1,
} as const satisfies FontSettings;
