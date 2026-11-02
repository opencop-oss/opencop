# Dependency Patches

This page is specifically for local `patch-package` fixes, where the package
contents need to be edited after installation.

For whole-package version overrides caused by `npm audit` findings or the npm
lockfile age policy, use [`../../../DEPENDENCY-OVERRIDES.md`](../../../DEPENDENCY-OVERRIDES.md)
instead.

For temporary local fixes to third-party dependencies, `opencop-web` uses [`patch-package`](https://github.com/ds300/patch-package). Reuse that mechanism before inventing a new workflow.

Use package patches sparingly. Prefer upgrading the dependency or using a normal app-level workaround when that is practical.

## Current patches

Every active patch has its own description file under [`docs/public/patches/`](../patches/) — what it
changes, why, what consumes it, and what to re-check on a dependency bump. The generated `.patch`
files live in the repo-root [`patches/`](../../../patches/) directory; its
[`README.md`](../../../patches/README.md) is the guide (and template) for adding and documenting a new
one.

## Known-Good Approach

1. Install the patching tool if needed:

   ```bash
   npm install -D patch-package
   ```

2. Keep patches applied before Chakra type generation in `package.json`:

   ```json
   {
     "scripts": {
       "postinstall": "patch-package && npm run chakra"
     }
   }
   ```

3. Create the patch file with `patch-package`. Follow the upstream instructions for the exact patch workflow:

   - [`patch-package` README](https://github.com/ds300/patch-package)

4. Commit the generated `patches/` directory.

## Dockerfile Update

Docker must copy `patches/` before `npm ci` runs. This is already configured in [`docker/Dockerfile`](../../../docker/Dockerfile):

```dockerfile
COPY package*.json ./
COPY patches ./patches
COPY packages/ui/src/theme packages/ui/src/theme

RUN npm ci
```

The important part is that `COPY patches ./patches` happens before `RUN npm ci`. Without that, Docker builds will fail or install without the patch files available.

## Cleanup

When an upstream fix lands and the patch is no longer needed:

- remove the patch file from `patches/`

Only after the last patch is removed:

- remove the `COPY patches ./patches` line from `docker/Dockerfile`
- remove `patch-package`
- change `postinstall` back to `npm run chakra`, preserving type generation

Do not keep an empty `patches/` directory around just to satisfy Docker. Only add it when actual patch files exist.
