// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Layer } from "@deck.gl/core";
import type { PartialPrimitiveOf, PointGeometry, Primitive, TextStyle } from "../../primitive";
import type { Device } from "@luma.gl/core";
import { VariableTable } from "../../gpu/VariableTable";
import type { Allocation } from "../../gpu/Allocator";
import { AtlasTexture } from "../../gpu/AtlasTexture";
import { FontAtlas, type FontSettings } from "./FontAtlas";
import { transformParagraph } from "./text.util";
import { MixedMultiIconLayer } from "./MixedMultiIconLayer";
import { InstancedBatch } from "../InstancedBatch";
import {
    DataFilterExtension,
    type CollisionFilterExtensionProps,
    type DataFilterExtensionProps,
} from "@deck.gl/extensions";
import { TextCollisionExtension } from "@opencop/layers";
import { filterValue, sharedLayerProps } from "../props.util";
import type { SceneProps } from "../../SceneProps";

/** The text outline width, px, that every map label is drawn with. */
export const OUTLINE_WIDTH = 5 as const;
/** The font stack every map label is drawn with, CJK-capable. */
export const FONT_FAMILY =
    '"Noto Sans", "Noto Sans CJK SC", "PingFang SC", "Microsoft YaHei", "Heiti SC", "WenQuanYi Micro Hei", sans-serif' as const;
/** The SDF glyph radius and edge smoothing every map label is drawn with. */
export const SDF_RADIUS = 16 as const;
export const SDF_SMOOTHING = 0.17 as const;
export const OUTLINE_COLOR = [0, 0, 0, 255] as const;
export const LINE_HEIGHT = 1 as const;
export const MAX_WIDTH = -1 as const;
export const WORD_BREAK: "break-word" | "break-all" = "break-word";

export const FONT_SETTINGS = {
    fontFamily: FONT_FAMILY,
    sdf: true,
    radius: SDF_RADIUS,
    smoothing: SDF_SMOOTHING,
    fontSize: 64,
} as const satisfies FontSettings;

export type TextPrimitive = Primitive<PointGeometry, TextStyle>;
export const TextPrimitive = {
    is: (primitive: Primitive): primitive is TextPrimitive =>
        primitive.geometry.type === "point" && primitive.style.type === "text",
};

export type TextBatchProps = {
    device: Device;
    capacity?: number;
};

const TEXT_ANCHOR = {
    start: 1,
    middle: 0,
    end: -1,
} as const;

const ALIGNMENT_BASELINE = {
    top: 1,
    center: 0,
    bottom: -1,
} as const;

type TextDef = {
    text: string;
    anchor: TextStyle["anchor"];
    baseline: TextStyle["baseline"];
};

/** Text filters on two channels. */
const FILTER_SIZE = 2;

// Shared instances: deck.gl compares extensions by instance.
const FILTER_EXTENSION = new DataFilterExtension({ filterSize: FILTER_SIZE });
const COLLISION_EXTENSION = new TextCollisionExtension();

/** Collision group every text layer declutters within. */
const COLLISION_GROUP = "text";

export class TextBatch extends InstancedBatch<TextPrimitive> {
    public readonly geometry = "point";
    public readonly style = "text";

    protected readonly table: VariableTable;

    private textOf = new Map<string, TextDef>();
    private usersOf = new Map<string, Set<string>>();
    private readonly atlas: FontAtlas;
    private readonly texture: AtlasTexture;

