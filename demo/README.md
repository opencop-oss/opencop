# OpenCOP evaluation demo

This directory runs the full OpenCOP product with `compose.yaml` as its only
required Infra runtime file. Compose builds the web app and all four Java
services from the included `backend/` and `frontend/` source trees, builds the demo's PostgreSQL/PostGIS
and MediaMTX images from inline Dockerfiles, and runs stock Keycloak. No
prepublished OpenCOP image or Azure account is required.

`README.md` and `LIMITATIONS.md` are operator documentation; neither is read
by the runtime.

This is a local testing and evaluation environment, not a production deployment. Its published ports bind to `127.0.0.1`; it has no TLS, commits fixed development credentials and a published demo-only OpenBao seal key, and runs a single-node broker. Its data lives in local Compose volumes, so stopping the stack and plain `docker compose down` keep configured connections, tokens, and map settings. `docker compose down -v` is the full reset.

Read [LIMITATIONS.md](LIMITATIONS.md) before treating the demo as anything
beyond a local evaluation environment. It explains what the demo keeps, what
a reset removes, and the production capabilities intentionally absent from
this topology.

This project provides application source and a local evaluation path; it does
not provide a production hosting architecture. Anyone deploying OpenCOP beyond
local evaluation owns the infrastructure design, security hardening, secret
management, scaling, observability, backup, recovery, upgrades, and ongoing
operations.

## Prerequisites

- Docker Desktop or Docker Engine with Compose 2.36.2 or newer, Buildx, and the default `/var/run/docker.sock`; older Compose releases mis-handle the service-backed build context used to share the local Java base image
- At least 12 GB of memory available to Docker and about 40 GB of free disk
- The included `backend/` and `frontend/` source trees kept beside this `demo/` directory
- Host ports 80, 8554, 9094, and 10000 available; 9094 is the loopback SCRAM endpoint returned to bundled-Kafka producers
- Internet access from the browser for the default basemap, which loads from the public OpenFreeMap service

The full demo path has been exercised with Docker Desktop on Apple silicon and
with Docker Engine on native x86-64 Ubuntu 24.04 LTS. A separate forced
`linux/amd64` run on Apple silicon used emulation; it is not evidence of a
native x86-64 Linux host. Windows Docker Desktop with WSL2 is not yet validated.
The restartable OpenBao storage and the default map content were exercised on
Docker Desktop for macOS.

The required repository layout is:

    repository-root/
      backend/
      frontend/
      demo/
        compose.yaml

## Start OpenCOP

From this directory, run:

    docker compose up --build --wait --wait-timeout 900

The first source build can take 10–25 minutes. Builds complete before Compose
begins its readiness waits. The 900-second value bounds Compose's wait phases;
it does not include source builds and is not a strict whole-command wall-clock
deadline. After the images are built, Keycloak's initial database migration
and identity bootstrap can take several additional minutes, especially with
limited Docker resources or an external PostgreSQL server. Leave the stack
running while Keycloak reports `starting`; the launch returns when the demo is
ready or a readiness wait expires. Later launches reuse the local images.
Compose exits successfully when every long-running service is healthy and all
four initialization jobs have completed.

To follow the complete identity gate from another terminal, run
`docker compose logs -f keycloak demo-user-init`. If the launch fails or
reaches its deadline, retain these diagnostics before resetting the demo:

    docker compose ps --all
    docker compose logs keycloak demo-user-init --tail=100

Open <http://localhost> and sign in with:

- Username: `demo`
- Password: `opencop-demo`

The initializer creates the bundled demo user in stock Keycloak and directly
provisions the matching user through Core's SCIM endpoint. The user receives
administrator access because its email matches `OPENCOP_DEFAULT_ADMIN`.
Keycloak does not contain the custom SCIM event listener, so users created
later through the Keycloak administration UI are not propagated to Core.

The identity configuration is bootstrap-only. If you change the demo
username, password, default administrator, or other Keycloak/SCIM settings
after the first launch, reset the volumes before starting again; the demo does
not reconcile those changes into an existing identity database.

Useful endpoints:

