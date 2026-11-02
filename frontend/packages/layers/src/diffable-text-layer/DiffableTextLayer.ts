// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    type _ConstructorOf,
    type Accessor,
    type Attribute,
    createIterable,
    type Layer,
} from "@deck.gl/core";
import { _MultiIconLayer, TextLayer, type TextLayerProps } from "@deck.gl/layers";
import type { TypedArray } from "@math.gl/types";
import { MixedBillboardMultiIconLayer } from "../mixed-billboard-text-layer/MixedBillboardMultiIconLayer";

// Patches deck.gl's `_MultiIconLayer` to survive partial updates where rows
// change length. The default `_dataDiff` updater only writes the changed range;
// when a row's character count changes, subsequent rows' `startIndices` shift
// but their data isn't relocated → garbled glyphs in trailing rows.
//
// Approach: snapshot the buffer tail at its OLD position before deck.gl's
// updater runs, then re-blit it at the NEW position after. We track previous
// `startIndices`/`numInstances` in state since deck.gl doesn't expose them.
//
// Fragile: reaches into private `attribute.settings.update`,
// `attribute.startIndices`, `attribute.updateSubBuffer`, and overrides
// `_updateAttributes`. A deck.gl bump can break this silently — symptom is
// visually-wrong glyphs in partial-update paths, not a crash.

// Remove when: deck.gl improves support for _dataDiff,
export class DiffableMultiIconLayer<
    DataT = unknown,
    ExtraProps extends object = object,
> extends MixedBillboardMultiIconLayer<DataT, ExtraProps> {
    static layerName = "DiffableMultiIconLayer";

    // Previous frame's `startIndices`/`numInstances`, captured at the end of
    // `_updateAttributes`. Used to locate the old tail position before deck.gl
    // rewrites the buffer.
    declare state: {
        oldStartIndices: number[];
        oldNumInstances: number;
    } & _MultiIconLayer<DataT, ExtraProps>["state"];

    initializeState() {
        super.initializeState();
        this.setState({
            oldStartIndices: null,
            oldNumInstances: 0,
        });

        // Monkey-patch each attribute's `settings.update` — that's the per-attribute
        // updater deck.gl invokes inside `_updateAttributes`, and there's no
        // class-level hook for it.
        const am = this.getAttributeManager()!;

        for (const name of Object.keys(am.attributes)) {
            const attr = am.attributes[name];
            const original = attr.settings.update;

            const wrapped = function (
                this: DiffableMultiIconLayer,
                attribute: Attribute,
                opts: {
                    data: unknown;
                    startRow: number;
                    endRow: number;
                    numInstances: number;
                    props: unknown;
                },
            ) {
                const { oldStartIndices, oldNumInstances } = this.state;

                const valueBefore = attribute.value;
                const sizeBefore: number = attribute.size;
                const valueLenBefore = valueBefore?.length ?? 0;

                let snapshot: TypedArray | undefined;
                let restoreTargetEl = 0;
                let newTailStartEl = 0;

                // Only run for partial updates with rows after the diff range.
                // Full rebuilds have endRow === Infinity and no tail to preserve.
                const endRowFinite =
                    typeof opts.endRow === "number" && Number.isFinite(opts.endRow);
                const endRowInRange =
                    oldStartIndices && endRowFinite && opts.endRow < oldStartIndices!.length;

                let oldTailStart = -1;
                let newTailStart = -1;
                let tailLenVerts = -1;

                if (oldStartIndices && attribute.startIndices && endRowFinite && endRowInRange) {
                    // Snapshot the tail at its OLD position. If old/new tail
                    // positions match, no shift happened — skip the snapshot.
                    const newStartIndices = attribute.startIndices;
                    oldTailStart = oldStartIndices[opts.endRow];
                    newTailStart = newStartIndices[opts.endRow];
                    tailLenVerts = oldNumInstances - oldTailStart;

                    if (oldTailStart !== newTailStart && tailLenVerts > 0) {
                        const oldStartEl = oldTailStart * sizeBefore;
                        const oldEndEl = oldNumInstances * sizeBefore;
                        if (!!valueBefore && oldEndEl <= valueLenBefore) {
                            snapshot = valueBefore.slice(oldStartEl, oldEndEl) as TypedArray;
                            newTailStartEl = newTailStart * sizeBefore;
                            restoreTargetEl = newTailStartEl;
                        }
                    }
                }

                // Original updater writes the diff range; tail ends up misplaced.
                original?.call(this, attribute, opts);

                // Re-blit the saved tail at its NEW position.
                if (snapshot) {
                    const valueAfter = attribute.value as TypedArray;
                    if (valueAfter && restoreTargetEl + snapshot.length <= valueAfter.length) {
                        valueAfter.set(snapshot, restoreTargetEl);
                        // deck.gl handles the Float64 → double-length Float32
                        // expansion internally for 64-bit attributes.
                        attribute.updateSubBuffer({
                            startOffset: restoreTargetEl,
                            endOffset: restoreTargetEl + snapshot.length,
                        });
                    }
                }
            };

            attr.settings.update = wrapped;
        }
    }

    // Snapshot start indices for the next frame. Copy — deck.gl may mutate.
    _updateAttributes() {
        super._updateAttributes();

        const props = this.props;
        const newPrev = props.startIndices
            ? Array.from(props.startIndices as ArrayLike<number>)
            : null;
        this.setState({
            oldStartIndices: newPrev,
            oldNumInstances: props.numInstances ?? 0,
        });
    }
}

