# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Keycloak is an open source Identity and Access Management (IAM) solution. It runs on Quarkus and is structured as a large Maven multi-module project with a frontend built in React/TypeScript.

## Build Commands

Use the Maven wrapper (`./mvnw` or `mvnw.cmd` on Windows). Requires JDK 17, 21, or 25. Java compiler target is 17.

```bash
# Full build (skip tests)
./mvnw clean install -DskipTests

# Build only the Quarkus server distribution (fastest way to get a runnable server)
./mvnw -pl quarkus/deployment,quarkus/dist -am -DskipTests clean install

# Build with distribution packaging
./mvnw clean install -DskipTests -Pdistribution

# Rebuild a single module after a code change (e.g. services)
./mvnw -f services/pom.xml clean install -DskipTests

# Enable Maven build cache for faster rebuilds
./mvnw -Dmaven.build.cache.enabled=true clean install -DskipTests

# Skip proto-schema compatibility checks (needed in proxy environments)
./mvnw clean install -DskipTests -DskipProtoLock=true
```

The built distribution ZIP is in `quarkus/dist/target/`.

### Maven Profiles

- `distribution` — Package the distribution
- `operator` — Build Kubernetes Operator (excluded by default)
- `fips140-2` — FIPS 140-2 crypto support
- `auth-server-quarkus` — Run tests against Quarkus server (production mode)
- `auth-server-quarkus-embedded` — Run tests against embedded Quarkus (supports IDE)
- `db-postgres`, `db-mysql`, `db-mariadb`, `db-mssql`, `db-oracle`, `db-aurora-postgres` — Database profiles for testing

### Code Formatting

Spotless enforces code formatting (Google Java style, LF line endings). Check and fix before submitting:

```bash
./mvnw spotless:check    # check formatting (must use ./mvnw, not mvn)
./mvnw spotless:apply    # auto-fix formatting
```

**Note:** Running `mvn spotless:check` instead of `./mvnw spotless:check` produces a misleading error about missing goal 'verify' — always use the wrapper.

### Java Import Ordering

Enforced by `.editorconfig` and Spotless. No wildcard imports. Order:

1. `java.**`
2. `javax.**`
3. `jakarta.**`
4. `org.keycloak.**`
5. Everything else
6. Static imports (same grouping)

## Running Keycloak in Dev Mode

From the command line (debugger on port 5005):

```bash
cd quarkus
../mvnw -f server/pom.xml compile quarkus:dev \
  -Dkc.config.built=true \
  -Dkc.home.dir=../.kc \
  "-Dquarkus.args=start-dev --bootstrap-admin-username=admin --bootstrap-admin-password=admin"
```

From IDE: run `org.keycloak.Keycloak` main class in `quarkus/tests/junit5`. Server starts on `http://localhost:8080`. Requires these JVM arguments:
```
-Djava.util.logging.manager=org.jboss.logmanager.LogManager
-Djava.util.concurrent.ForkJoinPool.common.threadFactory=io.quarkus.bootstrap.forkjoin.QuarkusForkJoinWorkerThreadFactory
```

Set `-Dkc.home.dir` to persist server state (H2 database, config) between restarts.

**Hot reload limitations:** Only changes in `quarkus/deployment`, `quarkus/runtime`, and `quarkus/server` modules auto-reload. Changes in other modules (e.g., `services`) require rebuilding that module first, or use IDE Hot Swap via the debugger.

## Testing

Keycloak focuses on integration/functional tests over unit tests. No mocking frameworks are used. Use `org.hamcrest.MatcherAssert.assertThat` with Hamcrest matchers for assertions.

```bash
# Run the base integration testsuite (development mode, embedded Undertow — fastest)
./mvnw -f testsuite/integration-arquillian/pom.xml clean install

# Run a single test
./mvnw -f testsuite/integration-arquillian/pom.xml clean install -Dtest=LoginTest

# Run tests on Quarkus (production mode — requires full distribution build first)
./mvnw -f testsuite/integration-arquillian/pom.xml -Pauth-server-quarkus clean install

# Run tests on embedded Quarkus (supports IDE execution)
./mvnw -f testsuite/integration-arquillian/pom.xml -Pauth-server-quarkus-embedded clean install -Dtest=LoginTest

# Use a real browser instead of HtmlUnit
-Dbrowser=chrome   # or -Dbrowser=firefox

# Debugging tests
-Darquillian.debug=true              # Arquillian debug output
-Dmaven.surefire.debug=true          # Attach debugger to test JVM
-Dpageload.timeout=3600000           # Extend WebDriver page load timeout (default 10s)

# Enable/disable server features for tests
-Dauth.server.feature=-Dkeycloak.profile.feature.FEATURE_NAME=disabled
```

