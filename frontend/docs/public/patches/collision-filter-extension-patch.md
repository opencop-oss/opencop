# @deck.gl/extensions@9.3.1 — CollisonFilterExtension patch

- **Patch file:** `patches/@deck.gl/extensions+9.3.1.patch`
- **Targets:** `node_modules/@deck.gl/extensions/src/collision-filter/collision-filter-effect.ts`
- **Original upstream version:** 9.3.1
- **Created:** 5/25/2026
- **Last reviewed against upstream:** 5/25/26
- **Issue** [[Bug] CollisionFilterExtension breaking on first render / resize #10333
  ](https://github.com/visgl/deck.gl/issues/10333)

## Status

**Active — required.** Upstream issue filed; no fix released.

| Date      | Upstream state                  | Action        |
| --------- | ------------------------------- | ------------- |
| 5/25/2026 | deck#10333 filed, no fix        | Patch created |

### Remove when

1. [#10333](https://github.com/visgl/deck.gl/issues/10333) is fixed in a released `@deck.gl/extensions`, covering **both** root causes (FBO-resize rerender **and** `TerrainEffect` not filtered out of the picking effects).

## TLDR

`CollisionFilterExtension` was hiding all data on initial render and resize for couple of reasons. This patch fixes that.

## The bug being worked around

### Symptom

On initial render all data using a `CollisionFilterExtension` is hidden until another render is caused. This can include panning, zooming, resizing, etc.

### Root cause

Two causes:

1. The collision map in `collision-filter-effect.ts` was not updating despite an FBO resize. On initial render, the size of the canvas is the default `width: 300`, `height: 150`. This caused the FBO to be at `width: 150`, `height: 75` after a downscale. Once the canvas resizes to match the viewport, the FBO is resized but the collision map is **not** rerendered.

2. `TerrainExtension` `shaderModuleProps` uniform being garbage on first collision map pass. The `CollisionFilterEffect` was not passing the `TerrainEffect` to its `_render` method, meaning `shaderModuleProps` are empty for terrain. This leads to the `terrainUniforms` having garbage data on first collision pass, breaking the collision map.

## What the patch does

### Conceptual change

1. Triggers `_render` to actually render when the size of the FBO has changed.
2. Stops `TerrainEffect` from being filtered out of the `effects` array passed to `_render`.

### Diff walkthrough

```diff
    for (const collisionGroup in channels) {
       const collisionFBO = this.collisionFBOs[collisionGroup];
       const renderInfo = channels[collisionGroup];
+      const lastW = collisionFBO.width;
+      const lastH = collisionFBO.height;
       // @ts-expect-error TODO - assuming WebGL context
       const [width, height] = device.canvasContext.getPixelSize();
       collisionFBO.resize({
         width: width / DOWNSCALE,
         height: height / DOWNSCALE
       });
+      const fboChanged = lastW !== collisionFBO.width || lastH !== collisionFBO.height;
       this._render(renderInfo, {
         effects,
         layerFilter,
         onViewportActive,
         views,
         viewport,
-        viewportChanged
+        viewportChanged: viewportChanged || fboChanged,
       });
     }
```

This is a simple change, just saves the last width and height and compares it to new width and height. If they changed, use the `viewportChanged` flag to indicate a rerender is needed.

```diff
-    const effects = allEffects?.filter(e => e.useInPicking && preRenderStats[e.id]);
+    const effects = allEffects?.filter(e => e.useInPicking);
```

`preRenderStats[e.id]` was filtering out `TerrainEffect` since it comes later in the effect chain. This line was initially used to search for the `MaskEffect` and trigger a re-render.

```ts
// Detect if mask has rendered. TODO: better dependency system for Effects
const effects = allEffects?.filter((e) => e.useInPicking && preRenderStats[e.id]);
const maskEffectRendered = (preRenderStats["mask-effect"] as MaskPreRenderStats)?.didRender;
```

The maintainers explicitly document this as a bad workaround due to complex dependencies between extensions. In reality, the `TerrainEffect` is needed for this layer pass because it affects picking and adds a default shader module (all shaders across our entire app are injected with terrain related code). I have yet to investigate how other extensions are affected by this change, but I think this is a scenario where we cross that bridge whenever we get to it.