export type DiffableTextLayerProps<DataT = unknown> = TextLayerProps<DataT> & {
    getBillboard: Accessor<DataT, number>;
};

// `TextLayer` with `DiffableMultiIconLayer` substituted for the `characters`
// sub-layer + `dataComparator` forwarded so the sub-layer's partial-update
// path fires.
// @ts-expect-error - Overriding private `_updateText` on the superclass.
export class DiffableTextLayer<
    DataT = unknown,
    ExtraProps extends object = object,
> extends TextLayer<DataT, ExtraProps & DiffableTextLayerProps<DataT>> {
    static layerName = "DiffableTextLayer";

    // Swap in our patched sub-layer for `characters`; pass others through.
    getSubLayerClass<T extends Layer>(
        subLayerId: string,
        defaultClass: _ConstructorOf<T>,
    ): _ConstructorOf<T> {
        if (subLayerId === "characters") {
            const cls = DiffableMultiIconLayer;
            return super.getSubLayerClass(subLayerId, cls) as unknown as _ConstructorOf<T>;
        }
        return super.getSubLayerClass(subLayerId, defaultClass);
    }

    // `TextLayer.renderLayers` doesn't forward `dataComparator` to sub-layers,
    // so they fall back to identity-check and never see `_dataDiff` fire.
    // Inject it here.
    getSubLayerProps(sublayerProps?: { id?: string; [propName: string]: unknown }): unknown {
        const props = super.getSubLayerProps(sublayerProps) as Record<string, unknown>;
        const parentComparator = (this.props as { dataComparator?: unknown }).dataComparator;
        if (parentComparator) {
            props.dataComparator = parentComparator;
        }
        // `TextLayer.renderLayers` forwards only the `billboard` boolean to the
        // characters sub-layer, but our `MixedBillboardMultiIconLayer` adds an
        // `instanceBillboards` attribute keyed on the `getBillboard` accessor.
        // Inject it here (falling back to the static `billboard` flag) so the
        // attribute always resolves to a function.
        if (sublayerProps?.id === "characters") {
            const p = this.props as { getBillboard?: unknown; billboard?: boolean };
            props.getBillboard = p.getBillboard ?? (() => (p.billboard ? 1 : 0));
        }
        return props;
    }

    // @ts-expect-error - Used in `updateState`.
    private _updateText() {
        const { data } = this.props;
        const { getText, characterSet } = this.props;
        let numInstances: number;

        const autoCharacterSet = characterSet === "auto" ? new Set() : undefined;

        const { iterable, objectInfo } = createIterable(data);
        const startIndices = [0];
        numInstances = 0;

        for (const object of iterable) {
            objectInfo.index++;
            // NOTE: EMOJIS NOT SUPPORTED
            const text = getText(object, objectInfo) ?? "";
            if (autoCharacterSet) {
                for (let i = 0; i < text.length; i++) {
                    if (!autoCharacterSet.has(text[i])) autoCharacterSet.add(text[i]);
                }
            }
            numInstances += text.length;
            startIndices.push(numInstances);
        }

        this.setState({
            getText,
            startIndices,
            numInstances,
            characterSet: autoCharacterSet || characterSet,
        });
    }
}