Main integration tests live in `testsuite/integration-arquillian/tests/base`. A good starting point is `org.keycloak.testsuite.forms.LoginTest`. Tests can run from the IDE — an embedded Keycloak server starts automatically.

For detailed test execution options, see `testsuite/integration-arquillian/HOW-TO-RUN.md`.

### New Test Framework

A modern JUnit5-based test framework in `test-framework/` is the preferred approach for new tests. It replaces Arquillian for new contributions. Annotate test classes with `@KeycloakIntegrationTest`:

```java
@KeycloakIntegrationTest
public class MyTest {
    @InjectRealm
    ManagedRealm realm;

    @InjectOAuthClient
    OAuthClient oAuthClient;

    @Test
    public void test() {
        TokenResponse response = oAuthClient.clientCredentialGrant();
        Assertions.assertTrue(response.indicatesSuccess());
    }
}
```

Server mode is controlled by `KC_TEST_SERVER` env var (`embedded` default in-repo, `distribution`, `remote`). Key options:
- `KC_TEST_SERVER=embedded` — run Keycloak within the test JVM
- `KC_TEST_SERVER_REUSE=true` — keep server running between test runs
- `KC_TEST_DATABASE=postgres` — use a database container (requires `keycloak-test-framework-db-postgres` dep)
- `KC_TEST_BROWSER=chrome` — use a real browser for UI tests

Available injections include `@InjectRealm`, `@InjectClient`, `@InjectUser`, `@InjectOAuthClient`, `@InjectPage` (Selenium), `@InjectWebDriver`, `@InjectAdminClient`, `@InjectEvents`, `@InjectRunOnServer`, `@InjectMailServer`, and more. See `test-framework/docs/WRITING_TESTS.md` for the full reference.

### Model Testsuite

Located in `testsuite/model/`. Tests the storage/model layer directly against a raw `KeycloakSessionFactory` — much faster than the full integration testsuite and useful when changing model or persistence code.

```bash
# Run model tests with JPA (H2 in-memory)
./mvnw -f testsuite/model/pom.xml test -Pjpa

# Run a single model test
./mvnw -f testsuite/model/pom.xml test -Pjpa -Dtest=ClientModelTest
```

### Test Utilities

Located in `testsuite/utils/`:

```bash
# Start a test Keycloak server (admin/admin credentials)
./mvnw -f testsuite/utils/pom.xml exec:java -Pkeycloak-server

# Start with live theme editing (loads from filesystem, no restart needed)
./mvnw -f testsuite/utils/pom.xml exec:java -Pkeycloak-server -Dresources

# Start mock mail server (localhost:3025)
./mvnw -f testsuite/utils/pom.xml exec:java -Pmail-server

# Start embedded LDAP server (localhost:10389)
./mvnw -f testsuite/utils/pom.xml exec:java -Pldap
```

## Frontend (JavaScript/TypeScript)

Located in `js/`. Uses pnpm 10.x (exact version in `js/package.json` `packageManager` field), Node.js 24+, and wireit for build orchestration. Husky manages git hooks.

```bash
cd js
pnpm install

# Build all JS packages
pnpm build

# Dev server for admin UI (requires running Keycloak backend)
pnpm --filter @keycloak/keycloak-admin-ui dev

# Dev server for account UI
pnpm --filter @keycloak/keycloak-account-ui dev

# Lint
pnpm --filter @keycloak/keycloak-admin-ui lint

# Tests
pnpm --filter @keycloak/keycloak-admin-ui test
```

**Apps:** `js/apps/admin-ui` (Admin Console, React + PatternFly), `js/apps/account-ui` (Account Console, React)
**Libs:** `js/libs/keycloak-admin-client` (Admin REST API client), `js/libs/ui-shared` (shared UI components)

### Admin Client OpenAPI Generation

The admin client TypeScript types are generated from `js/libs/keycloak-admin-client/openapi.yaml`. After changing Admin REST API endpoints, update the OpenAPI spec and regenerate:

```bash
cd js
pnpm --filter @keycloak/keycloak-admin-client generate:openapi
```

This runs automatically on `pnpm install` via the `postinstall` hook.

### Frontend Dev Mode with Proxy

The `js/apps/keycloak-server` package downloads and runs a Keycloak server that proxies the UI to a local Vite dev server — no Java build required for frontend work:

