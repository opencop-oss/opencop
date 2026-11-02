# Dependency Overrides

This is the first place to look when `opencop-web` needs a package-level
dependency security fix.

Use this document for:

- `npm audit` findings that need `package.json` `overrides`.
- npm lockfile age-policy failures where an override can select an older safe
  version.
- Short-lived lockfile-policy allowlist entries when a known-safe package newer
  than 7 days must be accepted.
- `npm audit` findings with no available fix (or only a disproportionate major
  bump) that need a documented CI exception in
  `scripts/check-npm-audit-allowlist.json`.

Do not use this document for local source edits inside an installed package. For
that workflow, see [`docs/public/developer-guide/dependency-patches.md`](docs/public/developer-guide/dependency-patches.md).

## Choosing A Fix

Prefer a direct package fix before adding an exception.

1. If a direct dependency can be upgraded or downgraded cleanly, do that and
   regenerate `package-lock.json`.
2. If an upstream package pulls a vulnerable or too-new transitive dependency,
   use `package.json` `overrides` to force the needed version, then document the
   override below.
3. If the npm lockfile policy blocks a package published less than 7 days ago,
   first try an override to an older safe version. This keeps the lockfile
   policy strict for everyone.
4. If the young package is known safe and must be accepted, add a temporary
   entry to `scripts/check-npm-lock-policy.allowlist.json`. Keep the exception
   package-specific, reviewed, and short-lived. Do not lower `min-release-age`
   globally.
5. If version selection cannot fix the issue because the package contents need a
   local code change, use `patch-package` instead.
6. If no fix exists at all, or the only fix is a disproportionate major bump,
   and you've verified the vulnerable code path is not reachable (or the risk
   is otherwise acceptable), add an entry to
   `scripts/check-npm-audit-allowlist.json` so `ci:audit-critical` stops
   failing on that specific advisory ID. `npm audit` has no native per-advisory
   ignore mechanism, so this is the only way to unblock CI without masking
   other findings. Document the rationale, scope, and removal criteria here as
   well as in the allowlist file.

## Override Entry Checklist

Every active override should include:

- why the override exists;
- the dependency chain that pulls the problematic package;
- the vulnerable, too-young, or otherwise problematic version;
- the selected fixed or policy-compliant version;
- links to advisories, release notes, or review notes;
- removal criteria and a command that verifies whether removal is safe.

For a temporary lockfile-policy allowlist entry, include the same evidence in
the PR description and in the allowlist fields: `name`, `version`,
`lockfilePath`, `publishedAt`, `reason`, `approver`, `merge_request`, and
`until`.

## Lockfile Policy Script Environment

`scripts/check-npm-lock-policy.js` reads these optional environment variables.
The defaults are correct for the current public npm registry flow; do not
override them without documenting the reason in the MR.

| Variable | Default | Purpose |
|----------|---------|---------|
| `NPM_LOCK_POLICY_REGISTRY` | `https://registry.npmjs.org/` | Comma-separated list of allowed lockfile `resolved` URL prefixes. The first prefix is also used to fetch publish-time metadata. Override only for an explicitly trusted registry mirror, and keep CI, Docker, and local development consistent. |
| `NPM_LOCK_POLICY_MIN_AGE_DAYS` | `7` | Minimum package publish age, in days, for committed lockfile entries. Do not lower globally to bypass the policy; prefer a direct dependency fix, an override, or a reviewed package-specific allowlist entry. |
| `NPM_LOCK_POLICY_FETCH_CONCURRENCY` | `20` | Number of concurrent package metadata fetches. Operational tuning only; it does not change which packages pass policy. |

## npm audit Allowlist

`ci:audit-critical` runs
[`scripts/check-npm-audit-allowlist.js`](scripts/check-npm-audit-allowlist.js)
instead of calling `npm audit` directly, because plain `npm audit` has no way
to ignore a single advisory ID short of the whole severity level. The script:

