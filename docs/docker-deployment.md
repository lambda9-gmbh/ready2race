# Docker deployment

Ready2Race runs as three services: a frontend nginx gateway, the Kotlin backend and PostgreSQL 17.
Every Compose project has its own database, uploaded files and network. The frontend gateway serves
the web application and forwards `/api/` and `/static/` to the backend. HTTPS belongs to the server's
reverse proxy; this can be an existing host service, an existing container or the optional Caddy example.

## Install a published release

Prerequisites: Docker Engine with Docker Compose v2 supporting `up --wait`, and a configured SMTP
account. The current backend requires SMTP settings. Use a separate env file for every installation.

From a checkout of the desired release:

```sh
cp .env.example .env
chmod 600 .env
```

Edit `.env` before starting:

- Set a stable, unique `COMPOSE_PROJECT_NAME`, for example `ready2race-club-a`.
- Set `BACKEND_VERSION` and `FRONTEND_VERSION` to an available release tag, including its `v` prefix
  when present. Use the same release for both unless their compatibility has been verified.
- Choose an unused `HTTP_PORT`, for example `8081`.
- Set database password, initial admin credentials and a random `SECURITY_PEPPER`. Generate separate
  secrets with `openssl rand -hex 32`. Preserve the pepper when restoring or updating the instance.
- Fill in your SMTP settings. Keep this file out of source control.

Optional SMTP reply/local-host settings, recipient whitelist and WebDAV settings are also passed
through from the instance env file; examples are in `.env.example`. Blank optional values count as
unset. WebDAV still requires the complete scheme/host/user/password group when enabled.

```sh
docker compose --env-file .env pull
docker compose --env-file .env up -d --wait
```

The gateway is reachable at `http://127.0.0.1:8081` on the Docker host. Only this local port is
published; PostgreSQL and the backend are accessible through the instance network. Configure your
reverse proxy and point the public domain's DNS at the server. Local HTTP can be used for an initial
check; installable PWA features need HTTPS or localhost.

You can also deploy without a source checkout: obtain `compose.yaml` and `.env.example` from the
same release and put them in your installation directory. Proxy examples are optional additional files.
No Java, Node.js or Gradle installation is needed on the deployment host.

