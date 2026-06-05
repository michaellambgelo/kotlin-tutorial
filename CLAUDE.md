# CLAUDE.md

Ktor 3 + Kotlin 2 + Gradle KTS service. Pedagogical: each `tour/*` route is intentionally short and focused on one Kotlin language feature. Don't refactor away the duplication between tour routes — the duplication is the point.

## Commands

```bash
./gradlew run            # localhost:8080
./gradlew test           # all tests
./gradlew installDist    # build/install/kotlin-tutorial/bin/kotlin-tutorial
```

## Layout

- `src/main/kotlin/dev/michaellamb/tutorial/Application.kt` — `EngineMain.main`, `Application.module()`
- `plugins/` — `Serialization` (Jackson), `Monitoring` (CallLogging), `Cors` (allows blog origins), `Routing` (mounts everything), `OpenApi` (post-processes the generated spec)
- `/swagger` — Swagger UI with fully testable endpoints. The OpenAPI spec is generated from the live routing tree by the `ktor { openApi { codeInferenceEnabled = true } }` compiler plugin (build.gradle.kts), which infers schemas/params from each handler's `call.receive`/`respond`/`parameters` — so tour routes stay untouched. Mounted in `Routing.kt` via `swaggerUI("/swagger") { source = OpenApiDocSource.Routing(...) }`.
- `tour/` — one file per language feature; **add new tour routes here, mount in `Routing.kt`**
- `widgets/` — one file per server-rendered HTML widget consumed by the blog's `/now.html` and `/cluster.html`. Each demonstrates one Kotlin feature (kotlinx.html DSL, Jackson + data classes, structured concurrency, java.time time-window filtering). 60s in-memory TTL cache via `WidgetCache`. `RecentlyUpdatedWidget.kt` also holds `NowStore`, the client for the curated "Recently updated" feed (Cloudflare Worker + KV at `now-store.michaellamb.dev`).
- `admin/AdminRoutes.kt` — `/admin` form to publish/delete "Recently updated" entries (proxies writes to the now-store Worker with the Access service token). Gated at the edge by **Cloudflare Access** (One-time-PIN email policy over `/admin*`); open locally for dev.
- `notes/` — CRUD persisted to SQLite-on-disk via **Exposed (DSL)**. `NotesTable` is the Exposed table; `NotesDatabase.connect()` opens the DB (WAL + busy_timeout set on the `SQLiteDataSource`) and creates the schema; `NoteRepository` runs each op in `newSuspendedTransaction(Dispatchers.IO)`. `createdAt` is stored as **epoch millis (a `long` column)**, not `exposed-java-time`'s `timestamp()` — that column type shifts the `Instant` by the local UTC offset on SQLite round-trips; epoch millis is TZ-proof and sorts chronologically. DB path resolves: system property `notes.db.path` → env `NOTES_DB_PATH` → default `build/notes.db` (gitignored). The container sets `NOTES_DB_PATH=/app/data/notes.db`, mounted as the `kotlin-tutorial-data` named volume on node5 (cluster-ops `group_vars/all/main.yml`) so notes survive redeploys. The `Note` data class + its Jackson/OpenAPI serialization are untouched — Exposed maps rows by hand.
- `health/HealthRoutes.kt` — `/health` for the Docker `HEALTHCHECK`
- `src/main/resources/application.yaml` — Ktor port/host config
- `src/main/resources/logback.xml` — logging

## Required environment (widgets)

`STEAM_API_KEY`, `STEAM_ID`, `UPTIME_KUMA_STATUS_SLUG` — when unset, those widgets respond with a non-fatal "not configured" fragment so the rest of the app keeps working. `LETTERBOXD_USERNAME` defaults to `michaellamb`. `UPTIME_KUMA_BASE_URL` defaults to `https://status.michaellamb.dev`. `GITHUB_USERNAME` defaults to `michaellambgelo`; `GITHUB_TOKEN` is optional and, when set, raises the GitHub API rate limit from 60/hr to 5000/hr.

**Recently-updated feed:** `NOW_STORE_URL` defaults to `https://now-store.michaellamb.dev`; `CF_ACCESS_CLIENT_ID` / `CF_ACCESS_CLIENT_SECRET` are the Cloudflare Access service-token credentials Ktor presents to the (Access-gated) now-store Worker. When unreachable, `/widgets/recently-updated` degrades to an "Updates unavailable" fragment. These are wired into the container in `cluster-ops` (`group_vars/all/main.yml`, sourced from `~/.zshrc`).

## Deploy

Image: `ghcr.io/michaellambgelo/kotlin-tutorial:latest` (**arm64-only**, built natively on **node0**, the M4 Mac mini control node).
Host: `node5`. Public URL: `https://kotlin-tutorial.michaellamb.dev`.
Playbook: `~/Workspace/cluster-ops/playbooks/update-kotlin-tutorial.yml`.

Run **`/deploy`** from this repo (wraps `scripts/deploy.sh`): pushes `main`, then SSHes node0 to run `cluster-ops/scripts/node0-build-deploy.sh kotlin-tutorial` — native arm64 build → push GHCR (`:latest` + `:sha-<short>`) → the playbook pulls on node5, tags the previous image `:rollback`, restarts, and health-gates.

GitHub Actions (`.github/workflows/build-and-push.yml`) still runs **tests on push**, but its image-build job is **`workflow_dispatch`-only** — a manual multi-arch (QEMU) fallback, e.g. if an amd64 image is ever needed.

## Conventions

- Every tour route file has a header comment explaining the language feature it demonstrates.
- Routes are mounted via `Route.xRoutes()` extension functions, not classes — idiomatic Ktor.
- Persistence (the `/notes` endpoints) uses **Exposed (JetBrains) DSL** over SQLite-on-disk — see `notes/`. No HikariCP: SQLite is single-writer regardless of pool size, so a pool buys nothing here. DSL (not DAO) keeps row↔`Note` mapping explicit. If another feature ever needs storage, prefer Exposed over JPA — pick the choice that teaches more Kotlin.
- OpenAPI: Ktor's `openApi` compiler plugin requires **Ktor 3.5+ and Kotlin 2.2.20+** (the version floor was raised for this). Its schema generator is kotlinx.serialization-based, but runtime JSON I/O stays on **Jackson** — DTOs carry `@Serializable` (e.g. `Note`, `Shape`, `HealthResponse`) **only** so the generator can describe them; `serialization/Serializers.kt` maps `UUID`/`Instant` to string schemas. Tour handlers returning ad-hoc `Map<String, Any>` can't be schema'd (kotlinx has no `Any` serializer); `plugins/OpenApi.kt` strips the resulting "Failed to resolve schema" noise so they render as plain objects.
