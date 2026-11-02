# @deck.gl/core@9.3.1 — post-process effect `isActive` gate

- **Patch file:** `patches/@deck.gl+core+9.3.1.patch`
- **Targets:** `node_modules/@deck.gl/core/src/lib/deck-renderer.ts` and `src/lib/effect.ts` (+ built `dist/lib/deck-renderer.js`, `dist/lib/effect.d.ts`)
- **Original upstream version:** 9.3.1
- **Created:** 6/28/2026
- **Last reviewed against upstream:** 6/28/26
- **Issue** Not filed

## Status

**Active — required.** No upstream issue filed.

| Date      | Upstream state                                                       | Action        |
| --------- | -------------------------------------------------------------------- | ------------- |
| 6/28/2026 | No way for a post-process effect to declare itself idle; not filed   | Patch created |

### Remove when

1. deck ships an equivalent per-frame effect-activity gate (migrate `OutlineEffect`'s `isActive` to it first), covering **both** touchpoints (`_preRender` post-process flag **and** `_postRender` invocation).

## TLDR

`DeckRenderer` treats an effect as "post-processing active" based purely on whether it has a
`postRender` method, so a registered `PostProcessEffect` forces the full scene→renderBuffers→MSAA
capture every frame even when it has nothing to draw. This patch adds an optional `Effect.isActive`
flag: when an effect sets `isActive === false`, the renderer skips both its `postRender` pass and the
MSAA scene capture. Defaults to active, so untouched effects behave exactly as before.

## The bug being worked around

### Symptom

Our `OutlineEffect` (a `PostProcessEffect`) is registered as a default effect by `OutlineExtension`.
Like deck's own `TerrainExtension`/`TerrainEffect`, the extension owns the effect for its whole life
and never removes it. But because `OutlineEffect` is a *screen-space* post-process effect, its mere
presence forces deck onto the post-process render path — including our MSAA-scene capture patch —
every frame, even when nothing is being outlined (e.g. terrain-draped outlines are in use instead,
or nothing is selected). That's wasted GPU work and memory for an idle effect.

### Root cause

`DeckRenderer._preRender` flags the post-process path from the static presence of `postRender`:

```js
for (const effect of effects) {
  opts.preRenderStats[effect.id] = effect.preRender(opts);
  if (effect.postRender) {           // ← presence, not "does it have work this frame"
    this.lastPostProcessEffect = effect.id;
  }
}
if (this.lastPostProcessEffect) {
  this._resizeRenderBuffers();       // builds renderBuffers + the MSAA scene FBO
}
```

`lastPostProcessEffect` then drives the scene render target switch and the MSAA capture/resolve in
`renderLayers`. There is no way for a post-process effect to declare itself idle for a frame, so an
always-registered one can never be free. (`_addDefaultEffect` is also one-way — defaults can't be
removed — so "just unregister it" isn't available either.)

## What the patch does

### Conceptual change

Add an optional `isActive` field to the `Effect` interface and gate both post-process touchpoints on
it. `preRender` runs *before* the gate, so an effect can compute `isActive` from its own state each
frame (e.g. `OutlineEffect` sets it from whether any layer opts into screen-space outlining). When
`isActive === false`, the effect is neither counted as `lastPostProcessEffect` (so no MSAA capture)
nor invoked in `_postRender` (which would otherwise run with an undefined `inputBuffer`, since
`renderBuffers` are never sized).

### Diff walkthrough

```diff
   // _preRender
   opts.preRenderStats[effect.id] = effect.preRender(opts);
-  if (effect.postRender) {
+  if (effect.postRender && effect.isActive !== false) {
     this.lastPostProcessEffect = effect.id;
   }
```

```diff
   // _postRender
   for (const effect of effects) {
-    if (effect.postRender) {
+    if (effect.postRender && effect.isActive !== false) {
       params.target = effect.id === this.lastPostProcessEffect ? opts.target : undefined;
       const buffer = effect.postRender(params);
```

`effect.ts` / `effect.d.ts` gain `isActive?: boolean` on the `Effect` interface.

### Notes / caveats

- **Backwards compatible.** The check is `!== false`, so any effect that doesn't set `isActive`
  (every upstream effect) stays active — identical to today's behavior.
- **Both gates are required.** Gating only `_preRender` leaves `_postRender` calling `postRender`
  with `inputBuffer = renderBuffers[0]`, which is undefined when no active effect sized the buffers.
- **Consumed by `OutlineEffect`** (`packages/layers/src/outline-extension/OutlineEffect.ts`): its
  `preRender` sets `isActive` from `opts.layers.some(l => has OutlineExtension)`. With terrain-draped
  outlines (no `OutlineExtension` layers), the lingering default effect reports inactive → no MSAA.
- **Pairs with the MSAA-scene patch** (`docs/public/patches/msaa-scene-patch.md`) in the same file — this
  is what lets the MSAA capture be skipped per-effect rather than for any post-process effect.
- Fragile across deck upgrades: if `_preRender`/`_postRender` change shape, re-apply by hand and
  regenerate with `npx patch-package @deck.gl/core`.
```
