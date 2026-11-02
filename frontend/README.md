# OpenCOP Web

This directory contains the OpenCOP frontend application.

## Run the complete application

For the quickest local evaluation, use the
[source-built demo](../demo/README.md). It builds this frontend together with
the included backend and supporting services.

## Build the web image

The production bundle is built inside Docker:

```bash
./docker-build.sh
```

See the [Docker build guide](docs/public/DOCKER.md) for build options, image metadata,
and troubleshooting.

## Develop the frontend

The supported Node and npm versions are declared in `package.json`. After
installing dependencies with `npm ci`, `npm run dev` starts the Vite
development server, but it does not provide the services behind the web
application. This export does not include a supported backing stack for Vite;
the included demo serves its own built frontend and is not a drop-in Vite
backend.

Using Vite with a complete application therefore requires developer-provided
API, identity, reverse-proxy, and WebSocket-origin configuration. Local
overrides belong in Vite's gitignored `.env.*.local` files and must not contain
values that should remain secret; `VITE_*` values are included in the browser
bundle.

The retained source documentation is indexed in
[`docs/public/README.md`](docs/public/README.md).
