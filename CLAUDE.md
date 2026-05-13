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
- `plugins/` — `Serialization` (Jackson), `Monitoring` (CallLogging), `Cors` (allows blog origins), `Routing` (mounts everything)
- `tour/` — one file per language feature; **add new tour routes here, mount in `Routing.kt`**
- `widgets/` — one file per server-rendered HTML widget consumed by the blog's `/now.html` and `/cluster.html`. Each demonstrates one Kotlin feature (kotlinx.html DSL, Jackson + data classes, structured concurrency). 60s in-memory TTL cache via `WidgetCache`.
- `notes/` — in-memory CRUD; data resets on restart
- `health/HealthRoutes.kt` — `/health` for the Docker `HEALTHCHECK`
- `src/main/resources/application.yaml` — Ktor port/host config
- `src/main/resources/logback.xml` — logging

## Required environment (widgets)

`STEAM_API_KEY`, `STEAM_ID`, `UPTIME_KUMA_STATUS_SLUG` — when unset, those widgets respond with a non-fatal "not configured" fragment so the rest of the app keeps working. `LETTERBOXD_USERNAME` defaults to `michaellamb`. `UPTIME_KUMA_BASE_URL` defaults to `https://status.michaellamb.dev`.

## Deploy

Image: `ghcr.io/michaellambgelo/kotlin-tutorial:latest` (multi-arch, built by `.github/workflows/build-and-push.yml`).
Host: `node5`. Public URL: `https://kotlin-tutorial.michaellamb.dev`.
Playbook: `~/Workspace/cluster-ops/playbooks/update-kotlin-tutorial.yml`.

## Conventions

- Every tour route file has a header comment explaining the language feature it demonstrates.
- Routes are mounted via `Route.xRoutes()` extension functions, not classes — idiomatic Ktor.
- No persistence layer. If/when adding one, prefer Exposed (JetBrains) over JPA — pick the choice that teaches more Kotlin.