1. Runs `npm audit --json` (forwarding `--audit-level`/`--omit=dev` as given).
2. For each flagged package at or above the configured severity, walks its
   `via` chain (including cascaded findings from other flagged packages) and
   checks whether every advisory in that chain has a matching `id` in
   [`scripts/check-npm-audit-allowlist.json`](scripts/check-npm-audit-allowlist.json).
3. Fails (non-zero exit) if any advisory in scope isn't fully covered. Passes,
   printing what was suppressed, otherwise.

It also warns (without failing the build) when an allowlist entry matched
nothing in the current run — a signal that entry may be stale and worth
removing — or when an entry's `revisitBy` date has passed.

Each allowlist entry requires: `id` (GHSA advisory ID), `package`, `reason`,
`reference` (advisory URL), `addedAt`, and an optional `revisitBy`. Keep this
file as small as possible — every entry is a standing exception to a security
gate — and document each entry's rationale, scope, and removal criteria in this
guide before accepting it.

## Active Lockfile-Policy Allowlist Entries

Entries in
[`scripts/check-npm-lock-policy.allowlist.json`](scripts/check-npm-lock-policy.allowlist.json)
temporarily accept a package published inside the 7-day `min-release-age`
window when no older version resolves the issue that motivated the bump. Each
entry expires on its own `until` date — after that date the package has
cleared the freshness window on its own merits and the entry becomes
redundant (safe to delete on sight).

No lockfile-policy exceptions are active. The required file remains an empty
array so the enforcement script can load it normally.

## Active Overrides


### handlebars → 4.7.10

**Added:** 2026-03-26
**Updated:** 2026-10-26 — bumped from `4.7.9` to `4.7.10` after three new advisories covering `<=4.7.9` (GHSA-8r5x-fm3f-whwj, GHSA-p8wg-vrv2-v86f, GHSA-xw65-4hp5-5hc7)
**Reason:** Security vulnerabilities GHSA-2qvq-rjwj-gvw9 (Prototype Pollution leading to XSS through Partial Template Injection), GHSA-8r5x-fm3f-whwj (critical — JavaScript injection via AST type confusion in `compile`), GHSA-p8wg-vrv2-v86f (critical — JavaScript injection via own-property check bypass), and GHSA-xw65-4hp5-5hc7 (JavaScript injection via unsafe inline embedding of precompiled templates)

#### Dependency Chain

```
eslint-plugin-boundaries@5.4.0 (devDependency)
  └── @boundaries/elements@1.2.0
        └── handlebars@4.7.8  ← vulnerable
```

#### Why Override is Necessary

- **Vulnerable versions:** <=4.7.9 (the 2026-10 advisories); 4.0.0 - 4.7.8 (GHSA-2qvq-rjwj-gvw9)
- **Fixed version:** 4.7.10+ (published 2026-10-05)
- **Current transitive version:** 4.7.8 (vulnerable without override)

`@boundaries/elements@1.2.0` pins `handlebars` to exactly `4.7.8`. Moving
`eslint-plugin-boundaries` off the pinning chain means a two-major jump to 7.x
(`npm audit` proposes `7.3.0`), which needs a lint-config migration of its own —
disproportionate for a dev-only lint dependency. The override is a patch bump on the same 4.7.x line.

#### References

