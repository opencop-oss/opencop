# @luma.gl/shadertools@9.3.3 — fragment output location patch

- **Patch file:** `patches/@luma.gl+shadertools+9.3.3.patch`
- **Targets:** `node_modules/@luma.gl/shadertools/src/lib/shader-assembly/assemble-shaders.ts` (+ built `dist/lib/shader-assembly/assemble-shaders.js`)
- **Original upstream version:** 9.3.3
- **Created:** 6/29/2026
- **Last reviewed against upstream:** 6/29/26
- **Issue** Not filed

## Status

**Active — required.** No upstream issue filed; noted below as a candidate for upstreaming.

| Date      | Upstream state                                                 | Action        |
| --------- | -------------------------------------------------------------- | ------------- |
| 6/29/2026 | luma emits unlocated `fragColor`, no MRT path; issue not filed | Patch created |

### Remove when

1. luma pins the primary fragment output's location itself (or exposes an API to declare it) in a released `@luma.gl/shadertools` — verify the terrain-outline MRT pass (`location = 1` mask output) still compiles.

## TLDR

luma assembles deck fragment shaders with an unqualified `out vec4 fragColor;`. GLSL ES 3.00 forbids mixing located and unlocated fragment outputs, so the moment a shader module adds a second output (`layout(location = 1) out vec4 maskColor;` for MRT), the shader fails to compile. The patch makes `assembleShaders` pin `fragColor` to `layout(location = 0)` whenever a higher output location is present, so multiple fragment outputs (MRT) compile.

## The bug being worked around

### Symptom

Adding a second fragment output to a deck shader module — e.g. `layout(location = 1) out vec4 maskColor;` in the terrain outline pass — makes the shader fail to compile/link, even though each declaration is individually valid GLSL. With only the default `fragColor`, the same shader compiles fine.

### Root cause

GLSL ES 3.00 rule: if **any** fragment output declares an explicit `layout(location = ...)`, then **every** fragment output must declare one. deck/luma's standard fragment template emits the primary output as:

```glsl
out vec4 fragColor;   // no location
```

`assembleShaders` never adds a location to it. So as soon as a `ShaderModule` injects a second output at `location = 1` (the only way to do MRT), `fragColor` (the intended attachment 0) is an unlocated output sitting alongside a located one — which the GLSL ES 3.00 compiler rejects. luma exposes no way to declare the primary output's location, so MRT is impossible out of the box.

## What the patch does

### Conceptual change

After shader injection, in `assembleShaders`, when assembling a **GLSL fragment** shader whose source contains any `layout(location = N)` with N ≥ 1, rewrite the unlocated `out vec4 fragColor;` to `layout(location = 0) out vec4 fragColor;`. This satisfies the all-or-none-locations rule and pins the primary output to attachment 0 (where everything already expects it). Non-MRT shaders (no `location = 1+`) and WGSL are untouched.

### Diff walkthrough

```diff
   // Apply any requested shader injections
   assembledSource = injectShader(assembledSource, stage, mainInjections);

+  // PATCH(luma-fragcolor-location): GLSL ES 3.00 requires every fragment output to declare a
+  // location once any output declares one, so pin fragColor to 0 when a second location appears.
+  if (
+    language === 'glsl' &&
+    stage === 'fragment' &&
+    /\blayout\s*\(\s*location\s*=\s*[1-9]\d*\s*\)/.test(assembledSource)
+  ) {
+    assembledSource = assembledSource.replace(
+      /(^|\n)([ \t]*)out\s+vec4\s+fragColor\s*;/,
+      '$1$2layout(location = 0) out vec4 fragColor;'
+    );
+  }
+
   if (language === 'glsl' && sourceVersion !== targetVersion) {
     assembledSource = transpileGLSLShader(assembledSource, stage);
   }
```

The guard regex only fires when a non-zero output location is present, so single-output shaders pay nothing. The replace is anchored to a line-leading `out vec4 fragColor;` (re-emitting the captured indentation) so it rewrites the declaration and not other mentions of `fragColor`.

The patched behavior is consumed by `@opencop/layers` `terrain-outline-extension`:

- `terrain.shader.ts` declares the second output `layout(location = 1) out vec4 maskColor;` (under `#if OUTLINE`) and writes the outlined object's silhouette mask into it, while `fragColor` (location 0) carries the terrain cover/height.
- `TerrainOutlineCover.ts` attaches a second color attachment (`fbo.colorAttachments[1] = maskTexture.view`), making the cover FBO a two-output MRT target — attachment 0 = cover, attachment 1 = mask.
- The terrain `DECKGL_FILTER_COLOR` stage later samples both (`terrain_map` + `mask_map`) to compute the outline edge.

Without the patch, that two-output terrain pass would not compile.

### Notes / caveats

- Only triggers for GLSL fragment shaders with an explicit `location = 1+`; everything else (single-output GLSL, all WGSL) is unchanged — low blast radius.
- The replace targets exactly `out vec4 fragColor;` (deck's canonical declaration). If a future luma/deck version renames the primary output, gives it its own location, or changes its type, the regex silently no-ops — re-check on a shadertools bump.
- Candidate for upstreaming: luma could declare the primary fragment output's location itself when modules request additional outputs. Not filed yet.
- Fragile across luma upgrades: if `assemble-shaders` restructures, re-apply the hunk by hand (both `src/*.ts` and `dist/*.js`) and regenerate with `npx patch-package @luma.gl/shadertools`.