```bash
cd js
# Start Keycloak (nightly) proxying the admin UI to http://localhost:5174
pnpm --filter keycloak-server start --admin-dev
# (or --account-dev for the account UI at http://localhost:5173)

# In a second terminal, start the Vite dev server
pnpm --filter keycloak-admin-ui run dev
```

To use the locally built Quarkus distribution instead of nightly: `pnpm --filter keycloak-server start --local`.

### Frontend Coding Guidelines

- **State management**: No Redux. Prefer local component state → lift state up → component composition → global context (in that order).
- **CSS**: Use PatternFly variables for colors/spacing. Custom classes follow the `keycloak-admin--block[__element][--modifier]` BEM convention. Component-specific CSS goes in a `.css` file alongside the component.
- **TypeScript**: Use `strict-type-checked` ESLint config. Non-null assertion operator (`!`) is only acceptable for types generated from the Admin API client.
- **Testing**: Playwright for UI tests.

## Architecture

### Server SPI System

Keycloak uses a Service Provider Interface (SPI) architecture:
- **`server-spi`** — Public SPI interfaces (stable API for extensions)
- **`server-spi-private`** — Internal SPI interfaces (can change between versions)
- **`services`** — Default provider implementations

New SPIs/providers are registered via `META-INF/services/` files in their respective modules. The `KeycloakSessionFactory` discovers and manages all providers.

### Quarkus Extension

Keycloak runs as a Quarkus extension with build-time augmentation:
- **`quarkus/deployment`** — Build-time processing: discovers SPIs/providers, configures Hibernate/Infinispan/etc., generates optimized bytecode
- **`quarkus/runtime`** — Runtime code, linked to deployment via `KeycloakRecorder`
- **`quarkus/dist`** — Packages the distribution
- **`quarkus/server`** — Generates the server artifacts

### Key Modules

- **`core`** — Core representations and model classes
- **`model`** — Data model and storage (JPA, Infinispan cache)
- **`services`** — REST endpoints, authentication flows, event listeners, provider implementations
- **`rest`** — Admin REST API
- **`crypto`** — Cryptographic providers (default BouncyCastle, FIPS variants)
- **`themes`** — FreeMarker templates and theme resources (login, account, admin, email)
- **`federation`** — User federation (LDAP, etc.)
- **`authz`** — Authorization services
- **`saml-core` / `saml-core-api`** — SAML protocol support
- **`operator`** — Kubernetes Operator (excluded by default, build with `-Poperator`)
- **`test-framework`** — Test infrastructure

### Themes

Login/account page templates are FreeMarker (`.ftl`) files in `themes/src/main/resources/theme/`. CSS and static assets live alongside the templates. Admin and Account UIs are React SPAs built from `js/apps/` and packaged into the theme during the Maven build.

Live theme editing: start the test server with `-Dresources` to load templates from the filesystem without rebuilding.

## Custom Enterprise Features (Fork Additions)

This fork adds enterprise security features on top of upstream Keycloak. These are **not** part of the upstream project.

### Custom Admin REST Extension Pattern

All custom realm-scoped REST resources follow the `AdminRealmResourceProvider` SPI:
- Implement both `AdminRealmResourceProviderFactory` and `AdminRealmResourceProvider`
- `getResource()` returns a JAX-RS sub-resource object
- Register the factory in `services/src/main/resources/META-INF/services/org.keycloak.services.resources.admin.ext.AdminRealmResourceProviderFactory`
- Requests to `/admin/realms/{realm}/{PROVIDER_ID}` are automatically routed via `RealmAdminResource.extension()` (~line 650 in `services`)

### Custom Features

**Cloud Apps** (`PROVIDER_ID=cloud-apps`) — `services/.../admin/cloudapps/`
- M2M service-account client registration with hardened secure defaults
- API: `GET/POST /admin/realms/{realm}/cloud-apps`
- Uses built-in `cloud-application` client profile in `services/src/main/resources/keycloak-default-client-profiles.json`
- Admin UI: `js/apps/admin-ui/src/realm-settings/CloudAppsTab.tsx` (tab registered in `RealmSettingsTabs.tsx`)

**Backup & Restore** — `services/.../admin/backup/`
- Export/import realm configurations as ZIP files (users, clients, roles)
- API: `POST/GET /admin/backups`, `GET /admin/backups/{id}/download`, `POST /admin/backups/{id}/restore`, `DELETE /admin/backups/{id}`
- Storage path configurable via `keycloak.backup.dir` system property
- Note: mounted directly on `AdminRoot`, not via the realm extension SPI

