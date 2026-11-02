// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Logger from "js-logger";
import { AtlasTexture } from "../../gpu/AtlasTexture";
import { FixedTable } from "../../gpu/FixedTable";
import type {
    Color,
    IconStyle,
    PartialPrimitiveOf,
    PointGeometry,
    Primitive,
} from "../../primitive";
import { IconAtlas, type IconPlacement } from "./IconAtlas";
import type { Device } from "@luma.gl/core";
import { load } from "@loaders.gl/core";
import { ImageLoader } from "@loaders.gl/images";
import type { Layer } from "@deck.gl/core";
import type { Allocation } from "../../gpu/Allocator";
import { InstancedBatch } from "../InstancedBatch";
import { pickableMixedIcon } from "./MixedIconLayer";
import { OUTLINE_COLUMN, outlineExtensions, outlineValue } from "../outline.util";
import { FILTER_COLUMN, filterValue, sharedLayerProps } from "../props.util";
import type { SceneProps } from "../../SceneProps";

const logger = Logger.get("icon-render-store");

export type IconPrimitive = Primitive<PointGeometry, IconStyle>;
export const IconPrimitive = {
    is: (primitive: Primitive): primitive is IconPrimitive =>
        primitive.geometry.type === "point" && primitive.style.type === "icon",
};

export type IconBatchProps = {
    device: Device;
    capacity?: number;
    /** Atlas edge length, clamped to what the device can hold. Default 2048 (16MB). */
    atlasSize?: number;
    /** Cell edge length; `(atlasSize / cellSize) ** 2` is how many icons fit. Default 128. */
    cellSize?: number;
};

const DEFAULT_ATLAS_SIZE = 2048;
const DEFAULT_CELL_SIZE = 128;
const OPAQUE_WHITE: Color = [255, 255, 255, 255];

export class IconBatch extends InstancedBatch<IconPrimitive> {
    public readonly geometry = "point";
    public readonly style = "icon";

    protected readonly table: FixedTable;

    private readonly iconOf = new Map<string, string>();
    private readonly usersOf = new Map<string, Set<string>>();
    private readonly texture: AtlasTexture;
    private readonly atlas: IconAtlas;

    constructor(props: IconBatchProps) {
        super(true);
        const { device, capacity, cellSize = DEFAULT_CELL_SIZE } = props;
        this.table = new FixedTable({
            device,
            capacity,
            attributes: {
                positions: {
                    type: "f64",
                    size: 3,
                },
                sizes: {
                    type: "f32",
                    size: 1,
                },
                angles: {
                    type: "f32",
                    size: 1,
                },
                colors: {
                    type: "unorm8",
                    size: 4,
                },
                iconDefs: {
                    type: "f32",
                    size: 7,
                },
                offsets: {
                    type: "f32",
                    size: 2,
                },
                outlines: OUTLINE_COLUMN,
                filters: FILTER_COLUMN,
                billboards: {
                    type: "u8",
                    size: 1,
                },
                pickingColors: {
                    type: "unorm8",
                    size: 4,
                },
            },
        });

        this.atlas = new IconAtlas({
            // WebGL2 only guarantees 2048, so never ask for more than the device has.
            size: Math.min(
                props.atlasSize ?? DEFAULT_ATLAS_SIZE,
                device.limits.maxTextureDimension2D,
            ),
            cellSize,
            fetch: (source) => load(source, ImageLoader),
        });

        this.texture = new AtlasTexture({ device, atlas: this.atlas, size: this.atlas.cellSize });
    }

    public override flush(): boolean {
        const changed = this.atlas.consumePlacements();
        if (changed.kind === "keys") {
            const { keys } = changed;
            for (const iconId of keys) {
                const users = this.usersOf.get(iconId);
                if (!users) continue;
                const placement = this.atlas.placement(iconId);
                for (const user of users) {
                    const allocation = this.allocations.get(user);
                    if (allocation) this.writeIconDef(allocation.offset, placement);
                }
            }
        }

        return [changed.kind !== "none", super.flush(), this.texture.flush()].reduce(
            (acc, v) => acc || v,
            false,
        );
    }

    public override destroy(): void {
        super.destroy();
        this.texture.destroy();
    }

