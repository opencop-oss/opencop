# patches/

[`patch-package`](https://github.com/ds300/patch-package) patches — local edits to installed
`node_modules` packages, re-applied on every install by the `postinstall` hook. **Use them
sparingly**; prefer upgrading the dependency or an app-level workaround when practical.

- **Mechanism** (how `patch-package` is wired here, the Dockerfile requirement, cleanup):
  [`../docs/public/developer-guide/dependency-patches.md`](../docs/public/developer-guide/dependency-patches.md).
- **Per-patch descriptions** (what each patch does and why):
  [`../docs/public/patches/`](../docs/public/patches/).

## Adding a patch

1. Edit the file(s) under `node_modules/<pkg>/…` — patch the **`src/*.ts`** source as well as the
   built `dist/*.js`, so the source a reader sees matches the shipped behavior. Then run
   `npx patch-package <pkg>` to generate the `<pkg>+<version>.patch` file here.
2. Commit the generated `.patch` file.
3. **Write a description** in `../docs/public/patches/` (template below). Every patch must have one — it is
   how the next maintainer knows what the patch does and whether it is still needed.

## Naming the description file

One markdown file per patch, under `docs/public/patches/`, named after **what it patches** (the
symbol/feature/class), kebab-case, with a `-patch.md` suffix — not after the package or version:

| Patch | Description file |
| --- | --- |
| `FontAtlasManager` font-atlas eviction (`@deck.gl/layers`) | `docs/public/patches/font-atlas-patch.md` |
| `CompositeLayer.getSubLayerProps` ordering (`@deck.gl/core`) | `docs/public/patches/subLayerProps-patch.md` |
| `CollisionFilterExtension` resize (`@deck.gl/extensions`) | `docs/public/patches/collision-filter-extension-patch.md` |
| `Tesselator` `_dataDiff` truncation (`@deck.gl/core`) | `docs/public/patches/tesselator-patch.md` |
| `DeckRenderer` MSAA post-process scene (`@deck.gl/core`) | `docs/public/patches/msaa-scene-patch.md` |
| `Effect.isActive` post-process gate (`@deck.gl/core`) | `docs/public/patches/post-process-active-patch.md` |
| Terrain internals re-export (`@deck.gl/extensions`) | `docs/public/patches/terrain-internals-export-patch.md` |
| Fragment output location (`@luma.gl/shadertools`) | `docs/public/patches/fragment-output-location-patch.md` |

The package and version live inside the file (header + _Patch file_ field), so the name stays stable
across version bumps.

## What a patch description should contain

Match the existing files. Header line, then a metadata block, then the narrative:

```markdown
# @deck.gl/<pkg>@<version> — <Name> patch

- **Patch file:** `patches/@deck.gl+<pkg>+<version>.patch`
- **Targets:** `node_modules/@deck.gl/<pkg>/src/.../<file>.ts`
- **Original upstream version:** <version>
- **Created:** <M/D/YYYY>
- **Last reviewed against upstream:** <M/D/YY>
- **Issue** <upstream issue link, or "Not filed">

## TLDR

One or two sentences: what's broken and what the patch does about it.

## The bug being worked around

### Symptom

What the user/developer actually observes.

### Root cause

Why it happens, in deck's code.

## What the patch does

### Conceptual change

The change in one short paragraph.

### Diff walkthrough

​```diff
<the relevant hunk>
​```

A sentence or two explaining the hunk, and where in our code the patched behavior is consumed.
```

Keep it concrete. The goal is that someone hitting a failed patch on a deck.gl bump — or wondering
whether they can delete it — can decide in a minute. Update **Last reviewed against upstream** (and
re-generate + rename the `.patch`) whenever the dependency version changes.