**Incident Webhooks** (`PROVIDER_ID=incident`) — `services/.../admin/incident/`
- Security event webhook notifications to external incident management systems
- API: `GET/PUT /admin/realms/{realm}/incident`, `POST .../incident/test`
- Event listener: `IncidentEventListenerProviderFactory` (dynamically enabled/disabled per realm config)

**PAM Integration** (`PROVIDER_ID=pam`) — `services/.../admin/pam/`
- Session event notifications to Privileged Access Management systems
- API: `GET/PUT /admin/realms/{realm}/pam`, `POST .../pam/test`
- Event listener: `PamSessionEventListenerProviderFactory`

**SIEM Forwarding** (`PROVIDER_ID=siem`) — `services/.../admin/siem/`
- Log export to SIEM systems; supports global defaults (master realm) overridden per realm
- API: `GET/PUT /admin/realms/{realm}/siem`, `POST .../siem/test`; global: `GET/PUT /admin/realms/master/siem/global`
- Event listener: `LogExportEventListenerProviderFactory`; signing secrets are masked (`***`) in API responses

**Audit Reports** (`PROVIDER_ID=audit-reports`) — `services/.../admin/audit/`
- Compliance reporting: authentication events, admin activity, security events, per-user activity (GDPR)
- API: `GET /admin/realms/{realm}/audit-reports/{authentication|admin-activity|security|user-activity}`; supports CSV export

**Event Integrity** (`PROVIDER_ID=event-integrity`) — `services/.../admin/integrity/`
- SHA-256 hash stored alongside each event for tamper detection
- API: `GET /admin/realms/{realm}/event-integrity` (runs verification scan)
- DB migration: `INTEGRITY_HASH` columns added in `model/jpa/src/main/resources/META-INF/jpa-changelog-27.0.0.xml`
- Key classes: `model/jpa/.../EventIntegrityUtil.java`, `JpaEventStoreProvider.verifyIntegrity()`
- Admin UI: `js/apps/admin-ui/src/realm-settings/event-config/EventsTab.tsx`

**Limit Enforcement** — `server-spi/.../models/LimitExceededException.java`, `model/jpa/.../JpaUserProvider.java`, `services/.../managers/RealmManager.java`
- Hard caps enforced at the model layer: max 1 non-master realm (`MAX_NON_MASTER_REALMS`), max 20 regular users per realm (`MAX_USERS_PER_REALM`)
- Service accounts (prefix `service-account-`) are excluded from the user count
- `LimitExceededException` (extends `ModelException`) is thrown at creation time; caught by `RealmsAdminResource` and `UsersResource` and returned as HTTP 403 Forbidden

## Post-Change Build & Verification (MANDATORY)

After every code change, you MUST build the affected module(s) and verify the server starts correctly. Never leave the software in a broken state.

### Step 1: Set JAVA_HOME (required on this machine)
```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.2.10-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
```

### Step 2: Kill any running Keycloak server
```bash
tasklist 2>/dev/null | grep java.exe | awk '{print $2}' | while read pid; do taskkill //F //PID $pid 2>/dev/null; done
```

### Step 3: Rebuild the changed module(s)
Build only what you changed. Common modules:
```bash
./mvnw -f services/pom.xml clean install -DskipTests          # services (REST, auth, events)
./mvnw -f server-spi/pom.xml clean install -DskipTests         # public SPI interfaces (e.g. LimitExceededException)
./mvnw -f server-spi-private/pom.xml clean install -DskipTests # internal SPI interfaces
./mvnw -f model/jpa/pom.xml clean install -DskipTests          # JPA model/persistence layer
./mvnw -f model/infinispan/pom.xml clean install -DskipTests   # model/cache layer
./mvnw -f themes/pom.xml clean install -DskipTests             # themes/templates
./mvnw -f crypto/default/pom.xml clean install -DskipTests     # crypto providers
```
For frontend changes:
```bash
cd js && pnpm --filter @keycloak/keycloak-admin-ui build       # admin UI
cd js && pnpm --filter @keycloak/keycloak-account-ui build     # account UI
```

**Important:** If you change an interface in `server-spi` or `server-spi-private`, you MUST also rebuild all modules that implement it (e.g., `services`, `model/infinispan`, `model/jpa`). Failure to do so causes `Unresolved compilation problem` errors at runtime.

### Step 4: Start the server and verify

