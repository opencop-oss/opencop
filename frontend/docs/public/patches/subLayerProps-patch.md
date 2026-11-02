# @deck.gl/core@9.3.1 — CompositeLayer.getSubLayerProps patch

- **Patch file:** `patches/@deck.gl+core+9.3.1.patch`
- **Targets:** `node_modules/@deck.gl/core/src/lib/composite-layer.ts` (+ built `dist/lib/composite-layer.js`)
- **Original upstream version:** 9.3.1
- **Created:** 5/25/2026
- **Last reviewed against upstream:** 6/24/26
- **Issue** [[Bug] LayerExtension.getSubLayerProps overrides my sublayerprops in CompositeLayer #6814](https://github.com/visgl/deck.gl/issues/6814)

## Status

**Active — required.** Upstream issue filed (long-standing); no fix released.

| Date      | Upstream state          | Action                                          |
| --------- | ----------------------- | ----------------------------------------------- |
| 5/25/2026 | deck#6814 filed, no fix | Patch created                                   |
| 6/24/2026 | deck#6814 open, no fix  | Reviewed against upstream; patch still required |

### Remove when

1. [#6814](https://github.com/visgl/deck.gl/issues/6814) is fixed in a released version, **and**
2. the fix also preserves extension-forwarded `updateTriggers` (fix #2 here, not described in the issue) — verify `getOutline`/`getFilterValue` accessors re-evaluate on composite layers before deleting.

## TLDR

Two related fixes to how `CompositeLayer.getSubLayerProps` merges props from layer extensions:

1. **Override order:** `_subLayerProps` were being overridden by `LayerExtension.getSubLayerProps`. The pass-through is moved earlier so per-sublayer props win.
2. **Dropped triggers:** that reorder then exposed a second bug — the final `updateTriggers` reassignment discards the extension-forwarded `updateTriggers` (e.g. `getOutline`, `getFilterValue`), so accessor attributes added by an extension never re-evaluate. We now preserve them.

## The bug being worked around

### Symptom

1. `_subLayerProps` passed to a `CompositeLayer` are overridden by `LayerExtension.getSubLayerProps`.
2. An extension's accessor (e.g. `OutlineExtension.getOutline`) stops updating: the attribute it drives (`instanceOutlines`) is computed once and never invalidated when the trigger value changes, so the outline doesn't track selection changes on composite layers.

### Root cause

1. `_subLayerProps` are applied **before** `LayerExtension.getSubLayerProps`, so the extension clobbers them.
2. After moving the extension pass-through earlier (fix #1), the final
   ```js
   newProps.updateTriggers = { all, ...sublayerProps.updateTriggers, ...overridingSublayerTriggers };
   ```
   **reassigns** (not merges) `updateTriggers`, rebuilding it from only `sublayerProps` and the `_subLayerProps` overrides. The extension-forwarded triggers that `LayerExtension.getSubLayerProps` had merged in (`layer-extension.ts`: `newProps.updateTriggers[key] = this.props.updateTriggers[key]`) are not among those sources, so they are silently dropped. It isn't an override collision — the key is simply absent from the replacement object.

## What the patch does

### Conceptual change

1. Move the `LayerExtension.getSubLayerProps` pass-through **above** the `_subLayerProps` (`sublayerProps`) / `overridingSublayerProps` assignment, so sublayer props take precedence.
2. Before that assignment replaces `newProps.updateTriggers`, **capture** the extension-merged triggers and spread them back into the final `updateTriggers` object — first, so `sublayerProps`/`overriding` still win on genuine key conflicts (preserving fix #1) while extension-only triggers survive.

### Diff walkthrough

Fix #1 — move the extension pass-through ahead of the sublayer-props assignment:

```diff
-        Object.assign(newProps, sublayerProps,
-        // experimental feature that allows users to override sublayer props via parent layer prop
-        overridingSublayerProps);
-        newProps.id = `${this.props.id}-${sublayerId}`;
-        newProps.updateTriggers = { ... };
         // Pass through extension props
         for (const extension of extensions) { ... }
+        Object.assign(newProps, sublayerProps, overridingSublayerProps);
+        newProps.id = `${this.props.id}-${sublayerId}`;
+        newProps.updateTriggers = { ... };
```

Fix #2 — preserve the extension-forwarded triggers across the reassignment:

```diff
+        // Capture extension-forwarded updateTriggers (e.g. getOutline) before the
+        // Object.assign + reassignment below would otherwise discard them.
+        const extensionTriggers = newProps.updateTriggers;
         Object.assign(newProps, sublayerProps, overridingSublayerProps);
         newProps.id = `${this.props.id}-${sublayerId}`;
         newProps.updateTriggers = {
             all: this.props.updateTriggers?.all,
+            ...extensionTriggers,
             ...sublayerProps.updateTriggers,
             ...overridingSublayerTriggers
         };
```

`extensionTriggers` must be captured **before** the `Object.assign`, because assigning `sublayerProps` over `newProps` replaces the `updateTriggers` object reference, losing the extension triggers otherwise.

This is consumed by `@opencop/layers` `OutlineExtension` (the `getOutline` accessor → `instanceOutlines` attribute, used by `OutlineEffect`'s coverage pass) and protects `DataFilterExtension.getFilterValue` forwarding the same way. Without fix #2 the outline would not update when the selected element changes on composite layers (`LabeledPolygonLayer`, `CircleLayer`, etc.).

### Notes / caveats

- Ordering in the final `updateTriggers` is `extensionTriggers` → `sublayerProps` → `overriding`, so the more specific sources still win on key collisions; extension-only keys are simply no longer dropped.
- Fragile across deck upgrades: if `getSubLayerProps` changes shape, re-apply both hunks and regenerate with `npx patch-package @deck.gl/core`.