- [GHSA-2qvq-rjwj-gvw9](https://github.com/advisories/GHSA-2qvq-rjwj-gvw9) - Prototype Pollution leading to XSS
- [GHSA-8r5x-fm3f-whwj](https://github.com/advisories/GHSA-8r5x-fm3f-whwj) - JavaScript injection via AST type confusion
- [GHSA-p8wg-vrv2-v86f](https://github.com/advisories/GHSA-p8wg-vrv2-v86f) - JavaScript injection via own-property check bypass
- [GHSA-xw65-4hp5-5hc7](https://github.com/advisories/GHSA-xw65-4hp5-5hc7) - JavaScript injection via inline precompiled templates
- [handlebars releases](https://github.com/handlebars-lang/handlebars.js/releases)

#### Removal Criteria

This override can be removed when `eslint-plugin-boundaries` is upgraded to a
release whose `@boundaries/elements` resolves handlebars 4.7.10+ (or drops it).

Check with: `npm ls handlebars`

### fflate → 0.8.3

**Added:** 2026-09-03
**Reason:** Security vulnerability GHSA-px8p-9vwx-vf98 (`unzipSync` can enter an
infinite loop when parsing malformed ZIP64 archives), which `ci:audit-critical`
surfaced as high severity through `@deck.gl/geo-layers` and `@loaders.gl/3d-tiles`

#### Dependency Chain

```
@deck.gl/geo-layers
  └── @loaders.gl/3d-tiles
        └── @loaders.gl/compression
              └── fflate@0.7.4  ← vulnerable (nested copy)
```

The project already resolved `fflate@0.8.3` at the top level; only
`@loaders.gl/compression` carried its own nested `0.7.4`.

#### Why Override is Necessary

- **Vulnerable versions:** >=0.4.5 <0.4.9, >=0.5.0 <0.5.4, >=0.6.0 <0.6.11,
  >=0.7.0 <0.7.5, >=0.8.0 <0.8.3
- **Fixed versions:** 0.4.9, 0.5.4, 0.6.11, 0.7.5, 0.8.3
- **Current transitive version:** 0.7.4 (vulnerable without override)

`npm audit` reported only the `>=0.7.0 <0.7.5` range because that is where the
installed copy sat; the advisory patches five release lines. The `0.8.3` pin is
above every vulnerable range, but note that 0.8.0–0.8.2 are *also* affected — a
future bump must land on a patched version, not merely a higher one.

`npm audit fix` proposes `@deck.gl/geo-layers@9.0.6`, flagged
`isSemVerMajor: true` — a breaking downgrade of the mapping stack to remediate a
single nested transitive. The override pins the one problematic package instead
and leaves deck.gl untouched.

`@loaders.gl/compression` 4.4.1 does not import `fflate` at all — no file under
its `dist/` or `src/` references it. Its deflate and gzip classes use `pako` in
the browser and Node's `zlib` on the server. `fflate` is a declared-but-unused
dependency, so the advisory was already unreachable through this chain; the
override makes the dependency graph agree with that fact rather than relying on
an allowlist exception.

#### References

- [GHSA-px8p-9vwx-vf98](https://github.com/advisories/GHSA-px8p-9vwx-vf98) - fflate `unzipSync` infinite loop on malformed ZIP64

#### Removal Criteria

Remove once `@loaders.gl/compression` either drops its unused `fflate`
dependency or declares a range that resolves to a patched version on its own
(either would arrive via a loaders.gl bump pulled in by deck.gl).

Check with: `npm ls fflate` — the override is unnecessary once no nested
`fflate` resolves inside a vulnerable range listed above. A version merely
higher than 0.7.5 is not sufficient: 0.8.0–0.8.2 are affected.

### @chakra-ui/cli › zod → $zod

**Added:** 2026-10-26
**Reason:** Security vulnerability GHSA-mrhx-c7xc-m3w5 (Zod can exhaust memory
while validating oversized constrained collections)

#### Dependency Chain

```
@chakra-ui/cli@3.36.1 (devDependency)
  └── zod@3.25.76  ← vulnerable (nested copy)
```

The project's own direct `zod` is already `4.6.6`; only `@chakra-ui/cli` carried
its own nested 3.x copy.

#### Why Override is Necessary

- **Vulnerable versions:** <4.6.6 — every 3.x release
- **Fixed version:** 4.6.6+
- **Current transitive version:** 3.25.76 (vulnerable without override)

There is no patched 3.x release, and `@chakra-ui/cli` (through the latest
`3.37.0`) declares `zod: ^3.25.76`, so neither a bump nor a 3.x override can fix
it. The override is **scoped** to `@chakra-ui/cli` so it cannot touch any other
consumer, and uses `$zod` — a reference to the root `zod` dependency — so the
nested copy dedupes onto the root and follows it on every future root bump,
rather than stranding the CLI on a separate pinned copy.

This crosses a zod major, so it was verified rather than assumed:

- The CLI's zod use is limited to `z.object` / `z.array` / `z.string` /
  `z.boolean` / `z.unknown` with `.optional`, `.default`, `.extend`, `.strict`,
  `.passthrough`, `.url`, `.parse`, `.safeParse` and `ZodError.issues` — all
  present in zod 4.
- The CLI runs only as `npm run chakra` (typegen), which `postinstall` invokes,
  so it executes on every `npm install` / `npm ci`, including CI and the Docker
  build. Every invocation also parses `process.env` with zod at module load
  (`dist/esm/utils/fetch.js`), so zod 4 is exercised on each run, not only by
  typegen itself.
- Typegen produced byte-identical output under zod 3.25.76 and under the
  override.

Dev-only: the CLI validates our own theme config, so the input is not
attacker-controlled.

#### References

- [GHSA-mrhx-c7xc-m3w5](https://github.com/advisories/GHSA-mrhx-c7xc-m3w5) - memory exhaustion on oversized constrained collections

#### Removal Criteria

Remove once `@chakra-ui/cli` declares a zod 4.6.6+ range (or drops zod).
Re-run the typegen and diff its output against the previous run on any
`@chakra-ui/cli` bump while the override is in place.

Check with: `npm ls zod` — the override is unnecessary once `@chakra-ui/cli`'s
declared range resolves 4.6.6+ on its own.

## Active npm audit Allowlist Entries

These `npm audit` findings are knowingly accepted rather than fixed. Each entry
records why the risk is acceptable and the condition that would let us drop the
exception.

### image-size — GHSA-w3rx-r6r6-pgpr

**Added:** 2026-08-10
**Updated:** 2026-10-26 — dropped GHSA-5p2g-fcmc-qvqq (JXL/HEIF), whose range was
narrowed to `>=1.2.0 <=2.0.2` and no longer covers the installed `0.7.5`; rewrote
the rationale now that a fixed `image-size` exists
**Revisit by:** 2026-11-10

A high-severity DoS advisory (CWE-835 infinite loop) in `image-size`'s ICNS
parser. Availability-only (CVSS 7.5, `A:H`, no confidentiality/integrity impact).

#### Dependency Chain

```
@deck.gl/geo-layers@9.3.1   (production dependency)
  └── @luma.gl/gltf@9.3.3
        └── @loaders.gl/textures@4.4.1
              └── texture-compressor@1.0.2
                    └── image-size@0.7.5  ← vulnerable
```

#### Why Accepted Rather Than Fixed

- **The fixed version cannot be applied.** The advisory covers `>=0.6.3 <=2.0.2`
  and is fixed in `2.0.3`, but `texture-compressor@1.0.2` (its only consumer)
  hard-pins `image-size: ^0.7.4` and calls `image_size_1.default(filepath)` in
  `dist/cli/lib/utilities.js`. In 2.x the default export accepts only a buffer
  (file-path input moved to the async `image-size/fromFile`), so an override to
  2.x would make that call throw rather than fix it. There is no patched 0.7.x,
  and no newer `texture-compressor` or `@loaders.gl/textures` release drops the
  dependency.
- **The vulnerable code is unreachable in the deployed browser app** (verified,
  not inferred):
  - `image-size` has exactly one import edge in the whole tree —
    `texture-compressor/dist/cli/lib/utilities.js`. Nothing else imports it.
  - `@loaders.gl/textures` never imports `texture-compressor`; its Node-only
    encoder (`encodeImageURLToCompressedTextureURL`) passes `"texture-compressor"`
    as an argument to `npx` and spawns it via `ChildProcessProxy`
    (`child_process`). So `image-size` is never in any bundleable module graph,
    and the path cannot run in a browser (no `child_process`).
  - `opencop-web` never imports the textures encoder — `@deck.gl/geo-layers`
    pulls `@loaders.gl/textures` only for its KTX/basis *loader* (WASM decode),
    not the CLI *encoder* that is the sole `image-size` consumer. A grep of
    `packages/*/src` for `@loaders.gl/textures` / `encodeImageURL*` /
    `CompressedTextureWriter` returns nothing.
- Reaching the bug would require the app to call the texture *encoder*, in a Node
  process, which shells out to `npx texture-compressor`, which then runs
  `image-size` on a crafted ICNS file — none of which happens in a deployed
  browser app.

#### Removal Criteria

Drop this entry when any of the following lands:
- `texture-compressor` (or a loaders.gl replacement) resolves `image-size`
  2.0.3+, OR
- `@loaders.gl/textures` / `@deck.gl/geo-layers` stops depending on
  `texture-compressor`.

Check with: `npm ls image-size` and `npm run ci:audit-critical`

### maplibre-gl — GHSA-jrc7-96c5-q579

**Added:** 2026-09-09
**Revisit by:** 2026-11-20 (re-checked 2026-10-20, when the app's own map credit was added)

Critical XSS sanitizer bypass (CWE-79) in `maplibre-gl`'s `DOM.sanitize()`: a
live `NamedNodeMap` mutates while being iterated, so removing one attribute
shifts the collection and the next attribute is skipped, letting a crafted
event-handler attribute survive sanitization.

This is a **stronger finding than the `image-size` entry above** — critical
rather than high, an integrity/confidentiality impact rather than
availability-only, and a fixed version does exist. It is accepted only because
the fix is a major-version upgrade and the vulnerable path is disabled in this
app. It carries a one-month revisit window rather than three.

#### Dependency Chain

```
opencop-web (direct production dependency)
  └── maplibre-gl@5.8.0  ← vulnerable
```

#### Why Accepted Rather Than Fixed

- **The only fix is a disproportionate major bump.** The advisory covers
  `<=6.4.0`; the first fixed release is in the 6.x line (`npm audit` proposes
  `6.9.0`). The 5.x line ends at `5.24.0`, entirely inside the vulnerable range,
  so there is no backport to move to. Going 5.x → 6.x replaces the map engine
  the whole application renders through, which needs its own MR, its own
  regression pass, and a perf comparison — not an audit-unblocking change.
- **The vulnerable code is unreachable in this app** (verified against the
  installed 5.8.0 bundle, not inferred):
  - `DOM.sanitize` has exactly one call site in the shipped code —
    `AttributionControl._updateAttributions`:
    `this._innerContainer.innerHTML = DOM.sanitize(attribHTML)`. The other
    `sanitize` matches in the bundle are `sanitizedRegExpFromScriptCodes`, an
    unrelated text-shaping helper.
  - Every map construction disables that control:
    `MapView.tsx` (`attributionControl={false}`) and
    `render-vector-tile-preview.util.ts` (`attributionControl: false`). No code
    calls `addControl(new AttributionControl(...))`.
  - Nothing in `packages/*/src` uses maplibre's `Popup` API, the other place
    user-influenced HTML would reach the DOM.
  - The map's credit line (`MapAttribution` in `MapStatusBar`) is app-owned. It
    reads source attribution strings from the map and passes them through
    `parseAttribution`, which parses into an inert `DOMParser` document and keeps
    only text and http(s) links that React renders. It never creates an
    `AttributionControl` and never assigns `innerHTML`, so showing attribution
    this way does not reach `DOM.sanitize`.
- Reaching the bug would require re-enabling the attribution control **and**
  serving a style or TileJSON whose `attribution` string carries a crafted
  attribute — so the exception should be revisited the moment MapLibre's
  `AttributionControl` is turned on.

#### Removal Criteria

Drop this entry when either lands:
- `maplibre-gl` is upgraded to a version outside the `<=6.4.0` range (tracked
  separately as the 5.x → 6.x migration), OR
- MapLibre's `AttributionControl` is enabled anywhere in the app, which
  invalidates the unreachability argument and makes the upgrade urgent rather
  than scheduled.

Check with: `npm ls maplibre-gl`, `npm run ci:audit-critical`, and
`grep -rn "attributionControl" packages`

---

Check the full set with: `npm audit` and `npm run ci:audit-critical`
