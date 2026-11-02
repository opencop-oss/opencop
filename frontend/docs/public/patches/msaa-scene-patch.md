# @deck.gl/core@9.3.1 — MSAA post-process scene patch

- **Patch file:** `patches/@deck.gl+core+9.3.1.patch`
- **Targets:** `node_modules/@deck.gl/core/src/lib/deck-renderer.ts` (+ built `dist/lib/deck-renderer.js`)
- **Original upstream version:** 9.3.1
- **Created:** 6/24/2026
- **Last reviewed against upstream:** 7/16/26
- **Issues**:
    1. [\[Bug\] PostProcessEffects do not have MSAA (WebGL)](https://github.com/visgl/deck.gl/issues/10404) (Open)
    2. [\[Question/Bug\]: Texture.samples ignored in WebGL backend – no MSAA for offscreen Framebuffers](https://github.com/visgl/luma.gl/issues/2702) (Closed)
    3. [RFC: Color-only MSAA for offscreen framebuffers](https://github.com/visgl/luma.gl/issues/2741).

## Status

**Active — required.** Upstream fix does not exist yet; RFC is open but unreleased.

| Date      | Upstream state                                               | Action                                          |
| --------- | ------------------------------------------------------------ | ----------------------------------------------- |
| 6/24/2026 | deck#10404 open; luma#2702 open                              | Patch created                                   |
| 7/16/2026 | luma#2702 closed in favor of RFC luma#2741, nothing released | Reviewed against upstream; patch still required |

### Remove when

1. luma ships offscreen MSAA per [RFC #2741](https://github.com/visgl/luma.gl/issues/2741) (or `Texture.samples` works on the WebGL backend), **and**
2. deck's `DeckRenderer` actually uses it for `renderBuffers[0]` ([#10404](https://github.com/visgl/deck.gl/issues/10404) fixed in a released version).

## TLDR

When any `PostProcessEffect` is active, deck renders the scene into a single-sample texture, so flat-shaded layers (Path/Polygon/Line) lose anti-aliasing. The patch renders that scene into a multisampled renderbuffer and blit-resolves it into the texture the effects read, restoring MSAA. It also resolves the scene **depth** into a sampleable depth texture on `renderBuffers[0]`, so effects can read scene depth via `inputBuffer.depthStencilAttachment`.

## The bug being worked around

### Symptom

Turning on a post-process effect (e.g. our `OutlineEffect` for selection) makes the deck scene look jagged — Path/Polygon edges that are smooth without the effect become aliased.

### Root cause

`DeckRenderer.renderLayers` switches the scene render target the moment an effect has a `postRender`:

```js
const outputBuffer = this.lastPostProcessEffect ? this.renderBuffers[0] : renderOpts.target;
```

- **Without an effect**, `renderOpts.target` is null, so `LayersPass` falls back to the default WebGL framebuffer (`WebGLCanvasContext.getCurrentFramebuffer()`, `handle: null`). The browser creates that backbuffer multisampled because the context's `antialias` attribute defaults to `true`, so flat-shaded layers get MSAA for free.
- **With an effect**, the scene goes to `renderBuffers[0]`, created in `_resizeRenderBuffers` via `device.createTexture(...)` with no `samples`. luma's WebGL backend has no multisampled-texture path, so it's single-sample. That texture is then sampled as `texSrc` in `ScreenPass` (`inputBuffer.colorAttachments[0]`).

So the aliasing isn't lost in a copy — deck never renders the post-process scene with MSAA in the first place. (In WebGL2 a buffer can't be both multisampled _and_ sampleable; you must render to an MSAA renderbuffer and `blitFramebuffer`-resolve into a sampleable texture.)

## What the patch does

### Conceptual change

When a post-process effect is active on a WebGL2 device, render the scene into a multisampled color renderbuffer FBO instead of `renderBuffers[0]`, then `blitFramebuffer`-resolve it into `renderBuffers[0]` before `_postRender` samples it. Effects are unchanged — they still read `renderBuffers[0]`, which now holds an anti-aliased scene. WebGPU / Node devices (no `gl`) fall through to the original single-sample path.

### Diff walkthrough

```diff
-    const outputBuffer = this.lastPostProcessEffect ? this.renderBuffers[0] : renderOpts.target;
+    const outputBuffer = this.lastPostProcessEffect
+      ? this._msaaFramebuffer || this.renderBuffers[0]
+      : renderOpts.target;
     ...
     const renderResult = layerPass.render({...renderOpts, target: outputBuffer});
+    if (this.lastPostProcessEffect && this._msaaFramebuffer) {
+      this._resolveMSAA();
+    }
```

`_resizeMSAA(width, height)` (called from `_resizeRenderBuffers`, which deck already calls per-frame while an effect is active) lazily builds a multisampled `RGBA8` color renderbuffer FBO from the raw `device.gl`, sized to the drawing buffer, wrapped via `device.createFramebuffer({handle})` so `LayersPass` binds it. `_resolveMSAA()` blits it into `renderBuffers[0].handle` and restores the framebuffer binding so luma's state tracker stays in sync. `_destroyMSAA()` (called from `finalize` and on resize) frees the raw handles.

The patched behavior is consumed by `@opencop/layers`' outline system, which is two parts: `OutlineExtension` (a `LayerExtension`) registers the `OutlineEffect` as a default deck effect (`deck._addDefaultEffect`) and tags which features to outline (`getOutline` → an `outlines` attribute + the `cover` shader module); `OutlineEffect` (a `PostProcessEffect`) then composites the outline over the post-process scene input — `mix(scene, vec4(color, 1.0), edge)`, where `edge` is non-zero only just _outside_ the silhouette. No change to that outline code is required for this patch — the scene-MSAA fix is entirely inside deck's renderer, so it also benefits any other post-process effect.

### Notes / caveats

- The MSAA buffer carries a multisampled `DEPTH_COMPONENT24` depth renderbuffer (sample count matched to the color buffer). This is required: the no-effect path renders to the **default canvas framebuffer**, which has depth, so the scene capture must too. An earlier version of this patch made the MSAA buffer color-only on the (wrong) assumption that the baseline was `renderBuffers[0]` — that disabled depth testing while an effect was active and flipped the z-order of equal-depth overlapping geometry (e.g. billboarded `MilStdIcon`s, which use `depthWriteEnabled: true` / `depthCompare: "less"`).
- **Scene depth is exposed (`PATCH(scene-depth)`).** `renderBuffers[0]` is created with `depthStencilAttachment: 'depth24plus'` (a sampleable depth texture; `DEPTH_COMPONENT24`, matching the MSAA depth renderbuffer's format), and `_resolveMSAA` resolves color **and** depth in a single `blitFramebuffer` call (`COLOR_BUFFER_BIT | DEPTH_BUFFER_BIT`, `NEAREST` — mandatory once depth is in the mask). Effects read scene depth via `inputBuffer.depthStencilAttachment`. This is one blit, not a second pass. As a side benefit it also gives the single-sample fallback (`renderBuffers[0]`, used only when the device lacks `renderbufferStorageMultisample`) a real depth attachment, which it previously lacked. Index 1 (the effect ping-pong swap buffer) gets no depth — intermediates don't need it.
- Sample count is `min(4, MAX_SAMPLES)`.
- Only the initial scene capture is multisampled; effect-to-effect ping-pong still uses the single-sample `renderBuffers`, which is correct (intermediates don't need MSAA).
- `OutlineEffect` separately MSAA-resolves its own _coverage_ pass (a `CoverPass` over only `OutlineExtension` layers, drawn into a multisampled renderbuffer FBO and `blitFramebuffer`-resolved — **color only** — into the single-sample texture the shader samples as `uCover`). Same technique as this patch but in app code, and independent of it: it anti-aliases the silhouette/edge texture, whereas this patch anti-aliases the _scene_ the outline is composited over.
- Fragile across deck upgrades: if `renderLayers` / `_resizeRenderBuffers` change shape, re-apply the hunks by hand and regenerate with `npx patch-package @deck.gl/core`.