- OpenCOP UI: <http://localhost>
- OpenCOP health: <http://localhost/api/actuator/health>
- Keycloak: <http://localhost/auth>
- MediaMTX HLS router: <http://localhost/streams>
- Azurite blob endpoint: <http://localhost:10000/devstoreaccount1>

To pause the demo and keep its data:

    docker compose stop

To resume it:

    docker compose up --wait --wait-timeout 900

Plain `docker compose down` followed by the same `up` command also keeps the
data, because only `-v` removes the volumes. OpenBao keeps the secrets
OpenCOP writes at runtime, such as connection credentials, mTLS material,
and map-tile tokens, on its `openbao-data` volume and unlocks itself on every
start.

To end the demo and remove all of its local data, including OpenBao's secrets
and the default map and banners:

    docker compose down -v

Older versions of this demo kept OpenBao in memory. After upgrading from one,
reset once with `docker compose down -v` before the first launch; volumes
from the older demo can hold records whose secrets are already gone.

All values required by the bundled path have fixed local defaults in
`compose.yaml`; no environment file is required. Compose accepts supported
override bundles from the shell or an untracked `.env`, which it loads
automatically. Interpolation does not mean that every variable is safe to
change independently.

| Goal | Supported inputs | Boundary |
|---|---|---|
| Choose the Compose project name | `docker compose -p <name> ...` or `COMPOSE_PROJECT_NAME` | The demo network and route discovery follow the effective project name. Use the same project setting for every command. Fixed host ports still allow only one active demo at a time. |
| Customize a fresh bundled login | `IDP_CLIENT_ID`, `IDP_CLIENT_SECRET`, `OPENCOP_DEFAULT_ADMIN`, `OPENCOP_DEMO_USER`, `OPENCOP_DEMO_PASSWORD`, and `OPENCOP_SCIM_BEARER_TOKEN` | Set the complete desired identity values before the first launch. Reset with `docker compose down -v` before changing them. |
| Replace a bundled dependency | The complete variable group in the corresponding section below | Also remove the bundled service, volume, initialization job, and `depends_on` edges named by that section. |

Bundled Keycloak's database credentials are fixed at
`keycloak_user` / `keycloak_pass` because the embedded PostgreSQL
initialization SQL creates exactly that role. Do not set
`KEYCLOAK_DB_URL`, `KEYCLOAK_DB_USERNAME`, or `KEYCLOAK_DB_PASSWORD`
individually while the bundled PostgreSQL service remains. Those three
variables are supported only as a complete group during the external
PostgreSQL replacement described below.

Other partial overrides can split a producer from its consumer—for example,
changing only the Redis or broker settings on the Java services while leaving
the bundled dependency in place. Such combinations are unsupported. Start
from the fixed bundled defaults or follow one complete replacement sequence.

Never put real, customer, or production credentials in `compose.yaml` or
commit an override file. Environment overrides are ordinary Compose
interpolation, not production secret delivery. The public origin is
intentionally fixed at `http://localhost`; non-localhost hostnames require a
different TLS and secure-cookie deployment contract and are not supported by
this demo.

## Default map and credits

A fresh demo opens on the OpenFreeMap `dark` basemap. A header banner marks
the evaluation demo, and a footer banner directly under the map carries the
map credits. Users cannot dismiss either banner. The `demo-content-init` job adds this content once per database,
after Core has migrated it, and records that in a separate `demo_seed` schema.
Later launches leave the map and banners alone, so an administrator can
choose another primary map, edit or delete the banners, or remove the tile,
and those changes are kept. `docker compose down -v` deletes them, and
the next launch adds the defaults again.

The basemap loads in the browser from the public OpenFreeMap service at
<https://tiles.openfreemap.org>, so the map needs internet access. OpenFreeMap
needs no key or account, is provided as-is, and may change or stop without
notice. To use a different basemap, add it as an administrator and make it the
primary map.

