// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { TextBox, type TextBoxElement } from "@opencop/elements";
import { kits, MercatorVector, type ControlPoint, type EditModel } from "@opencop/tools";
import { boxFrame } from "./box-geometry.util";
import { textAngle } from "./TextBoxElementMapper";
import { textBoxEditModel } from "./textbox-edit-model";

const rectangle = kits.rectangle.editModel as EditModel;

/** A box whose first edge runs east, wider than it is tall. */
const upright = () => TextBox.create({ positions: [0, 0, 0.05, 0, 0.05, 0.03, 0, 0.03] });

/**
 * The same box with the opposite winding — first edge still east, but the box
 * extends south instead of north. The host rewinds the ring, so the fix has to
 * hold for both.
 */
const reversed = () => TextBox.create({ positions: [0, 0, 0.05, 0, 0.05, -0.03, 0, -0.03] });

const handle = (box: TextBoxElement, index: number): ControlPoint => {
    const positions = box.geometry.getAllPos();
    return { kind: "vertex", index, position: [positions[index * 2], positions[index * 2 + 1]] };
};

/** Drag corner `index` outwards and report the box's own frame afterwards. */
function dragCorner(model: EditModel, box: TextBoxElement, index: number) {
    const snapshot = [...box.geometry.getAllPos()];
    const control = handle(box, index);
    const moved: [number, number] = [control.position[0] + 0.01, control.position[1] + 0.005];

    model.applyHandle(box, control, moved, snapshot);
    return boxFrame(box.geometry.getAllPos())!;
}

describe("textBoxEditModel", () => {
    // The textbox reads slots 0 and 1 as the edge its text runs along, so a
    // drag that re-labels the corners turns the text 90° and re-wraps it.
    describe.each([
        ["drawn one way", upright],
        ["drawn the other way", reversed],
    ])("%s", (_label, make) => {
        it.each([0, 1, 2, 3])("keeps the first edge the long one after dragging corner %i", (i) => {
            const before = boxFrame(make().geometry.getAllPos())!;
            const after = dragCorner(textBoxEditModel, make(), i);

            // The box is wider than it is tall; it must still be after the drag.
            expect(before.width).toBeGreaterThan(before.height);
            expect(after.width).toBeGreaterThan(after.height);
        });
    });

    it("is a real fix, not a restatement of what the host already does", () => {
        // The host model re-labels the corners, so at least one drag turns a
        // wide box into a tall one. If this ever stops being true the wrapper
        // is redundant and should go.
        const turned = [0, 1, 2, 3].filter((i) => {
            const frame = dragCorner(rectangle, upright(), i);
            return frame.width < frame.height;
        });
        expect(turned.length).toBeGreaterThan(0);
    });

    // Dropping a far-edge corner on the line through corners 0 and 1 leaves the
    // box no height. Written, it collapses to three vertices and the host then
    // ignores every later drag on it, so the box can never be repaired.
    it.each([2, 3])("keeps the pre-drag ring when corner %i is dropped flat", (index) => {
        const box = upright();
        const snapshot = [...box.geometry.getAllPos()];

        textBoxEditModel.applyHandle(box, handle(box, index), [0.02, 0], snapshot);

        const after = box.geometry.getAllPos();
        expect(after).toEqual(snapshot);
        expect(boxFrame(after)).not.toBeNull();
    });

    it("is a real guard: the host model degenerates on the same drag", () => {
        // If the host ever stops degenerating here the restore is redundant.
        const degenerate = [2, 3].filter((index) => {
            const box = upright();
            const snapshot = [...box.geometry.getAllPos()];
            rectangle.applyHandle(box, handle(box, index), [0.02, 0], snapshot);
            return !boxFrame(box.geometry.getAllPos());
        });
        expect(degenerate).toEqual([2, 3]);
    });

    it("still accepts a flat drag that only shortens the first edge", () => {
        // Corners 0 and 1 live on that line already: dropping one on it is an
        // ordinary narrowing drag, not a degenerate one.
        const box = upright();
        textBoxEditModel.applyHandle(box, handle(box, 1), [0.02, 0], [...box.geometry.getAllPos()]);

        const frame = boxFrame(box.geometry.getAllPos());
        expect(frame).not.toBeNull();
        expect(frame!.width).toBeLessThan(boxFrame(upright().geometry.getAllPos())!.width);
    });

    it("grows the box so a narrowing drag does not clip the text", () => {
        const box = upright();
        box.getStyle().setText("alpha bravo charlie delta echo foxtrot golf hotel india");
        box.getStyle().setTextSize(400);

        // Pull the far edge in, leaving fewer characters per line and more lines.
        const snapshot = [...box.geometry.getAllPos()];
        const control = handle(box, 1);
        textBoxEditModel.applyHandle(box, control, [0.02, 0], snapshot);

        const frame = boxFrame(box.geometry.getAllPos())!;
        // Taller than the drag asked for, because the text needs the room.
        expect(frame.height).toBeGreaterThan(boxFrame(snapshot)!.height);
    });
});

describe("textBoxEditModel rotate handle", () => {
    const ring = [0, 0, 0.05, 0, 0.05, 0.03, 0, 0.03];

    /** The pointer position after sweeping `degrees` about the centre from the handle's own. */
    function sweptPointer(grab: [number, number], degrees: number): [number, number] {
        const centre = MercatorVector.fromLngLat([0.025, 0.015]);
        const offset = MercatorVector.fromLngLat(grab).subtract(centre);
        const [cos, sin] = [
            Math.cos((degrees * Math.PI) / 180),
            Math.sin((degrees * Math.PI) / 180),
        ];
        const to = centre.clone();
        to.x += offset.x * cos - offset.y * sin;
        to.y += offset.x * sin + offset.y * cos;
        return to.toLngLat().slice(0, 2) as [number, number];
    }

    it.each([60, 150, 210, 300])(
        "turns the text %i degrees and keeps each corner's slot",
        (degrees) => {
            const box = TextBox.create({ positions: [...ring] });
            const grab = textBoxEditModel.controlPoints(box).find((c) => c.kind === "rotate")!;

            textBoxEditModel.applyHandle(box, grab, sweptPointer(grab.position, degrees), ring);

            const after = box.geometry.getAllPos();
            expect(after).toHaveLength(8);
            // A reshuffled ring would add a multiple of 90 degrees on top of the sweep.
            const turned = ((((textAngle(after) - textAngle(ring)) % 360) + 540) % 360) - 180;
            expect(Math.abs(turned)).toBeCloseTo(Math.min(degrees, 360 - degrees), 4);
        },
    );
});
