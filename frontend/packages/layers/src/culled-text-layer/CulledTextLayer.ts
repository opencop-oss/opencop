// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { DefaultProps, Layer, UpdateParameters } from "@deck.gl/core";
import type { TextLayerProps } from "@deck.gl/layers";
import { areBoundsContained, computeArea, padBounds, type Bounds } from "./culling.util";
import { BinaryTextLayer } from "../binary-text-layer/BinaryTextLayer";
import { attr, grow } from "../binary.util";
import { BinaryMultiIconLayer } from "../binary-text-layer/BinaryMultiIconLayer";
import { textAttributes, type TextAttributes } from "../binary-text-layer/binary-text.util";
import { MixedBillboardTextLayer } from "../mixed-billboard-text-layer/MixedBillboardTextLayer";
import { isFiltering, type FilterProps } from "../filter.props";

export type CullingProps<DataT> = {
    zoomThreshold?: number;
    alwaysVisible?: DataT[];
    paddingFactor?: number;
    minFillFactor?: number;
    culled?: boolean;
    text?: boolean;
};

export type CulledTextLayerProps<DataT = unknown> = CullingProps<DataT> &
    TextLayerProps<DataT> &
    FilterProps<DataT>;

export const defaultCullingProps = {
    culled: true,
    zoomThreshold: 5,
    alwaysVisible: [],
    paddingFactor: 0.5,
    minFillFactor: 0.1,
    text: true,
} as const satisfies DefaultProps<CulledTextLayerProps>;

// Expand 1 viewport in either direction.
export const CULL_EXPANSION_FACTOR = 1;

export class CulledTextLayer<DataT, ExtraProps extends object = object> extends BinaryTextLayer<
    DataT,
    Required<CulledTextLayerProps<DataT>> & ExtraProps
