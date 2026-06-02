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
- `notes/` — in-memory CRUD; data resets on restart
- `health/HealthRoutes.kt` — `/health` for the Docker `HEALTHCHECK`
- `src/main/resources/application.yaml` — Ktor port/host config
- `src/main/resources/logback.xml` — logging

## Required environment (widgets)

`STEAM_API_KEY`, `STEAM_ID`, `UPTIME_KUMA_STATUS_SLUG` — when unset, those widgets respond with a non-fatal "not configured" fragment so the rest of the app keeps working. `LETTERBOXD_USERNAME` defaults to `michaellamb`. `UPTIME_KUMA_BASE_URL` defaults to `https://status.michaellamb.dev`. `GITHUB_USERNAME` defaults to `michaellambgelo`; `GITHUB_TOKEN` is optional and, when set, raises the GitHub API rate limit from 60/hr to 5000/hr.

**Recently-updated feed:** `NOW_STORE_URL` defaults to `https://now-store.michaellamb.dev`; `CF_ACCESS_CLIENT_ID` / `CF_ACCESS_CLIENT_SECRET` are the Cloudflare Access service-token credentials Ktor presents to the (Access-gated) now-store Worker. When unreachable, `/widgets/recently-updated` degrades to an "Updates unavailable" fragment. These are wired into the container in `cluster-ops` (`group_vars/all/main.yml`, sourced from `~/.zshrc`).

## Deploy

Image: `ghcr.io/michaellambgelo/kotlin-tutorial:latest` (multi-arch, built by `.github/workflows/build-and-push.yml`).
Host: `node5`. Public URL: `https://kotlin-tutorial.michaellamb.dev`.
Playbook: `~/Workspace/cluster-ops/playbooks/update-kotlin-tutorial.yml`.

## Conventions

- Every tour route file has a header comment explaining the language feature it demonstrates.
- Routes are mounted via `Route.xRoutes()` extension functions, not classes — idiomatic Ktor.
- No persistence layer. If/when adding one, prefer Exposed (JetBrains) over JPA — pick the choice that teaches more Kotlin.
- OpenAPI: Ktor's `openApi` compiler plugin requires **Ktor 3.5+ and Kotlin 2.2.20+** (the version floor was raised for this). Its schema generator is kotlinx.serialization-based, but runtime JSON I/O stays on **Jackson** — DTOs carry `@Serializable` (e.g. `Note`, `Shape`, `HealthResponse`) **only** so the generator can describe them; `serialization/Serializers.kt` maps `UUID`/`Instant` to string schemas. Tour handlers returning ad-hoc `Map<String, Any>` can't be schema'd (kotlinx has no `Any` serializer); `plugins/OpenApi.kt` strips the resulting "Failed to resolve schema" noise so they render as plain objects.
