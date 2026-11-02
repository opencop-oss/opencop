// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { LuRadius } from "react-icons/lu";
import {
    MdArrowOutward,
    MdOutlineHexagon,
    MdOutlinePolyline,
    MdOutlineRectangle,
} from "react-icons/md";
import { RxAngle } from "react-icons/rx";
import { TbPointFilled } from "react-icons/tb";
import type { ContributionContext, ElementMapper } from "@opencop/contributions";
import { lineSpec, pointSpec, shapeSpec } from "./style-specs";
import { kits } from "@opencop/tools";
import { ELEMENT_STORE_ID } from "@opencop/tools";
import type { Element } from "@opencop/elements";
import { AngleMapper } from "./mappers/AngleMapper";
import { ArrowMapper } from "./mappers/ArrowMapper";
import { CircleMapper } from "./mappers/CircleMapper";
import { EllipseMapper } from "./mappers/EllipseMapper";
import { LineMapper } from "./mappers/LineMapper";
import { PointMapper } from "./mappers/PointMapper";
import { PolygonMapper } from "./mappers/PolygonMapper";
import { RectangleMapper } from "./mappers/RectangleMapper";
import {
    drawArrowPreview,
    drawCirclePreview,
    drawClosedPathPreview,
    drawOpenPathPreview,
    drawPointPreview,
    shapePreview,
    type ShapeDrawer,
} from "./shape-preview";
import { ArrowGlyph, EllipseGlyph, LineGlyph, PointGlyph, PolygonGlyph } from "./details-glyphs";
import { arrowPosition, centerPosition } from "./details-positions";
import { AngleEditForm, ArrowEditForm, CircleEditForm } from "./edit-forms/shape-edit-forms";
import { ShapeSummary } from "./ShapeSummary";
import { AngleMenuBlock } from "./AngleMenuBlock";

/**
 * The generic-shape subject: point / line / arrow / angle / polygon / circle /
 * rectangle (+ the wire-only ellipse mapper). It contributes its full concern
 * set — draw tools (each from its built-in draw kit) and scene mappers — through
 * the one {@link ContributionContext}, exactly as a plugin's `install(ctx)`
 * would. This replaces the three scattered central registrars (native tools,
 * the shape slice of the renderer list, the built-in edit handlers).
 */

const SHAPES = "shapes";

/** Every element type this subject draws, for the contributions they all share. */
const SHAPE_TYPES = [
    "point",
    "line",
    "arrow",
    "angle",
    "polygon",
    "circle",
    "rectangle",
    "ellipse",
];

/**
 * A shape's mapper, with its thumbnail and its text toggle. `textOrder` places
 * the toggle in the map settings.
 */
function shapeMapper<E extends Element, S>(
    mapper: ElementMapper<E, S>,
    draw: ShapeDrawer,
    textOrder: number,
): ElementMapper<E, S> {
    return {
        type: mapper.type,
        selector: mapper.selector,
        map: (element, state) => mapper.map(element, state),
        preview: (element) => shapePreview(draw, element),
        textSetting: { labelKey: `mapSettings.textLabels.${mapper.type}`, order: textOrder },
    };
}

