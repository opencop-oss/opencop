# @deck.gl/extensions@9.3.1 — terrain internals re-export patch

- **Patch file:** `patches/@deck.gl+extensions+9.3.1.patch`
- **Targets:** `node_modules/@deck.gl/extensions/src/index.ts` (+ built `dist/index.js`, `dist/index.d.ts`)
- **Original upstream version:** 9.3.1
- **Created:** 6/30/2026
- **Last reviewed against upstream:** 6/30/26
- **Issue** Not filed

## Status

**Active — required.** No upstream issue filed; purely additive re-exports.

| Date      | Upstream state                                             | Action        |
| --------- | ---------------------------------------------------------- | ------------- |
| 6/30/2026 | Terrain internals not in the public barrel; issue not filed | Patch created |

### Remove when

1. deck exports `TerrainEffect`, `TerrainCover`, and `terrainModule` from `@deck.gl/extensions` — then delete the three added export lines and keep the subclasses (they are independent of this patch; see the subclassing section).

## TLDR

deck.gl exports only `_TerrainExtension` from `@deck.gl/extensions`; the machinery the
extension drives (`TerrainEffect`, `TerrainCover`, and the `terrainModule` shader module) is
package-internal. We subclass all three to drape **outlines** onto terrain, so this patch
adds three `export`s to the barrel. The patch itself is a one-line-per-symbol re-export — the
real work is the set of subclassing workarounds in
`packages/layers/src/terrain-outline-extension/` that the re-exports unlock, documented below.

> Shares the `@deck.gl+extensions+9.3.1.patch` file with
> [`collision-filter-extension-patch.md`](./collision-filter-extension-patch.md). That patch
> fixes `collision-filter-effect.ts`; this one only adds exports to `index.ts`. They are
> independent hunks in the same generated `.patch`.

## The bug being worked around

### Symptom

We want to outline features that are draped onto the terrain mesh. The screen-space
`OutlineExtension` / `OutlineEffect` coverage pass is too expensive to run against draped
terrain (see [`post-process-active-patch.md`](./post-process-active-patch.md)), so the outline
has to be produced **inside** the terrain cover render — the same offscreen pass deck already
runs to drape layers onto the height map.

To hook into that pass we need to subclass deck's terrain internals. But `import { TerrainEffect, TerrainCover, terrainModule } from "@deck.gl/extensions"` fails to resolve:
the symbols exist in the package but are not in its public barrel, so they are unreachable
without reaching into deep `node_modules/.../dist/terrain/*.js` paths (which the bundler
aliases and TS module resolution will not honor).

### Root cause

`@deck.gl/extensions/src/index.ts` deliberately exports only the extension classes (and
`_TerrainExtension` under an underscore to mark it unstable). The effect, the cover, and the
shader module are treated as private collaborators of the extension and are never re-exported,
even though they are concrete classes/objects with stable shapes.

## What the patch does

### Conceptual change

Add three re-exports to the extensions barrel (in `src/index.ts` and both built `dist`
artifacts so source and shipped behavior match), exposing the terrain collaborators we need to
subclass. No behavior changes — it is purely a visibility widening.

### Diff walkthrough

```diff
 export {default as _TerrainExtension} from './terrain/terrain-extension';
+export {TerrainEffect} from "./terrain/terrain-effect";
+export {TerrainCover} from "./terrain/terrain-cover";
+export {terrainModule} from "./terrain/shader-module";

 // Shader module
 export {default as project64} from './fp64/project64';
```

Consumed entirely by `packages/layers/src/terrain-outline-extension/`:

- `terrainModule` → swapped for our `terrainOutlineModule` in `TerrainOutlineEffect`.
- `TerrainCover` → base class of `TerrainOutlineCover`.
- `TerrainEffect` → base class of `TerrainOutlineEffect`.
- `_TerrainExtension` (already exported) → base class of `TerrainOutlineExtension`.

## The subclassing workarounds the re-exports enable

These re-exports exist so the three classes below can subclass deck's terrain pipeline. Each
subclass needs `@ts-expect-error` escapes and/or copied-from-source method bodies, because
deck never designed these classes for extension. This is the fragile part — list it here so a
deck bump that changes any private shape has one place to check.

### `TerrainOutlineExtension extends _TerrainExtension`

(`TerrainOutlineExtension.ts`) — the public entry point; the only symbol re-exported from
`@opencop/layers` (`packages/layers/src/index.ts`).

- Adds a `getOutline` accessor prop and registers an `outlines` attribute (per-feature
  `float32`) on the host layer's `AttributeManager` so individual draped features can opt into
  the outline.
- `getShaders` injects an `OUTLINE` define driven by layer state.
- `initializeState` registers our `TerrainOutlineEffect` as a default effect
  (`deck._addDefaultEffect`) instead of deck's `TerrainEffect`.
- The extension lifecycle methods are invoked with the **layer** as `this` (deck calls
  extension hooks bound to the layer), hence the `this: Layer<...>` annotations and the
  `.bind(this)` calls into helper methods — an awkward but deck-idiomatic pattern.

### `TerrainOutlineEffect extends TerrainEffect`

(`TerrainOutlineEffect.ts`) — the bulk of the workarounds.

- **Class-level `@ts-expect-error`** on `extends TerrainEffect`: we add private fields
  (`outline`, `color`) and override methods with incompatible-from-TS's-view signatures.
- **Shader-module swap** (`setup`/`cleanup`): `_removeDefaultShaderModule(terrainModule)` then
  `_addDefaultShaderModule(terrainOutlineModule)`. deck injects the terrain module into **every**
  shader in the app; we replace it wholesale with our outline-aware variant.
- **`_updateTerrainCover` is copied verbatim from deck source** and overridden for one reason:
  the original constructs a `TerrainCover`, and we need a `TerrainOutlineCover` (which carries
  the extra mask attachment). Everything else in the body is unchanged from upstream, so it
  accesses several private fields (`isPicking`, `terrainPickingPass`, `terrainPass`,
  `terrainCovers`, `dummyHeightMap`) — each flagged with `@ts-expect-error "per deck.gl source"`.
  **This method must be re-diffed against upstream on every deck bump.**
- `getShaderModuleProps` extends the base props with `outline`, `color`, and `drawMask`.
- `preRender` scans the frame's layers for one whose state has `terrainOutline` and pulls the
  color off the matching `TerrainOutlineExtension` instance.

### `TerrainOutlineCover extends TerrainCover`

(`TerrainOutlineCover.ts`) — overrides `getRenderFramebuffer` to attach a **second** color
attachment (`colorAttachments[1]`, an `rgba8unorm` mask texture) to the terrain cover FBO, so
the outline pass can write a mask alongside the draped color. Relies on luma's
`updateAttachments`, which is typed `protected` but is public on the WebGL framebuffer impl —
worked around with a local `MutableFramebuffer` type rather than an `@ts-expect-error`.

### Notes / caveats

- **The re-export patch is low-risk** (additive, no behavior change) but **the subclasses are
  high-risk** across deck upgrades. If a terrain bump fails the patch, regenerate exports with
  `npx patch-package @deck.gl/extensions`; then separately verify the subclasses — especially
  the copied `_updateTerrainCover` body and every `@ts-expect-error "per deck.gl source"`.
- If deck ever makes these symbols public, delete the three added export lines and keep the
  subclasses unchanged.
- Pairs with [`post-process-active-patch.md`](./post-process-active-patch.md): the whole reason
  outlining is done in the terrain cover pass (rather than the screen-space `OutlineEffect`) is
  cost, and that patch lets the idle `OutlineEffect` report inactive when terrain-draped
  outlines are in use.