    public render({ icon = {} }: SceneProps): Layer {
        const bindings = this.table.bindings();
        const { extensions, ...shared } = sharedLayerProps(icon, { depthCompare: "less" });
        return pickableMixedIcon({
            ...shared,
            id: "icon-batch-layer",
            data: {
                length: this.table.extent,
                attributes: {
                    instancePositions: bindings.positions,
                    instanceSizes: bindings.sizes,
                    instanceIconDefs: bindings.iconDefs,
                    instanceColors: bindings.colors,
                    instanceAngles: bindings.angles,
                    instancePixelOffset: bindings.offsets,
                    instancePickingColors: bindings.pickingColors,
                    instanceBillboards: bindings.billboards,
                    outlines: bindings.outlines,
                    filterValues: bindings.filters,
                },
            },
            extensions: [...outlineExtensions(icon), ...extensions],

            iconAtlas: this.texture.gpu,
            iconMapping: this.atlas.placements,

            pickable: true,
            pickingIndex: this.pickingIndex!,

            sizeUnits: "pixels",
            sizeScale: icon.sizeScale ?? 1,
            billboard: true,
        });
    }

    protected slots(): number {
        return 1;
    }

    protected write(primitive: IconPrimitive, allocation: Allocation): void {
        const { id, geometry, style } = primitive;
        const { offset } = allocation;
        const {
            positions,
            sizes,
            angles,
            colors,
            offsets,
            billboards,
            outlines,
            filters,
            pickingColors,
        } = this.table.attributes;

        positions.write(offset, geometry.positions);
        outlines.write(offset, outlineValue(primitive.outline));
        filters.write(offset, filterValue(primitive.filter));
        sizes.write(offset, [style.size ?? 1]);
        angles.write(offset, [style.angle ?? 0]);
        colors.write(offset, style.color ?? OPAQUE_WHITE);
        offsets.write(offset, style.offset ?? [0, 0]);
        billboards.write(offset, [style.billboard ? 1 : 0]);
        pickingColors.write(offset, this.pickingIndex!.encode(id));

        const placement = this.atlas.placement(style.id);
        this.writeIconDef(offset, placement);
    }

    protected patch(primitive: PartialPrimitiveOf<IconPrimitive>, allocation: Allocation): void {
        const { geometry, style } = primitive;
        const { offset } = allocation;
        const { positions, sizes, angles, colors, offsets, billboards, outlines, filters } =
            this.table.attributes;

        if (geometry?.positions) positions.write(offset, geometry.positions);
        if (primitive.outline !== undefined)
            outlines.write(offset, outlineValue(primitive.outline));
        if (primitive.filter !== undefined) filters.write(offset, filterValue(primitive.filter));
        if (style?.size !== undefined) sizes.write(offset, [style.size]);
        if (style?.angle !== undefined) angles.write(offset, [style.angle]);
        if (style && "color" in style) colors.write(offset, style.color ?? OPAQUE_WHITE);
        if (style && "offset" in style) offsets.write(offset, style.offset ?? [0, 0]);
        if (style?.billboard !== undefined) billboards.write(offset, [style.billboard ? 1 : 0]);

        if (style?.id) {
            const placement = this.atlas.placement(style.id);
            this.writeIconDef(offset, placement);
        }
    }

    protected override paint(color: ArrayLike<number>, allocation: Allocation): void {
        const { pickingColors } = this.table.attributes;
        const { offset } = allocation;
        pickingColors.write(offset, color);
        pickingColors.flush();
    }

    protected override onAdd(primitive: IconPrimitive): void {
        this.acquireIcon(primitive);
    }

    protected override onUpdate(primitive: IconPrimitive): void {
        const { id } = primitive;
        if (this.iconOf.get(id) !== primitive.style.id) {
            this.releaseIcon(id);
            this.acquireIcon(primitive);
        }
    }

    protected override onRemove(id: string): void {
        this.releaseIcon(id);
    }

    private acquireIcon(primitive: IconPrimitive): void {
        const {
            id,
            style: { id: iconId },
        } = primitive;
        if (!this.atlas.acquire(primitive.style)) {
            logger.warn(`Icon atlas is full; "${iconId}" will not draw`);
            return;
        }

        this.iconOf.set(id, iconId);
        const users = this.usersOf.get(iconId);
        if (users) users.add(id);
        else this.usersOf.set(iconId, new Set([id]));
    }

    private releaseIcon(id: string): void {
        const iconId = this.iconOf.get(id);
        if (!iconId) return;
        this.iconOf.delete(id);

        const users = this.usersOf.get(iconId);
        users?.delete(id);
        if (users?.size === 0) this.usersOf.delete(iconId);

        this.atlas.release(iconId);
    }

    private writeIconDef(offset: number, placement: IconPlacement): void {
        const { iconDefs } = this.table.attributes;
        iconDefs.write(offset, [
            placement.width / 2 - placement.anchorX,
            placement.height / 2 - placement.anchorY,
            placement.x,
            placement.y,
            placement.width,
            placement.height,
            placement.mask ? 1 : 0,
        ]);
    }
}
