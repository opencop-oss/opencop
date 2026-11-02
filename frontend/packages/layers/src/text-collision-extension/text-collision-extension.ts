// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { CollisionFilterExtension, type CollisionFilterExtensionProps } from "@deck.gl/extensions";
import type { Layer, LayerContext } from "@deck.gl/core";
import textCollision from "./shader-module";

/** Props a layer may supply alongside the base extension's. */
export type TextCollisionExtensionProps = CollisionFilterExtensionProps & {
    /** Per-instance opt-out of the collision test; see the shader module. */
    getNoCollide?: unknown;
};

export class TextCollisionExtension extends CollisionFilterExtension {
    static componentName = "TextCollisionExtension";
    static defaultProps = {
        ...CollisionFilterExtension.defaultProps,
        getNoCollide: { type: "accessor", value: 0 },
    };

    getShaders(this: Layer<CollisionFilterExtensionProps>) {
        return { modules: [textCollision] };
    }

    /**
     * Declares `instanceNoCollide` for every layer the extension is attached
     * to, not only those that supply it.
     *
     * The shader module reads it unconditionally. An attribute a layer's
     * `AttributeManager` does not know about is never bound, and luma reads an
     * unbound location from the context's global constant — a value left by
     * whatever drew last. Declaring it here gives every such layer a defined 0,
     * which is the "collides as before" case, rather than one that depends on
     * draw order.
     */
    initializeState(
        this: Layer<TextCollisionExtensionProps>,
        context: LayerContext,
        extension: TextCollisionExtension,
    ): void {
        CollisionFilterExtension.prototype.initializeState.call(this, context, extension);
        this.getAttributeManager()?.add({
            instanceNoCollide: {
                size: 1,
                type: "uint8",
                stepMode: "dynamic",
                defaultValue: 0,
                accessor: "getNoCollide",
            },
        });
    }
}