Map credits: [OpenFreeMap](https://openfreemap.org),
[© OpenMapTiles](https://openmaptiles.org/), and data © OpenStreetMap
contributors (<https://www.openstreetmap.org/copyright>). OpenCOP's map turns
off MapLibre's attribution control, so the tile source's own credits do not
show; the footer banner is the required on-map credit.

## Replacing bundled dependencies

The Compose file is deliberately readable rather than abstract. Replacing a
dependency has two parts: provide the corresponding environment overrides,
then remove that bundled service and its matching `depends_on` entries from
`compose.yaml`. Put overrides in the shell or an ignored `.env`. Run
`docker compose config -q` before starting the edited file.

`docker compose down -v` resets only the bundled topology. Use dedicated, disposable external dependencies and least-privileged identities, or perform their operator-owned cleanup before starting a new demo session. Clear or recreate the external PostgreSQL databases used by OpenCOP and bundled Keycloak, plus external Redis state, when bootstrap or session identity changes; remove demo-created Blob objects separately. For Event Hubs, delete UI-created sources while Core and its management identity are still available so their dynamic hubs and send rules can be removed before the external topology is torn down.

Only the alternatives named below are known deployment paths. Other hosted Kafka products, AWS services, and external MediaMTX or Vault deployments are not claimed as supported.

### PostgreSQL

OpenCOP supports a reachable PostgreSQL server with PostGIS and `pgcrypto`; Azure Database for PostgreSQL Flexible Server is the exercised managed deployment. The demo also needs a separate `keycloak` database when bundled Keycloak is retained.

1. Create `opencop-db` with PostGIS and `pgcrypto` available, including provider-specific extension allowlisting and role permission where required, plus a separate `keycloak` database.
2. Set `POSTGRES_URL`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` for OpenCOP.
3. Set `KEYCLOAK_DB_URL`, `KEYCLOAK_DB_USERNAME`, and `KEYCLOAK_DB_PASSWORD` for Keycloak.
4. Remove the `postgres` service and its `postgres-data` volume declaration.
5. Remove the `postgres` dependency from `keycloak` and each Java service.
6. Choose what happens to the default map and banners, which `demo-content-init` adds through `psql`:
   - To keep them, edit that service's `environment`: set `PGHOST` to the server, set `PGDATABASE` only if the database is not `opencop-db`, add `PGPORT` when the port is not 5432, and add `PGSSLMODE: require` for a managed server such as Azure Database for PostgreSQL. `PGUSER` and `PGPASSWORD` already follow `POSTGRES_USER` and `POSTGRES_PASSWORD`. Then remove its `postgres` dependency.
   - To skip them, remove the `demo-content-init` service and its entry in the `traefik` service's `depends_on`.

Flyway migrates an empty external `opencop-db`; the demo does not restore either legacy development dump. This path was validated with a separate `postgis/postgis:17-3.5` container on the host and with Azure Database for PostgreSQL Flexible Server after allowlisting `PGCRYPTO,POSTGIS`. That validation predates the `demo-content-init` job, so step 6 has not been exercised against an external server.

### Kafka or Azure Event Hubs

The bundled path is the single-node Kafka service. The only supported external broker alternative is a pre-provisioned OpenCOP-compatible Azure Event Hubs namespace; an arbitrary namespace plus credentials is not sufficient.

The exercised topology used Premium because OpenCOP requires the following 13 fixed hubs and [Standard permits only 10 hubs per namespace](https://learn.microsoft.com/azure/event-hubs/compare-tiers). Another tier is suitable only if it supports this complete topology and [per-hub log compaction](https://learn.microsoft.com/azure/event-hubs/log-compaction).

| Fixed hub | Partitions | Cleanup |
|---|---:|---|
| `opencop.event.external.raw` | 10 | Delete |
| `opencop.event.external.raw.dlt` | 1 | Delete |
| `opencop.geo.mapped` | 10 | Delete |
| `opencop.geo.mapped.dlt` | 1 | Delete |
| `opencop.geo.validated` | 4 | Delete |
| `opencop.geo.validated.dlt` | 1 | Delete |
| `opencop.notification` | 1 | Delete |
| `opencop.notification.dlt` | 1 | Delete |
| `opencop.jobs` | 1 | Delete |
| `opencop.jobs.dlt` | 1 | Delete |
| `opencop.history.jobs` | 1 | Delete |
| `opencop.history.jobs.dlt` | 1 | Delete |
| `opencop.byod.sources` | 3 | Compact, with an explicit tombstone-retention policy |

Provision a namespace authorization rule with Manage, Send, and Listen for application Kafka clients. Separately provision a client-secret Entra service principal with Contributor scoped to the namespace; Core uses it to create and delete per-source hubs, DLT hubs, and send rules.

1. Set `MESSAGING_BROKER_PROVIDER=azure-event-hub`.
2. Set `MESSAGING_BROKER_BOOTSTRAP_URL`,
   `MESSAGING_BROKER_SASL_BOOTSTRAP_URL`,
   `MESSAGING_BROKER_CONNECTION_STRING`, `MESSAGING_BROKER_TENANT_ID`,
   `MESSAGING_BROKER_CLIENT_ID`, `MESSAGING_BROKER_CLIENT_SECRET`,
   `MESSAGING_BROKER_SUBSCRIPTION_ID`, `MESSAGING_BROKER_RESOURCE_GROUP`, and
   `MESSAGING_BROKER_NAMESPACE`.
3. Remove the `kafka` and `kafka-topic-init` services, the Kafka volume, and both matching dependencies from the Java services.

Generic hosted Kafka and other broker providers are not tested by this configuration.

### Azurite or Azure Blob Storage

The supported external blob path is Azure Blob Storage.

1. Set `CLOUD_BLOB_ACCOUNT_NAME`, `CLOUD_BLOB_ACCOUNT_KEY`, `CLOUD_BLOB_CONTAINER`, `CLOUD_BLOB_ENDPOINT`, and `CLOUD_BLOB_PUBLIC_ENDPOINT`.
2. Remove the `azurite` service, its volume, and the `azurite` dependencies from the Java services.
3. Configure the storage account's browser-facing CORS policy for the OpenCOP origin.

### Redis

OpenCOP currently supports the bundled Redis or a compatible standalone
(non-clustered) external Redis deployment. Redis Cluster is not supported
because current Lua scripts and multi-key operations are not cluster-safe.
Clustered Redis support is planned as a near-term improvement.

Classic Azure Cache for Redis Standard without clustering matches the current
topology, but Microsoft is retiring the service and [blocking new cache
creation on its published schedule](https://learn.microsoft.com/azure/azure-cache-for-redis/cache-whats-new).
Azure Managed Redis was not validated for this demo and is not claimed as a
supported replacement.

1. Set `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, and `REDIS_SSL` for the external deployment.
2. Remove the `redis` service, its volume, and the `redis` dependencies from the Java services.

### MediaMTX and OpenBao

MediaMTX and OpenBao are part of the supported demo topology and are not
externalized. MediaMTX supplies the product's stream API/HLS path. OpenBao
supplies the runtime `SecretStore` required by Core; ordinary startup-secret
defaults are declared in `compose.yaml`. Keep them fixed unless they belong to
the fresh-identity bundle or a complete external-dependency replacement above.

OpenBao stores its data on the `openbao-data` volume and unlocks it with a
static seal key written in `compose.yaml`. That key is a published,
demo-only value: it protects nothing, so never reuse it or store real
credentials in the demo. OpenBao cannot read existing data with a different
key, so changing the key requires `docker compose down -v`.

If a launch fails, inspect `docker compose ps --all` and `docker compose logs <service> --tail=100`, correct the failing service, and rerun the launch command; the retained volumes are meant to be reused. If the very first launch was interrupted and `vault` never becomes healthy or `vault-seed` gives up waiting for OpenBao readiness, or `vault` cannot unseal after its configuration was edited, the `openbao-data` volume cannot be used as it is; reset with `docker compose down -v`. Traefik limits route discovery to the effective Compose project name; `docker compose down -v`, run with the same project settings, removes that demo project's containers and volumes.

If the resume command succeeds but `http://localhost` resets connections, run `docker compose restart traefik`.

For the full set of deliberate tradeoffs, see
[LIMITATIONS.md](LIMITATIONS.md). Production hosting and operations are outside
the scope of this demo.