**Until the first Docker-enabled release has been published, use the local build below.** Adding the
workflow does not itself publish images. Newly created GHCR packages must be changed to **public**
once, so self-hosters can pull them without a GitHub login.
Maintainers should follow the [first release checklist](#first-docker-release-github-checklist).

## Build from source

Use a source checkout and fill in `.env` as above. Version entries can remain placeholders when using
the build override; Compose still requires them during interpolation of the base file.

```sh
docker compose --env-file .env -f compose.yaml -f compose.build.yaml up -d --build --wait
```

This builds `ready2race-backend:local` and `ready2race-frontend:local` for the host architecture.
The backend Dockerfile starts a temporary PostgreSQL database **inside its build stage** for Flyway
and jOOQ generation. It never uses an installed instance's database, and neither PostgreSQL nor the
JDK is included in the backend runtime image. The application runs as UID/GID 10001, with the static
files volume writable by that user. Local env files, generated builds and dependencies are excluded
from the Docker build context. The frontend image contains no installation-specific URLs or secrets.

The existing `backend/docker-compose.yaml` remains the development database setup. It is not the
production Compose file. Gradle additionally accepts `BUILD_DATABASE_URL`, `BUILD_DATABASE_USER`
and `BUILD_DATABASE_PASSWORD`; without them, the existing localhost development defaults remain.

## Multiple instances and versions

Create `deploy/club-a.env` and `deploy/club-b.env` from `.env.example`. Give them different project
names, local HTTP ports and secrets. Set versions independently:

```sh
docker compose --env-file deploy/club-a.env up -d --wait
docker compose --env-file deploy/club-b.env up -d --wait
```

Compose prefixes volume and network names with the project name. Do not change an existing instance's
project name: doing so creates a new stack and new, empty volumes. Do not add shared volume names or
`container_name` settings. A single host reverse proxy can route both domains to their local ports.

For source builds of different versions, build each checkout under a distinct image tag and set
`BACKEND_IMAGE`, `BACKEND_VERSION`, `FRONTEND_IMAGE` and `FRONTEND_VERSION` in the instance env files.
The `:local` build override is intended for one working checkout, rather than versioned installations.

## Reverse proxy choices

### Existing Caddy or nginx on the host

Keep your existing proxy. Add domain routing to the local port; examples are in
`deploy/Caddyfile.host.example` and `deploy/nginx.host.example.conf`. These examples need your real
domains and, for nginx, your certificate paths. Reload the existing proxy after adding the configuration.
There is no Caddy container in the default Ready2Race stack.

### Existing reverse proxy in Docker

Set `PROXY_NETWORK` to the existing proxy's Docker network and `PROXY_ALIAS` to a name unique to the
instance, such as `ready2race-club-a-web`. Start with the network override:

```sh
docker compose --env-file deploy/club-a.env \
  -f compose.yaml -f deploy/compose.proxy-network.yaml up -d --wait
```

Connect the proxy container to that network if it is not already attached. Route the domain to
`ready2race-club-a-web:80`, not to `127.0.0.1` inside the proxy container. Only the frontend gateway
joins the external network; the backend and database remain on the project network. The local
loopback port also remains available for troubleshooting.

For stronger separation, create a separate proxy network per instance and connect the central proxy
to each one. A shared proxy network lets its attached frontend gateways communicate with each other.

### New server with no reverse proxy

Set `PUBLIC_DOMAIN` and `ACME_EMAIL` and use the optional Caddy example:

```sh
docker compose --env-file .env \
  -f compose.yaml -f deploy/compose.caddy.yaml up -d --wait
```

Caddy owns ports 80 and 443 and persists its certificate state in volumes. DNS must resolve to this
server and those ports must be reachable. Do not use this override when an existing proxy already
owns the ports. This is a single-instance starting point; for several instances, operate one central
proxy with all domain routes instead of starting a separate Caddy on ports 80/443 for each stack.

## Runtime frontend configuration and embedding

The frontend container writes `/runtime-config.js` at startup, before the browser initializes the
application. Changing its environment and running `docker compose up -d` updates configuration without
rebuilding the image. That file is public: put only public URLs in it, never passwords or other secrets.
It is served with `Cache-Control: no-store` and excluded from the PWA precache. The service worker uses
network-first loading with an offline fallback to the previously loaded configuration.

Default URLs are `/api`, `/static/theme.json`, `/static/fonts`, `/static/logos` and `/r2r_logo.png`.
For embedding directly in a page on another domain, set absolute URLs:

```dotenv
API_BASE_URL=https://race.example.org/api
THEME_URL=https://race.example.org/static/theme.json
FONTS_URL=https://race.example.org/static/fonts
LOGOS_URL=https://race.example.org/static/logos
R2R_LOGO_URL=https://race.example.org/r2r_logo.png
CORS_ALLOWED_ORIGINS=https://club.example.org,https://www.club.example.org
```

Load `https://race.example.org/runtime-config.js` **before** the application's module script in the
embedding page. Use absolute URLs for its module script and stylesheet as well. The gateway permits
cross-origin loading of public build assets. API and backend static resources use the explicit CORS
origin list, including the existing `X-Api-Session` header. Origins contain scheme and optional port,
but no path, credentials, wildcard or trailing slash. Production CORS is disabled when the list is empty.

The embedding page can override individual settings by defining `window.READY2RACE_CONFIG` before
loading the config script. Existing source deployments using `VITE_*` settings still work: runtime
settings take precedence, then build settings, then relative defaults. An iframe loads the complete
Ready2Race page with its own origin and normally needs no cross-origin API configuration.

## Updates, backups and restores

The backend migrates its own database before accepting requests. Back up **both** PostgreSQL and
the static files volume, and preserve the instance env file securely. For a consistent backup, stop
the application while taking it. The examples below use the base deployment and `.env`; include any
overrides and the correct env file for your installation in all lifecycle commands.

```sh
mkdir -p backup
chmod 700 backup
docker compose --env-file .env stop frontend backend
docker compose --env-file .env exec -T db \
  pg_dump -U ready2race -d ready2race -Fc > backup/database.dump
docker compose --env-file .env run --rm --no-deps --user 0 --entrypoint tar backend \
  -C /data/static -czf - . > backup/static.tar.gz
docker compose --env-file .env up -d --wait
```

Update the version entries, then run `pull` and `up -d --wait` for the target instance. Other Compose
projects retain their versions. Release tags must not be reused for different contents. For exact
image pinning, set e.g. `BACKEND_IMAGE=ghcr.io/lambda9-gmbh/ready2race-backend@sha256` and
`BACKEND_VERSION=<digest hex>`; Compose combines these into `image@sha256:<digest hex>`.

An older image may not work with a newly migrated database. Roll back by restoring the matching
database and static backup and selecting the corresponding image versions. Restore into a fresh
installation with the original secrets and versions, start only the database, and restore before
starting the backend:

```sh
docker compose --env-file .env up -d --wait db
docker compose --env-file .env exec -T db \
  pg_restore -U ready2race -d ready2race --exit-on-error < backup/database.dump
docker compose --env-file .env run --rm --no-deps --user 0 --entrypoint sh backend \
  -c 'tar -C /data/static -xzf - && chown -R 10001:10001 /data/static' < backup/static.tar.gz
docker compose --env-file .env up -d --wait
```

`docker compose down` retains the data volumes. `down -v` removes them and is only suitable when
deliberately deleting an installation. PostgreSQL major upgrades require their own migration process;
do not change the database major version by simply replacing its image tag.

## Release workflow and validation

`.github/workflows/docker.yaml` runs frontend and backend tests, builds both images and runs
`scripts/docker-smoke-test.sh` on pull requests and main. Publishing a GitHub release additionally
builds and pushes both images for `linux/amd64` and `linux/arm64`, tagged with the exact release tag.
No `latest` tag is deployed implicitly. The workflow uses the repository's `GITHUB_TOKEN`; package
write permission must be permitted in the organization. Images are named from the repository, so
forks publish under their own namespace and can override the default image names in Compose.

### First Docker release: GitHub checklist

1. **Commit and push the Docker changes, then merge them into `main`.** The release tag must point
   to a commit containing `.github/workflows/docker.yaml`, the Dockerfiles and the deployment files.
   Existing releases do not acquire these files retroactively.
2. **Check Actions permissions:** repository **Settings → Actions → General**. Actions must be
   enabled and the actions used by this workflow from `actions/*` and `docker/*` must be allowed.
   If an organization policy overrides repository settings, an organization administrator must
   allow them there. A policy requiring full commit-SHA references also requires pinning the
   remaining version-tag references in the workflow.
3. **Check package policy:** the organization must allow this repository to create container
   packages and make them public. The publish job already requests `contents: read` and
   `packages: write` and logs in with the automatically provided `GITHUB_TOKEN`. Normally no
   personal access token, additional repository secret or blanket write-permission setting is
   needed. If packages with the intended names already exist, grant the repository write access
   under the package's **Settings → Manage Actions access** when it does not inherit that access.
4. **Wait for the `Docker` workflow on `main` to pass.** Inspect its result in the repository's
   **Actions** tab. This verifies tests, AMD64 image builds and the two-instance deployment test.
   A manual workflow run executes verification only; it does not publish images.
5. **Publish a GitHub release** from the commit containing these changes, with a new tag such as
   `v1.2.0`. Use **Releases → Draft a new release → Publish release**. A draft release or pushing
   a tag alone does not trigger image publication in this workflow. Wait for the release-triggered
   `Docker` workflow's `verify` job and both `publish` jobs to succeed. Do not reuse a published
   release tag for different source code.
6. **Make both packages public once:** organization **Packages → ready2race-backend /
   ready2race-frontend → Package settings → Change visibility → Public**. Package visibility is
   separate from repository visibility; an open-source repository alone does not make its container
   images publicly downloadable. For a fork, use its corresponding package names and namespace.
7. **Verify public installation before announcing Docker support.** From a machine without GHCR
   credentials, pull both images using the exact release tag, set that tag in `BACKEND_VERSION`
   and `FRONTEND_VERSION`, and follow the installation steps above. An isolated, empty Docker
   credential directory can also check anonymous image access without changing your normal login:

   ```sh
   registry_test_config=$(mktemp -d)
   docker --config "$registry_test_config" pull ghcr.io/lambda9-gmbh/ready2race-backend:v1.2.0
   docker --config "$registry_test_config" pull ghcr.io/lambda9-gmbh/ready2race-frontend:v1.2.0
   rm -rf "$registry_test_config"
   ```

   Replace `v1.2.0` with the release you actually published. Both downloads must succeed. The
   publish jobs build AMD64 and ARM64 manifests, but the workflow's deployment test runs on AMD64;
   validate startup on an ARM64 host as well before claiming tested ARM64 operation.

For later releases, publish a new release tag and check the workflow and public downloads again.
Actions/package policy and package visibility normally only need to be configured once. Deployments
are updated explicitly by their operators; publishing a release does not update running instances.

References: [Actions permissions](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository),
[GHCR authentication](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry),
and [package visibility and Actions access](https://docs.github.com/en/packages/learn-github-packages/configuring-a-packages-access-control-and-visibility).

### Run the deployment test locally

To run the deployment smoke test locally after building both `:local` images:

```sh
sh scripts/docker-smoke-test.sh
```

It needs Docker Compose, curl, Node.js and standard shell tools. It starts two temporary stacks using
random local ports, checks configuration, CORS, persistence and database/file separation, then removes
only its own test stacks and volumes. It never connects to existing installations or uses real credentials.