**IMPORTANT — `quarkus:dev` classloader bug on JDK 25:** On this machine (JDK 25), `quarkus:dev` fails with `JpaConnectionSpi not a subtype` or `Truncated class file` errors due to a Quarkus classloader incompatibility. **Do NOT waste time retrying `quarkus:dev`** — it will not work.

**Use the distribution-based approach instead:**

```bash
# Option A: If the distribution tar.gz already exists in Maven local repo
DIST_TAR="$HOME/.m2/repository/org/keycloak/keycloak-quarkus-dist/999.0.0-SNAPSHOT/keycloak-quarkus-dist-999.0.0-SNAPSHOT.tar.gz"
if [ -f "$DIST_TAR" ]; then
  rm -rf .kc-dist
  mkdir -p .kc-dist && cd .kc-dist
  tar xzf "$DIST_TAR"
  cd ..
fi

# Replace the updated module JARs in the distribution:
# For services changes:
cp services/target/keycloak-services-999.0.0-SNAPSHOT.jar \
   .kc-dist/keycloak-999.0.0-SNAPSHOT/lib/lib/main/org.keycloak.keycloak-services-999.0.0-SNAPSHOT.jar

# For admin UI changes:
cp js/apps/admin-ui/target/keycloak-admin-ui-999.0.0-SNAPSHOT.jar \
   .kc-dist/keycloak-999.0.0-SNAPSHOT/lib/lib/main/org.keycloak.keycloak-admin-ui-999.0.0-SNAPSHOT.jar

# Start the server from the distribution
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.2.10-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
.kc-dist/keycloak-999.0.0-SNAPSHOT/bin/kc.sh start-dev \
  --bootstrap-admin-username=admin --bootstrap-admin-password=admin
```

```bash
# Option B: If no distribution exists, build it first (takes ~10 min)
./mvnw -pl quarkus/deployment,quarkus/dist -am -DskipTests clean install
# Then extract from quarkus/dist/target/ and run as above
```

### Step 5: Health check
```bash
# Wait for startup then check
sleep 45
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/
# Expected: 302 (redirect to login)

# Verify login works
curl -s http://localhost:8080/realms/master/protocol/openid-connect/token \
  -d "client_id=admin-cli" -d "username=admin" -d "password=admin" -d "grant_type=password" | head -c 50
# Expected: {"access_token":"eyJ...
```

If the health check fails, check server logs for errors and fix before reporting the change as complete.

### Node.js / pnpm Path Fix

On this Windows machine, `node` is not in the default shell PATH. Always prepend it:
```bash
export PATH="/c/Program Files/nodejs:$PATH"
```
Use `npx pnpm` or the full path if `pnpm` alone fails with "cannot execute: Is a directory".

## Docker Deployment

The repo includes Docker support for production deployment with PostgreSQL:
- `Dockerfile` — Two-stage build: JDK 25 builder runs Quarkus augmentation (`kc.sh build --db=postgres`), JRE 25 runtime image
- `docker-compose.yml` — PostgreSQL 16 + Keycloak with health checks, named volumes for data persistence
- `DEPLOY.md` — Full step-by-step deployment guide (build, configure `.env`, start, reverse proxy setup, backup, upgrades)

```bash
# Build distribution, then Docker image
./mvnw -pl quarkus/deployment,quarkus/dist -am -DskipTests clean install
docker compose build

# Configure secrets in .env (DB_PASSWORD, KC_HOSTNAME, BOOTSTRAP_ADMIN_PASSWORD)
# Start services
docker compose up -d
```

The `.env` file (gitignored) holds runtime secrets. `DEPLOY.md` has the full reference.

## Contributing Guidelines

- One feature/change per PR, one commit per PR, rebased on main (`git rebase`, not `git pull`)
- Each PR needs an associated GitHub Issue
- Commit message format: brief summary, optional details, then `Closes #1234` as the last line
- Commits must be signed off: `git commit --signoff`
- Larger changes require a [GitHub Discussion](https://github.com/keycloak/keycloak/discussions/categories/ideas) first
- New contributors: max 2 open PRs at a time
- Do not format or refactor unrelated code
- Do not add mocking frameworks
- PRs must include relevant documentation updates (see `docs/documentation/README.md`)
- AI agent usage must be disclosed in the PR description
- IDE builds: run initial Maven build first so generated code is available, then use `Build → Build Project` (not Rebuild)
- Database schema changes: see `docs/updating-database-schema.md`
- New dependencies require discussion on the dev mailing list first
