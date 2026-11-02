// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { CompositeLayerProps } from "@deck.gl/core";
import type { LabelProps } from "./label.props";
import type { MeasurementProps } from "./measurement.props";
import type { TextProps } from "./text.props";
import { TextCollisionExtension } from "./text-collision-extension/text-collision-extension";
import { union } from "lodash";
import type { Element } from "@opencop/elements";
import type { RenderContext } from "./render-context";

/** The text outline width, px, that every map label is drawn with. */
export const TEXT_OUTLINE_WIDTH_PX = 5;
/** The font stack every map label is drawn with, CJK-capable. */
export const FONT_FAMILY =
    '"Noto Sans", "Noto Sans CJK SC", "PingFang SC", "Microsoft YaHei", "Heiti SC", "WenQuanYi Micro Hei", sans-serif';
/** The SDF glyph radius and edge smoothing every map label is drawn with. */
export const SDF_RADIUS = 16;
export const SDF_SMOOTHING = 0.17;
const getElementTextIdentity = (element: Element): string => element.key;

/** The stable identity fields the text-visibility policy reads off whatever is
 * hovered or selected. Keys, not object identity, because live layer datasets
 * hold clones of the store elements the interaction state points at. */
interface TextInteractionElement {
    readonly key: string;
    readonly type: string;
}

/** The subset of {@link RenderContext} the per-source text-visibility policy
 * reads. Stated structurally rather than as a `Pick` so the policy can be
 * resolved — and unit-tested — without assembling a full render context;
 * `RenderContext` is assignable to it. */
interface TextVisibilityContext {
    readonly hovered: Readonly<Record<string, TextInteractionElement>>;
    readonly selected?: TextInteractionElement | null;
    readonly filterManagedSources?: readonly string[];
    readonly textVisibleSources?: readonly string[];
}

/** Hovered/selected elements are exempt from per-source text suppression so
 * the `alwaysVisible` sub-layer (see {@link hoverProps}) still has text to
 * draw. Stable keys survive the clones used by live layer datasets. */
function textSuppressionExemptions(options: TextVisibilityContext): Set<string> {
    const exempt = new Set(Object.values(options.hovered).map((element) => element.key));
    if (options.selected) exempt.add(options.selected.key);
    return exempt;
}

/** Whether each element may render ordinary text under the per-source policy.
 * Unmanaged and opted-in sources remain visible; selection and hover temporarily
 * exempt a managed-off element so its inspection text can still be shown. */
export function sourceTextVisibility<E extends { key: string; source: string }>(
    options: TextVisibilityContext,
): (d: E) => boolean {
    const managed = new Set(options.filterManagedSources);
    if (managed.size === 0) return () => true;
    const visible = new Set(options.textVisibleSources);
    const exempt = textSuppressionExemptions(options);
    return (d) => !managed.has(d.source) || visible.has(d.source) || exempt.has(d.key);
}

/** Update-trigger values for feature content derived from source text visibility.
 * Interaction identities are included only when they belong to `elementType`,
 * so selecting or hovering an unrelated element does not rebuild this layer. */
export function sourceTextVisibilityUpdateTriggers(
    elementType: string,
    options: TextVisibilityContext,
) {
    return [
        options.filterManagedSources,
        options.textVisibleSources,
        options.selected?.type === elementType ? options.selected : undefined,
        options.hovered[elementType],
    ] as const;
}

/** Label accessor that blanks out labels from a filter-managed source that has
 * not been opted into text visibility, exempting whatever is hovered or
 * selected so it still renders its label. */
export function sourceLabel<E extends { key: string; source: string; getDisplayName(): string }>(
    options: RenderContext,
): (d: E) => string {
    const isVisible = sourceTextVisibility<E>(options);
    return (d) => (isVisible(d) ? d.getDisplayName() : "");
}

/** Measurement counterpart of {@link sourceLabel}: returns empty text for a
 * managed source that is not opted in; null defers to the layer's own formatted measurement. */
export function sourceMeasurement<E extends { key: string; source: string }>(
    options: RenderContext,
): (d: E) => string | null {
    const isVisible = sourceTextVisibility<E>(options);
    return (d) => (isVisible(d) ? null : "");
}

/** Keep the hovered element of a type always visible (exempt from culling). */
export function hoverProps<DataT extends Element = Element>(
    type: string,
    options: RenderContext,
): { alwaysVisible?: DataT[] } {
    const hovered = options.hovered[type] ? [options.hovered[type]] : [];
    const selected = options.selected?.type === type ? [options.selected] : [];
    return {
        alwaysVisible: union(hovered, selected) as DataT[],
    };
}

/** Label + measurement text props (SDF font, collision, historical filter). */
export function textProps<DataT extends Element = Element>(
    type: string,
    options: RenderContext,
): Partial<
    MeasurementProps<DataT> &
        LabelProps<DataT> &
        TextProps<DataT> &
        Omit<CompositeLayerProps, "data">
> {
    return {
        // Default Text Props
        text: options.settings.textEnabled[type] ?? true,
        characterSet: "auto",
        textSizeUnits: "pixels",
        fontFamily: FONT_FAMILY,
        textOutlineWidth: TEXT_OUTLINE_WIDTH_PX,
        textOutlineColor: [0, 0, 0, 255],
        zoomThreshold: options.settings.textZoomThreshold,
        fontSettings: {
            sdf: true,
            radius: SDF_RADIUS,
            smoothing: SDF_SMOOTHING,
        },

        // Label
        getLabelColor: [255, 255, 255, 255],
        getLabelSize: options.settings.textSize,
        getTextIdentity: getElementTextIdentity,

        // Measurement
        getMeasurementColor: [200, 200, 200, 255],
        getMeasurementSize: options.settings.textSize,
        ...(options.measurementSystem ? { measurementSystem: options.measurementSystem } : null),

        updateTriggers: {
            getLabelSize: options.settings.textSize,
            getMeasurementSize: options.settings.textSize,
            getLabel: sourceTextVisibilityUpdateTriggers(type, options),
            getMeasurementText: sourceTextVisibilityUpdateTriggers(type, options),
        },

        // Pass explicit extensions to sub text layers.
        _subLayerProps: {
            // Labels have higher priority than measurements.
            text: {
                // The DataFilterExtension must be added here (not just at the composite
                // level): `_subLayerProps.text.extensions` overrides the auto-forwarded
                // composite extensions for the text sub-layer. Historical View sets
                // `dataFilter`; live leaves it undefined and adds only collision.
                extensions: [
                    new TextCollisionExtension(),
                    ...(options.dataFilter ? [options.dataFilter.extension] : []),
                ],
                collisionGroup: "text",
                // Adds offset to z-buffer to ensure all text is above
                // every other geometry (aside from text itself).
                getPolygonOffset: ({ layerIndex }: { layerIndex: number }) => [
                    0,
                    layerIndex * -10_000,
                ],

                // Use "less" to save performance when encountering overlapping text.
                // Prefer getPolygonOffset + "less" over "always".
                parameters: {
                    depthWriteEnabled: true,
                    depthCompare: "less",
                },
            },
        },
    };
}

/** Depth parameters for terrain draping. */
export function terrainProps(options: RenderContext): Partial<Omit<CompositeLayerProps, "data">> {
    return {
        parameters: {
            depthWriteEnabled: !options.depthCompareAlways,
            depthCompare: options.depthCompareAlways ? "always" : undefined,
        },
    };
}