export function installShapes(ctx: ContributionContext): void {
    ctx.registerTool({
        id: "pointTool",
        label: "point",
        shortcut: "p",
        icon: TbPointFilled,
        group: SHAPES,
        order: 10,
        produces: { store: ELEMENT_STORE_ID, type: "point" },
        style: pointSpec,
        draw: kits.point,
    });
    ctx.registerTool({
        id: "lineTool",
        label: "line",
        shortcut: "l",
        icon: MdOutlinePolyline,
        group: SHAPES,
        order: 20,
        produces: { store: ELEMENT_STORE_ID, type: "line" },
        style: lineSpec,
        draw: kits.line,
    });
    ctx.registerTool({
        id: "arrowTool",
        label: "arrow",
        shortcut: "a",
        icon: MdArrowOutward,
        group: SHAPES,
        order: 30,
        produces: { store: ELEMENT_STORE_ID, type: "arrow" },
        style: lineSpec,
        draw: kits.arrow,
    });
    ctx.registerTool({
        id: "angleTool",
        label: "angle",
        shortcut: "n",
        icon: RxAngle,
        group: SHAPES,
        order: 40,
        produces: { store: ELEMENT_STORE_ID, type: "angle" },
        style: lineSpec,
        draw: kits.angle,
    });
    ctx.registerTool({
        id: "polygonTool",
        label: "polygon",
        shortcut: "g",
        icon: MdOutlineHexagon,
        group: SHAPES,
        order: 50,
        produces: { store: ELEMENT_STORE_ID, type: "polygon" },
        style: shapeSpec,
        draw: kits.polygon,
    });
    ctx.registerTool({
        id: "circleTool",
        label: "circle",
        shortcut: "c",
        icon: LuRadius,
        group: SHAPES,
        order: 60,
        produces: { store: ELEMENT_STORE_ID, type: "circle" },
        style: shapeSpec,
        draw: kits.circle,
    });
    ctx.registerTool({
        id: "rectangleTool",
        label: "rectangle",
        icon: MdOutlineRectangle,
        group: SHAPES,
        order: 65,
        produces: { store: ELEMENT_STORE_ID, type: "rectangle" },
        style: shapeSpec,
        draw: kits.rectangle,
    });

    ctx.registerElementMapper(shapeMapper(new PointMapper(), drawPointPreview, 80));
    ctx.registerElementMapper(shapeMapper(new LineMapper(), drawOpenPathPreview, 90));
    ctx.registerElementMapper(shapeMapper(new ArrowMapper(), drawArrowPreview, 50));
    ctx.registerElementMapper(shapeMapper(new AngleMapper(), drawOpenPathPreview, 60));
    ctx.registerElementMapper(shapeMapper(new PolygonMapper(), drawClosedPathPreview, 10));
    ctx.registerElementMapper(shapeMapper(new CircleMapper(), drawCirclePreview, 40));
    ctx.registerElementMapper(shapeMapper(new RectangleMapper(), drawClosedPathPreview, 20));
    // Ellipse has no draw tool (it arrives from the wire) — mapper only.
    ctx.registerElementMapper(shapeMapper(new EllipseMapper(), drawClosedPathPreview, 30));

    // The details panel: glyphs where the host's default (a circle for a point
    // geometry, else a rectangle) is not the shape's own; positions for shapes
    // that are not a single point; geometry inputs for the shapes that have them.
    ctx.registerDetailsHeader({ type: "point", typeIcon: PointGlyph });
    ctx.registerDetailsHeader({ type: "line", typeIcon: LineGlyph });
    ctx.registerDetailsHeader({ type: "polygon", typeIcon: PolygonGlyph });
    ctx.registerDetailsHeader({ type: "ellipse", typeIcon: EllipseGlyph });
    ctx.registerDetailsHeader({ type: "arrow", typeIcon: ArrowGlyph });
    ctx.registerDetailsPosition({ type: "arrow", position: arrowPosition });
    for (const type of ["circle", "ellipse"]) {
        ctx.registerDetailsPosition({ type, position: centerPosition });
    }
    // The map's right-click menu: drop a point where it was opened, and an
    // angle's reading with a choice of angle unit.
    ctx.registerContextMenuItem({
        id: "opencop.shapes.drop-point",
        order: 10,
        target: { kind: "ground" },
        mutates: true,
        labelKey: "contextMenu.dropPoint",
        activate: { toolId: "pointTool", placeAt: true },
    });
    ctx.registerContextMenuItem({
        id: "opencop.shapes.angle",
        order: 20,
        target: { kind: "object", type: "angle" },
        Content: AngleMenuBlock,
    });

    for (const type of SHAPE_TYPES) {
        ctx.registerDetailsInfoSection({
            id: "opencop.shapes.summary",
            type,
            order: 30,
            Component: ShapeSummary,
        });
    }
    ctx.registerDetailsEditForm({
        id: "opencop.shapes.circle",
        order: 10,
        type: "circle",
        Component: CircleEditForm,
    });
    ctx.registerDetailsEditForm({
        id: "opencop.shapes.arrow",
        order: 10,
        type: "arrow",
        Component: ArrowEditForm,
    });
    ctx.registerDetailsEditForm({
        id: "opencop.shapes.angle",
        order: 10,
        type: "angle",
        Component: AngleEditForm,
    });
}
