// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type AccessorContext, createIterable, type UpdateParameters } from "@deck.gl/core";
import { _MultiIconLayer, TextLayer } from "@deck.gl/layers";
import type { DataFilterExtensionProps } from "@deck.gl/extensions";
import { access } from "../layer.util";
import { copyTails, fill, grow, writeRepeated } from "../binary.util";
import { BinaryMultiIconLayer } from "./BinaryMultiIconLayer";
import { textAttributes, type TextAttributes } from "./binary-text.util";
import {
    MixedBillboardTextLayer,
    type MixedBillboardTextLayerProps,
} from "../mixed-billboard-text-layer/MixedBillboardTextLayer";
import { propsChanged } from "../change-detection.util";
import { isFiltering, type FilterProps } from "../filter.props";

export type BinaryTextLayerProps<DataT = unknown> = MixedBillboardTextLayerProps<DataT> &
    FilterProps<DataT>;

/**
 * To support viewport culling, we need to swap data out repeatedly. Unfortunately, even `_dataDiff` can't optimize this
 * as we won't have stable data indices. So, we preserve pre-computed buffers for the supplied data and update them based on `_dataDiff`.
 * From there, we supply filtered attributes based off the viewport to the downstream `MultiIconLayer`. This avoids any recomputation
 * from swapping out data.
 */
export class BinaryTextLayer<
    DataT,
    ExtraProps extends object = object,