> {
    static defaultProps = { ...BinaryTextLayer.defaultProps, ...defaultCullingProps };
    static layerName = "CulledTextLayer";
    static componentName = "CulledTextLayer";

    declare state: {
        culled: TextAttributes;
        bounds: Bounds;
        zoom: number;
        culledNumInstances: number;
        culledStartIndices: number[];
    } & BinaryTextLayer<DataT, ExtraProps>["state"];

    override initializeState(): void {
        super.initializeState();
        this.setState({
            culled: {
                getPosition: attr("float64", 3),
                getColor: attr("uint8", 4),
                getSize: attr("float32"),
                getAngle: attr("float32"),
                getPixelOffset: attr("float32", 2),
                getContentBox: attr("float32", 4),
                getIcon: attr("float32", 7),
                // Mirror the parent's filter attribute so the culled re-pack carries it.
                ...(isFiltering(this.props.extensions)
                    ? { getFilterValue: attr("float32", 1) }
                    : {}),
            },
            bounds: this.context.viewport.getBounds(),
            zoom: this.context.viewport.zoom,
            culledNumInstances: 0,
            culledStartIndices: [0],
        });
    }

    override shouldUpdateState(params: UpdateParameters<this>): boolean {
        const { culled } = params.props;

        if (
            super.shouldUpdateState(params) ||
            (params.changeFlags.viewportChanged && this.context.viewport.zoom !== this.state.zoom)
        )
            return true;

        if (!culled) return false;

        const { viewport } = this.context;
        const { zoomThreshold } = this.props;
        const wasVisible = this.state.zoom >= zoomThreshold && this.props.visible;
        const isVisible = viewport.zoom >= zoomThreshold && this.props.visible;
        if (wasVisible !== isVisible) return true;
        if (viewport.zoom <= zoomThreshold) return false;
        return this.needsRefresh(viewport.getBounds());
    }

    override updateState(params: UpdateParameters<this>): void {
        super.updateState(params);

        const { viewport } = this.context;
        const {
            changeFlags: { viewportChanged, dataChanged, updateTriggersChanged },
            oldProps: { text: oldText },
        } = params;
        const { zoom } = this.state;
        const { zoomThreshold } = this.props;
        const bounds = viewport.getBounds();

        const textJustEnabled = !oldText;
        const currentZoom = viewport.zoom;
        if (viewportChanged && currentZoom !== zoom) this.setState({ zoom: currentZoom });
        if (!params.props.culled || !params.props.text) return;

        const needsRefresh =
            textJustEnabled ||
            dataChanged ||
            updateTriggersChanged ||
            (viewportChanged && this.needsRefresh(bounds) && viewport.zoom >= zoomThreshold);

        if (needsRefresh) this.cull(bounds);
    }

    override renderLayers() {
        const layers = super.renderLayers();
        const multi = layers.find((layer) => layer instanceof BinaryMultiIconLayer);
        const rest = layers.filter((layer) => !(layer instanceof BinaryMultiIconLayer));
        if (!multi)
            throw new Error(
                "Could not find BinaryMultiIconLayer in parents rendered sublayers – `BinaryMultiIconLayer` is required for culling.",
            );

        return [...rest, this.renderCulledLayer(multi), this.renderVisibleLayer()] as ReturnType<
            BinaryTextLayer<DataT>["renderLayers"]
        >;
    }

    private renderCulledLayer(layer: Layer): Layer | undefined {
        const { culled: isCulled, text: isEnabled } = this.props;
        const { zoom, culled, culledNumInstances, culledStartIndices } = this.state;
        const { zoomThreshold, visible } = this.props;
        if (!isEnabled) return;
        if (!isCulled) return layer;

        return layer.clone({
            ...layer.props,
            visible: zoom >= zoomThreshold ? visible : false,
            data: {
                attributes: culled,
                startIndices: culledStartIndices,
                length: culledNumInstances,
            },
            startIndices: culledStartIndices,
            numInstances: culledNumInstances,
        });
    }

    private renderVisibleLayer(): Layer | false {
        const { culled: isCulled, text: isEnabled, alwaysVisible } = this.props;
        const VisibleLayer = this.getSubLayerClass("alwaysVisible", MixedBillboardTextLayer);

        if ((!isCulled && isEnabled) || !alwaysVisible?.length) return false;

        return new VisibleLayer({
            ...this.props,
            ...this.getSubLayerProps({
                id: "alwaysVisible",
            }),
            extensions: [],
            dataComparator: undefined,
            _dataDiff: undefined,
            data: [...alwaysVisible],
            visible: true,
        });
    }

    private needsRefresh(bounds: Bounds) {
        const {
            props: { minFillFactor },
        } = this;
        const { bounds: currentBounds } = this.state;

        const area = computeArea(bounds);
        const currentArea = computeArea(currentBounds);
        const contained = areBoundsContained(bounds, currentBounds);

        return (contained && area <= currentArea * minFillFactor) || !contained;
    }

    private cull(bounds: Bounds) {
        const { startIndices = [0], attributes } = this.state;
        const {
            props: { paddingFactor },
        } = this;
        const culled = textAttributes(isFiltering(this.props.extensions));

        const paddedBounds = padBounds(bounds, paddingFactor);
        const [minX, minY, maxX, maxY] = paddedBounds;

        let culledNumInstances = 0;
        const culledStartIndices = [0];
        for (let i = 0; i < startIndices.length - 1; i++) {
            const start = startIndices[i];
            const end = startIndices[i + 1];
            const instances = end - start;
            const buffer = attributes.getPosition.value;
            const size = attributes.getPosition.size;

            const x = buffer[start * size];
            const y = buffer[start * size + 1];
            if (minX <= x && x <= maxX && minY <= y && y <= maxY) {
                const culledIndex = culledStartIndices[culledStartIndices.length - 1];
                culledNumInstances += instances;
                culledStartIndices.push(culledNumInstances);

                for (const [key, attr] of Object.entries(attributes)) {
                    const attrName = key as keyof TextAttributes;
                    // Non-null: `culled` is built from `textAttributes(filtering)`, the same
                    // flag that decides whether `attributes` holds the filter attribute.
                    const nextAttr = grow(culled[attrName]!, culledNumInstances);
                    const size = attr.size;
                    nextAttr.value.set(
                        attr.value.subarray(start * size, end * size),
                        culledIndex * size,
                    );
                    culled[attrName] = nextAttr;
                }
            }
        }

        this.setState({
            bounds: paddedBounds,
            culled,
            culledStartIndices,
            culledNumInstances,
        });
    }
}