    constructor(props: TextBatchProps) {
        super(true);
        const { device, capacity } = props;
        this.table = new VariableTable({
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
                    // Only need to tombstone size to get degenerate quad.
                    tombstoneValue: [0],
                },
                angles: {
                    type: "f32",
                    size: 1,
                },
                colors: {
                    type: "unorm8",
                    size: 4,
                },
                charDefs: {
                    type: "f32",
                    size: 7,
                },
                offsets: {
                    type: "f32",
                    size: 2,
                },
                clipRects: {
                    type: "f32",
                    size: 4,
                    tombstoneValue: [0, 0, -1, -1],
                },
                billboards: {
                    type: "u8",
                    size: 1,
                },
                // Inverted so that zero — an unwritten slot, or a layer not
                // supplying the attribute — means the collision default.
                noCollide: {
                    type: "u8",
                    size: 1,
                },
                filters: {
                    type: "f32",
                    size: FILTER_SIZE,
                },
                pickingColors: {
                    type: "unorm8",
                    size: 4,
                },
            },
        });
        this.atlas = new FontAtlas({ settings: FONT_SETTINGS });
        this.texture = new AtlasTexture({
            device,
            atlas: this.atlas,
        });
    }

    public flush(): boolean {
        const changed = this.atlas.consumePlacements();

        if (changed.kind === "keys") {
            for (const key of changed.keys) {
                const users = this.usersOf.get(key);
                if (!users) continue;
                for (const user of users) {
                    const def = this.textOf.get(user);
                    if (def) this.writeCharDefs(this.allocations.get(user)!, def);
                }
            }
        } else if (changed.kind === "all") {
            for (const [user, allocation] of this.allocations) {
                const def = this.textOf.get(user);
                if (def) this.writeCharDefs(allocation, def);
            }
        }

        return [changed.kind !== "none", this.texture.flush(), super.flush()].reduce(
            (acc, v) => acc || v,
            false,
        );
    }

    public render({ text = {} }: SceneProps): Layer {
        const bindings = this.table.bindings();
        const { filterRange, collisionEnabled = true, sizeScale = 1, ...rest } = text;
        const { extensions, ...shared } = sharedLayerProps(rest, { depthCompare: "less" });
        if (filterRange) extensions.push(FILTER_EXTENSION);
        return new MixedMultiIconLayer<
            unknown,
            DataFilterExtensionProps & CollisionFilterExtensionProps
        >({
            ...shared,
            id: "text-batch-layer",
            numInstances: this.table.extent,
            data: {
                length: 0,
                attributes: {
                    instancePositions: bindings.positions,
                    instanceSizes: bindings.sizes,
                    instanceIconDefs: bindings.charDefs,
                    instanceColors: bindings.colors,
                    instanceAngles: bindings.angles,
                    instancePixelOffset: bindings.offsets,
                    instanceClipRect: bindings.clipRects,
                    instanceBillboards: bindings.billboards,
                    instanceNoCollide: bindings.noCollide,
                    filterValues: bindings.filters,
                    instancePickingColors: bindings.pickingColors,
                },
            },
            extensions: [...extensions, COLLISION_EXTENSION],
            pickable: false,
            iconAtlas: this.texture.gpu,
            iconMapping: this.atlas.placements,
            billboard: true,
            sizeUnits: "pixels",
            sizeScale,
            fontSize: this.atlas.settings.fontSize,
            sdf: this.atlas.settings.sdf,
            smoothing: this.atlas.settings.smoothing,
            // The SDF encodes distance relative to its radius, as in deck.gl's `TextLayer`.
            outlineWidth: OUTLINE_WIDTH / this.atlas.settings.radius,
            outlineColor: OUTLINE_COLOR,
            filterRange,
            collisionGroup: COLLISION_GROUP,
            collisionEnabled,
        });
    }

    public override destroy(): void {
        super.destroy();
        this.texture.destroy();
    }

    protected slots(primitive: TextPrimitive): number {
        return Array.from(primitive.style.text).length;
    }

    protected write(primitive: TextPrimitive, allocation: Allocation) {
        const { offset } = allocation;
        const { id, geometry, style } = primitive;
        const {
            positions,
            sizes,
            angles,
            colors,
            offsets,
            billboards,
            noCollide,
            filters,
            pickingColors,
        } = this.table.attributes;

        const chars = Array.from(style.text).length;
        const range = { start: offset, end: offset + chars };
        pickingColors.fill(this.pickingIndex!.encode(id), range);
        positions.fill(geometry.positions, range);
        sizes.fill([style.size], range);
        angles.fill([style.angle ?? 0], range);
        colors.fill(style.color, range);
        offsets.fill(style.offset ?? [0, 0], range);
        billboards.fill([style.billboard ? 1 : 0], range);
        noCollide.fill([style.collides === false ? 1 : 0], range);
        filters.fill(filterValue(primitive.filter, FILTER_SIZE), range);

        const def = this.textOf.get(id)!;
        this.writeCharDefs(allocation, def);
    }

    protected patch(primitive: PartialPrimitiveOf<TextPrimitive>, allocation: Allocation): void {
        const { offset, requested } = allocation;
        const { id, geometry, style } = primitive;
        const { positions, sizes, angles, colors, offsets, billboards, noCollide, filters } =
            this.table.attributes;

        const range = { start: offset, end: offset + requested };
        if (geometry?.positions) positions.fill(geometry.positions, range);
        if (style?.size !== undefined) sizes.fill([style.size], range);
        if (style?.angle !== undefined) angles.fill([style.angle], range);
        if (style?.color) colors.fill(style.color, range);
        if (style && "offset" in style) offsets.fill(style.offset ?? [0, 0], range);
        if (style?.billboard !== undefined) billboards.fill([style.billboard ? 1 : 0], range);
        if (style?.collides !== undefined)
            noCollide.fill([style.collides === false ? 1 : 0], range);
        if (primitive.filter !== undefined)
            filters.fill(filterValue(primitive.filter, FILTER_SIZE), range);

        if (style?.anchor || style?.baseline || style?.text) {
            let def = this.textOf.get(id)!;
            def = {
                text: style?.text ?? def.text,
                anchor: style?.anchor ?? def.anchor,
                baseline: style?.baseline ?? def.baseline,
            };
            this.textOf.set(id, def);
            this.writeCharDefs(allocation, def);
        }
    }

    protected override paint(color: ArrayLike<number>, allocation: Allocation): void {
        const { pickingColors } = this.table.attributes;
        const { offset, requested } = allocation;
        pickingColors.fill(color, { start: offset, end: offset + requested });
        pickingColors.flush();
    }

    protected override onAdd(primitive: TextPrimitive): void {
        this.acquireCharacters(primitive.id, primitive);
    }

    protected override onUpdate(primitive: TextPrimitive): void {
        this.releaseCharacters(primitive.id);
        this.acquireCharacters(primitive.id, primitive);
    }

    protected override onRemove(id: string): void {
        this.releaseCharacters(id);
    }

    private acquireCharacters(id: string, primitive: TextPrimitive): void {
        const { text, anchor, baseline } = primitive.style;
        this.textOf.set(id, { text, anchor, baseline });
        for (const char of text) {
            this.atlas.acquire(char);
            const users = this.usersOf.get(char);
            if (users) users.add(id);
            else this.usersOf.set(char, new Set([id]));
        }
    }

    private releaseCharacters(id: string): void {
        const textOf = this.textOf.get(id);
        if (!textOf) return;
        this.textOf.delete(id);
        const { text } = textOf;

        for (const char of text) {
            const users = this.usersOf.get(char);
            if (users) users.delete(id);
            if (users?.size === 0) this.usersOf.delete(char);
            this.atlas.release(char);
        }
    }

    private writeCharDefs(allocation: Allocation, def: TextDef) {
        const { text, anchor, baseline } = def;
        const { charDefs } = this.table.attributes;
        const { offset } = allocation;

        const {
            x,
            y,
            rowWidth,
            size: [, height],
        } = transformParagraph({
            paragraph: text,
            baselineOffset: this.atlas.baselineOffset,
            lineHeight: LINE_HEIGHT * FONT_SETTINGS.fontSize,
            maxWidth: MAX_WIDTH,
            wordBreak: WORD_BREAK,
            placements: this.atlas.placements,
        });

        const anchorX = TEXT_ANCHOR[anchor ?? "middle"];
        const anchorY = ALIGNMENT_BASELINE[baseline ?? "center"];

        const chars = x.length;
        const offsets: { x: number; y: number }[] = [];

        for (let i = 0; i < chars; i++) {
            offsets.push({
                // Shift the entire row left or right depending on anchor.
                x: ((anchorX - 1) * rowWidth[i]) / 2 + x[i],
                // Shift paragraph up or down depending on anchor.
                y: ((anchorY - 1) * height) / 2 + y[i],
            });
        }

        let i = 0;
        for (const char of Array.from(text)) {
            const placement = this.atlas.placement(char);
            if (!placement) {
                charDefs.write(offset + i, [0, 0, 0, 0, 0, 0, 0]);
                i++;
                continue;
            }

            const { width, height, anchorY, x, y } = placement;
            const centerToBaseline = height / 2 - anchorY;
            charDefs.write(offset + i, [
                offsets[i].x,
                // Shift character to row baseline
                centerToBaseline +
                    // Shift row baseline to its position in paragraph
                    offsets[i].y,
                x,
                y,
                width,
                height,
                1,
            ]);
            i++;
        }
    }
}