> extends MixedBillboardTextLayer<DataT, ExtraProps & BinaryTextLayerProps<DataT>> {
    static defaultProps = { ...super.defaultProps };
    static layerName = "BinaryTextLayer";
    static componentName = "BinaryTextLayer";

    declare state: {
        attributes: TextAttributes;
        normalizers: Record<string, (o: DataT, i: AccessorContext<DataT>) => number[]>;
    } & TextLayer["state"];

    initializeState(): void {
        super.initializeState();

        this.setState({
            // Include the per-glyph filter attribute only when a DataFilterExtension is
            // attached (Historical View), so the live path packs/uploads nothing extra.
            attributes: textAttributes(isFiltering(this.props.extensions)),
            characterSet: new Set(),
        });
    }

    updateState(params: UpdateParameters<this>): void {
        const {
            props,
            oldProps,
            changeFlags: { dataChanged, updateTriggersChanged },
        } = params;
        const { numInstances = 0, startIndices = [0] } = this.state;
        super.updateState(params);

        if (
            propsChanged(
                [
                    "getPosition",
                    "getColor",
                    "getSize",
                    "getAngle",
                    "getPixelOffset",
                    "getContentBox",
                    "getBillboard",
                    "getFilterValue",
                ],
                props,
                oldProps,
            )
        ) {
            const normalizers: Record<string, (o: DataT, i: AccessorContext<DataT>) => number[]> = {
                getPosition: (o, i) => fill(access(props.getPosition, o, i), 3),
                getColor: (o, i) => fill(access(props.getColor, o, i), 4, 255),
                getSize: (o, i) => [access(props.getSize, o, i)],
                getAngle: (o, i) => [access(props.getAngle, o, i)],
                getPixelOffset: (o, i) => fill(access(props.getPixelOffset, o, i), 2),
                getContentBox: (o, i) => fill(access(props.getContentBox, o, i), 4),
                getBillboard: (o, i) => [access(props.getBillboard, o, i)],
                instancePickingColors: (_, { index, target }) =>
                    this.encodePickingColor(index, target),
            };

            // Pack a per-glyph filter value only when GPU-filtering (Historical View).
            // The value is the element's `filterValue` (constant across its glyphs), so it
            // is packed once at load and never re-uploaded during scrub — only the
            // DataFilterExtension's `filterRange` uniform changes as the playhead moves.
            const getFilterValue = props.getFilterValue;
            if (isFiltering(props.extensions) && getFilterValue !== undefined) {
                normalizers.getFilterValue = (o, i) => [access(getFilterValue, o, i)];
            }

            this.setState({ normalizers });
        }

        if (Array.isArray(dataChanged)) {
            dataChanged.reduce<{ numInstances: number; startIndices: number[] }>(
                (acc, curr) =>
                    this.updateBinaryAttributes(acc.numInstances, acc.startIndices, curr),
                { numInstances, startIndices },
            );
        } else if (dataChanged) {
            this.updateBinaryAttributes(numInstances, startIndices);
        } else if (updateTriggersChanged) {
            const { all, getText } = updateTriggersChanged;
            if (all || getText) this.updateBinaryAttributes(numInstances, startIndices);
            else {
                Object.keys(updateTriggersChanged).forEach((attrName) =>
                    this.updateBinaryAttribute(
                        attrName as Exclude<
                            keyof TextAttributes,
                            "instancePickingColors" | "getIcon"
                        >,
                    ),
                );
            }
        }
    }

    renderLayers() {
        const {
            attributes,
            numInstances = 0,
            startIndices = [0],
            fontAtlasManager: { atlas, mapping },
        } = this.state;
        const layers = super.renderLayers();
        const multi = layers.find((layer) => layer instanceof _MultiIconLayer)!;
        const rest = layers.filter((layer) => !(layer instanceof _MultiIconLayer));
        const filtering = isFiltering(this.props.extensions);

        // When GPU-filtering, attach the DataFilterExtension + range to the glyph layer.
        // The per-glyph `getFilterValue` attribute is supplied via `data.attributes` (deck
        // maps the `getFilterValue` accessor key to the extension's `filterValues`
        // attribute); filtered glyphs are discarded in the shader's
        // `DECKGL_FILTER_GL_POSITION` hook. `filterRange` is spread via a typed variable so
        // TS excess-property checks don't reject this extension-only prop on the icon layer.
        const dfeProps: Partial<DataFilterExtensionProps<DataT>> = filtering
            ? { filterRange: this.props.filterRange }
            : {};

        return [
            ...rest,
            new BinaryMultiIconLayer({
                ...multi.props,
                iconAtlas: atlas as never,
                iconMapping: mapping,
                data: {
                    attributes,
                    startIndices,
                    length: numInstances,
                },
                _dataDiff: undefined as never,
                numInstances,
                ...(filtering ? { extensions: this.props.extensions } : null),
                ...dfeProps,
            }),
        ];
    }

    protected getInstanceIconDef(text: string[], offsets: number[]): number[] {
        const iconDefs: number[] = [];
        for (let i = 0; i < text.length; i++) {
            const {
                x,
                y,
                width,
                height,
                anchorY = height / 2,
            } = this.state.fontAtlasManager.mapping![text[i]];

            iconDefs.push(
                offsets[i * 2],
                height / 2 - anchorY + offsets[i * 2 + 1],
                x,
                y,
                width,
                height,
                1,
            );
        }
        return iconDefs;
    }

    private updateBinaryAttributes(
        prevNumInstances: number,
        prevStartIndices: number[],
        dataRange: { startRow?: number; endRow?: number } = {},
    ): { numInstances: number; startIndices: number[] } {
        const { attributes, normalizers } = this.state;
        const { data, getText } = this.props;
        const nextAttributes: TextAttributes = { ...attributes };

        const { startRow = 0, endRow } = dataRange;
        const startIndex = prevStartIndices[startRow];
        const lastIndex = prevStartIndices[prevStartIndices.length - 1];
        const endIndex =
            endRow !== undefined
                ? prevStartIndices[Math.min(prevStartIndices.length - 1, endRow)]
                : lastIndex;
        const tailAttributes = copyTails(attributes, endIndex, prevNumInstances);

        const indices: number[] = [startIndex];
        let currTotal = startIndex;

        const { iterable, objectInfo } = createIterable(data, startRow, endRow);
        for (const object of iterable) {
            objectInfo.index++;
            const index = indices[indices.length - 1];

            const chars = Array.from(getText(object, objectInfo) || "");
            currTotal += chars.length;
            indices.push(currTotal);

            // @ts-expect-error using private method on TextLayer.
            const offsets = this.getIconOffsets(object, objectInfo);
            const iconDefs = this.getInstanceIconDef(chars, offsets);
            const nextIconAttr = grow(nextAttributes.getIcon, currTotal);
            writeRepeated(nextIconAttr, iconDefs, index, 1);
            nextAttributes["getIcon"] = nextIconAttr;

            for (const [key, fn] of Object.entries(normalizers)) {
                const attrName = key as keyof TextAttributes;
                // Non-null: normalizer keys are a subset of the attribute schema
                // (both gated on the same `filtering` flag), so the attribute exists.
                const attr = nextAttributes[attrName]!;
                const nextAttr = grow(attr, currTotal);
                writeRepeated(nextAttr, fn(object, objectInfo), index, chars.length);
                nextAttributes[attrName] = nextAttr;
            }
        }

        const nextStartIndices = prevStartIndices.slice(0, startRow).concat(indices);
        const diff = currTotal - endIndex;
        const tailStartRow = (endRow ?? prevStartIndices.length - 1) + 1;
        for (const index of prevStartIndices.slice(tailStartRow)) {
            nextStartIndices.push(index + diff);
        }

        // Restore tail ends of buffers
        for (const [key, copied] of Object.entries(tailAttributes)) {
            const attrName = key as keyof TextAttributes;
            // Non-null: `tailAttributes` is copied from the same attribute set.
            const attr = nextAttributes[attrName]!;
            const nextAttr = grow(attr, nextStartIndices[nextStartIndices.length - 1]);
            nextAttr.value.set(copied.value, currTotal * attr.size);
            nextAttributes[attrName] = nextAttr;
        }

        this.setState({
            attributes: nextAttributes,
        });

        return {
            numInstances: prevNumInstances + diff,
            startIndices: nextStartIndices,
        };
    }

    private updateBinaryAttribute(
        attrName: Exclude<keyof TextAttributes, "instancePickingColors" | "getIcon">,
    ) {
        const { data } = this.props;
        const {
            startIndices = [0],
            normalizers,
            attributes: { [attrName]: attr },
        } = this.state;
        // The filter attribute has no update trigger (its value is constant per element),
        // so it never routes here; guard anyway since the schema makes it optional.
        if (!attr) return;
        const normalizer = normalizers[attrName];

        const { iterable, objectInfo } = createIterable(data);
        for (const object of iterable) {
            objectInfo.index++;
            const startIndex = startIndices[objectInfo.index];
            const endIndex = startIndices[objectInfo.index + 1];
            const instances = endIndex - startIndex;

            const updated = normalizer(object, objectInfo);
            writeRepeated(attr, updated, startIndex, instances);
        }

        this.setState({
            attributes: {
                ...this.state.attributes,
                [attrName]: { ...attr },
            },
        });
    }
}
