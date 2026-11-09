# Changelog

All notable changes to this project are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Operational hardening pass — no change to the scientific models or API contracts.

### Added
- `GET /api/v1/ready` readiness probe that verifies the artifact volume is writable; the
  Docker `HEALTHCHECK` now uses it, while `/api/v1/health` stays I/O-free (liveness).
- Every HTTP response carries an `X-Request-ID` (client-supplied IDs are accepted only when
  they match a safe charset) and the server logs one line per request with status and latency.
- Deep-processing queue cap (`MAX_ACTIVE_JOBS = 4`): excess submissions get HTTP 429 with
  `Retry-After` instead of queueing unbounded RAM-heavy jobs.
- Catalog responses now report the true `total_matched` via OData `$count=true`.
- CI gates: Android `:app:lintDebug` (with `abortOnError = true`) and backend `ruff` E9/F.
- Selftest runs are capped at two concurrent executions (429 + `Retry-After`) and their
  artifact directories are pruned to the ten newest, so diagnostics cannot fill the volume.
- CORS preflights may send `X-Request-ID`; the deep executor shuts down with the app.

### Changed
- Copernicus catalog calls retry transient 429/5xx responses with capped exponential
  backoff and honour a small `Retry-After`; malformed JSON becomes a clean 502.
- Container image runs as unprivileged uid 10001 and owns only its artifact directory.
- Backend dependencies are pinned with SHA-256 hashes in `requirements.lock` and
  `requirements-dev.lock` (universal markers for Python 3.10+, uv-generated); Docker and CI
  install those locks, and `requirements.txt` is now a shim for PaaS hosts. Builds no longer
  drift with new upstream releases.
- The PEP 517 build backend is pinned too (`setuptools==84.2.1` in `pyproject.toml` and
  hash-locked in `requirements-build.lock`); Docker and CI build the wheel with
  `--no-build-isolation` and a fixed `SOURCE_DATE_EPOCH`, producing byte-identical wheels.

### Fixed
- Android: `busy`/`terrainBusy` are set before the coroutine starts (double-tap race), and a
  late response is discarded when the sketch changed while the request was in flight.
- Android: HTTP logging is disabled in release builds so request URLs never reach logcat.
- Deep `/analyze` now applies the same strict geometry validation as the catalog and terrain
  endpoints and normalises closed rings before they reach the Process API.
- A failed CDSE pixel fetch no longer sleeps after its final attempt (4–12 s saved per date).
- Terrain analysis refuses to report statistics when more than half of the DEM grid is missing.
- The Android Retrofit cache is a small LRU instead of an unbounded map.

## [1.4.0] - 2026-11-08

Exploration-flow and identity release. **Presentation only** — backend, API contracts, geo maths
and the analysis pipeline are untouched.

### Fixed
- **Release build broken on `main`** (introduced with the v1.3.0 UI work): `BRAND`/`BRAND_LIGHT`
  were declared `const val` but initialised with `.toInt()`, which is not a compile-time constant
  in Kotlin. They are now `val`s, and `AndroidManifest`-referenced `@mipmap/ic_launcher_round`
  exists at every density.

### Added — Google-Earth-style exploration
- The home screen now opens on the **globe** and behaves like a maps app: free one-finger pan,
  pinch zoom, a "back to globe" and "recenter/fit" control, and no accidental vertex drops.
- **Lock → trace → collect**: a prominent **Lock** button freezes the camera, and each tap then
  places a corner of the exploration boundary. The boundary is drawn as a **red and black dashed
  ring** (`lineDasharray`) over a faint gold fill, with gold vertex handles, a blue centroid
  marker and a pulsing crosshair reticle while tracing.
- Live area/perimeter read-out while tracing, plus a step-dot progress indicator (3–10 points).
- A full-screen **collection overlay** ("Collecting data for your area") with staged progress
  rows for validation → satellite catalog → terrain indicators.
- One run now gathers the complete picture: when the Sentinel-2 catalog for the locked area
  arrives, the terrain indicators are fetched automatically (UI orchestration only).

### Added — brand identity from the logo
- New launcher icon generated from the supplied artwork at every density, plus adaptive
  foreground/background (near-black plate), round variants and a monochrome themed-icon glyph.
- Branded launch window built from the logo; the app **defaults to the dark theme** because the
  logo is luminous artwork on black (Light and System remain selectable in Settings).
- Full palette rebuilt around the logo: obsidian neutrals, **molten gold** primary, **electric
  blue** secondary and **ember orange** alerts, in both light and dark schemes.
- The logo now appears in the map header plate and in Settings → About.

### Changed
- Every screen was re-cut against the new palette; removed unused colour/style tokens.
- Copy for the new flow added and translated in English + فارسی (no dead string keys remain).

## [1.3.0] - 2026-11-07

Calibration & honesty release, driven by an independent scientific review of v1.2.0.

### Fixed
- **CRITICAL live-mode bug**: the Process API evalscript requested reflectance bands without
  `units: "DN"` — the API served REFLECTANCE floats (0..1) that truncate to 0/1 under the
  UINT16 sample type, so every pixel failed the (DN−1000)/10⁴ harmonisation and live analysis
  produced garbage. The script now groups B02..B12 under `units: "DN"` (SCL/dataMask stay
  unit-less) — guarded by a regression test.
- API-key comparison now uses `hmac.compare_digest` (constant-time; no byte-by-byte key
  recovery via response timing).
- CORS is configurable via `CORS_ORIGINS` (comma-separated) and restricts methods/headers
  to what the API actually uses; the default stays `*` so the Android app and `/deep-console`
  work unchanged.

### Added
- **CPF-lite fusion (default `fusion_mode="cpf"`)** — calibrated anomaly statistics replacing
  the raw hand-weighted score as the detection criterion:
  1. Tukey-biweight IRLS degree-3 polynomial **context detrending** per evidence channel
     (smooth phenology/soil/illumination trends are removed before anything is "anomalous");
  2. robust median/MAD standardisation + trimmed shrinkage-covariance **whitening** →
     Mahalanobis d² over the six signed channels (correlated evidence is not double-counted);
  3. Efron-style **empirical null scaling** of d² to a chi²₆ reference;
  4. **phase-randomised surrogates** (shared phases across channels, default 199) calibrate a
     cluster-mass null; detections get **add-1 conformal p-values** and are reported only at
     p ≤ alpha (default 0.01). Confidence classes: high (p≤0.001) / medium (p≤0.01) / low.
  Measured on the null/drift injection suite (`tests/test_null_injection.py`): under strong
  seasonal drift with NO anomaly the legacy detector flags ~69 % of a region while CPF keeps
  false-positive area at ~3 %; with a planted anomaly CPF retains recall 1.00 at ~7.5 % FP
  (legacy: 72 % FP). Legacy fusion stays available via `fusion_mode="legacy"`.
- **Native-resolution tiling**: `resolution_m` (10..100 m) analyses the AOI on its native
  Sentinel-2 grid instead of resampling to ≤512 px. The area is split into ≤512-px space tiles
  with a 24-px overlap halo, each tile runs the full chain, maps are stitched and components
  labeled globally. Budget: ≤4.2 M px (≈20×20 km at 10 m) with a clear error message beyond.
  Requests: `processing.resampling` is set (bilinear in native mode, nearest in grid mode).
- **Synthetic flags everywhere in selftest mode**: report (`synthetic: true`), every
  detections.geojson feature (`properties.synthetic`), FeatureCollection metadata and the API
  response — a planted-ellipse detection can no longer be mistaken for real-terrain evidence.
- Detection properties now include `bbox_wgs84`, `elongation_ratio` (line-like detections such
  as shaft rows are no longer hidden by the convex-hull blob) and, in CPF mode, `p_value` +
  `method`. report.json gains `fusion_mode`, `cpf` (calibration summary), `native_resolution`,
  `resolution_m`, `tiles`, `tiles_ok/failed`.
- `n_surrogates`, `alpha`, `fusion_mode`, `resolution_m` request fields; selftest endpoint
  accepts `resolution_m`/`fusion_mode`/`n_surrogates` query parameters; capabilities endpoint
  advertises fusion modes + native-resolution limits. `n_surrogates` is auto-raised to the
  add-1 conformal floor (1/alpha − 1) when it would otherwise make significance impossible.
- `/deep-console` gains fusion-mode and native-resolution controls and surfaces
  synthetic/native/tile info in the completion line.
- New test modules: `test_null_injection.py` (legacy-vs-CPF drift/injection contract),
  `test_cpf.py` (detrend/whiten/null-scale/plant/quiet unit tests), `test_tiling.py`
  (native grid caps, exact tile-core coverage, stitched selftest, legacy-through-tiles) and
  evalscript/payload regression tests — suite now **72 tests, all green**.

### Changed
- Version bump to 1.3.0 (app `versionCode` 4; backend 1.3.0; report schema
  `subsurf.deep.report/1.3.0`); `scipy>=1.11` added to backend dependencies.

## [1.3.1] - 2026-11-07

_See 1.4.0 below._


### Android — presentation overhaul (PR #11)

Purely visual/UX work on the Android client. Networking, ViewModel logic, geo maths, API
contracts and the FastAPI backend are untouched.

#### Added
- **Design system** (`ui/theme`, `ui/components`): full Material 3 colour schemes for light and
  dark (hand-tuned AA contrast), a dedicated type scale, a spacing/elevation token set, and
  reusable primitives — `SubsurfCard`, `CardHeader`, `Pill`, `MetricValue`/`MetricRow`,
  `MeterBar`/`MeterRow`, `InfoBanner`, `BulletLine`, `EmptyState`, `SegmentedPicker`.
- **Local icon set** (`ui/icons`): 20 hand-built 24dp glyphs on a single 2dp optical stroke.
  Keeps the APK small — no `material-icons-extended` dependency was added.
- **Theme choice**: System / Light / Dark, persisted locally (`SettingsStore.themeMode`).
- **Real cartography**: the key-less MapLibre *demo* style was replaced with OpenFreeMap
  vector basemaps (Positron in light, Dark in dark mode), so roads, water and land cover
  actually render and the map matches the app theme.
- **Live sketch feedback**: floating area/perimeter read-out, vertex progress pill and a
  step-dot indicator; the polygon fill, connecting line and vertex markers now have distinct
  layers with a soft halo, plus in-map zoom and "fit region" controls.
- **Results as a report**: validation hero, metric grid, animated surface-anomaly gauge with a
  transparent component breakdown, satellite products with cloud-quality pills, terrain metric
  cards, provenance/caveat sections and a proper empty state that links back to the map.
- **Settings**: connection section with inline URL validation, typed test-connection result
  banner (success/failure), appearance picker, data-source cards and an about/licence block.
- **Branding**: new adaptive launcher icon (light/dark/monochrome), round icon variant and a
  dependency-free branded launch window (`launch_screen` layer-list) instead of a white flash.

#### Changed
- Floating pill navigation bar with animated selection capsule (replaces the stock bar).
- Edge-to-edge layout handled through `WindowInsets.safeDrawing`; screens respect the status
  bar, navigation pill and display cut-outs (`shortEdges`).
- All user-facing copy revised and fully translated (English + فارسی); no hardcoded UI strings.

#### Performance
- Map GeoJSON source is only rewritten when the sketch geometry actually changes (previously
  every recomposition rebuilt the feature collection, which stuttered during animations).
- Sketch layers install on a freshly loaded style without redundant look-ups.
- Theme flips reload the basemap in place and replay the sketch, so the map never resets.
- Icon/animation work is allocation-light (precomputed vectors, `graphicsLayer` scaling), and
  the launcher icon gained a monochrome variant for themed-icon rendering.

## [1.2.0] - 2026-11-07

### Added
- **Deep temporal-processing pipeline** (`backend/subsurface_api/deep/`, `/api/v1/deep/...`):
  - Copernicus Data Space **Process API** client — per-date 8-band UINT16 stacks
    (B02/B03/B04/B08/B11/B12/SCL/dataMask) with OAuth (password grant or client credentials),
    retry/back-off, and small bbox-only transfers (no full-scene downloads).
  - Preprocessing: baseline-4.0 reflectance harmonisation ((DN−1000)/10⁴) and SCL cloud/shadow/
    snow masking with a per-scene valid-fraction quality gate (≥ 55 % by default, ≥ 4 scenes).
  - Seven spectral indices: NDVI, NDWI, MNDWI, NDMI, NBR, BSI, albedo (NaN = invalid).
  - Robust temporal evidence: median/MAD z-scores, anomaly persistence over the recent window,
    Theil–Sen trends (per 30 days).
  - Unsupervised anomaly models: robust PCA (default) or a dependency-free numpy autoencoder
    (13→8→3→8→13, seeded, deterministic) → reconstruction-error percentile.
  - Transparent evidence fusion (0.25 moisture + 0.20 vegetation + 0.15 soil exposure +
    0.20 persistence + 0.20 deep model) with coherence and confidence classes.
  - Detections: 8-connected components (≥ 9 px), convex-hull GeoJSON polygons with geodesic area,
    mean/max score, persistence, coherence, confidence.
  - Artifacts per job: score/overlay/evidence PNGs, three EPSG:4326 GeoTIFFs (tifffile),
    detections.geojson, timeseries.json, full report.json.
  - Async job manager (single-worker executor, progress stages, artifact retention) and optional
    `X-API-Key` gate via `DEEP_API_KEY`.
  - `mode: selftest` — identical downstream chain on a deterministic synthetic region; verifies a
    deployment end-to-end without any Copernicus account (`POST /api/v1/deep/selftest`).
  - Browser console at `/deep-console` (MapLibre bbox picker → analyze → artifacts).
- `docs/DEEP-PROCESSING.md` — full mathematical specification + honest limitations.
- `docs/DEPLOY-RUNFLARE-FA.md` — complete Persian Runflare deployment guide (CLI, GitHub oAuth/
  classic, port mapping, Secrets, disk, post-deploy tests).
- `backend/requirements.txt` for PaaS/Docker builds expecting a plain requirements file.
- 20 new backend tests (indices math, robust statistics, labeling, GeoTIFF roundtrip, API selftest,
  artifact serving, credential gate) — suite now 50 tests, all green.

### Changed
- Version bump to 1.2.0 (app `versionCode` 3, backend package 1.2.0); `tifffile` dependency added.

## [1.1.0] - 2026-11-07

### Added
- Gradle wrapper fully committed (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`) — the CI workflow
  and local Termux builds now use `./gradlew` directly; no pre-installed Gradle needed anywhere.
- `:app:testDebugUnitTest` added to the APK workflow — GeoMath unit tests run on every CI build.
- Backend `Dockerfile` (+ `.dockerignore`) honouring `$PORT` with a container health check —
  deployable to RunFlare (Docker hosting), Hugging Face Spaces, or any Docker host.
- `scripts/termux-sign.sh` — one-shot Termux script: installs `openjdk-17`/`gh`, generates the
  RSA-4096 keystore, base64-encodes it and registers all four GitHub Secrets automatically.
- `scripts/upload-to-github.sh` — Termux-friendly `git` push that preserves the hidden
  `.github/workflows` folder (which the GitHub web uploader drops).
- `docs/QUICKSTART-FA.md` — complete Persian quick-start (zip → repo → APK → backend incl. RunFlare).
- `github-workflow-backup/` — visible copies of both workflow files with web-uploader recovery
  instructions (Persian + English).
- RunFlare free-tier deployment option documented in `docs/OPERATIONS.md` (option E).

### Changed
- Version bump to 1.1.0 (app `versionCode` 2, backend package 1.1.0).
- APK workflow: `gradle/actions/setup-gradle@v4` now used for dependency caching only; the build
  itself runs through the committed wrapper.

## [1.0.0] - 2026-11-07

### Added
- Native Android app: Kotlin 2.2, Jetpack Compose (Material 3), MapLibre Native 11 interactive map.
- Region sketching: tap to add 3–10 vertices, undo, clear, live geodesic area estimate.
- Local (offline) polygon validation fallback inside the app; server validation on top.
- FastAPI backend: strict polygon validation (geometry, vertex count, area bounds, self-intersection).
- Live Sentinel-2 L2A catalog metadata search via Copernicus Data Space Ecosystem OData API
  (anonymous, no API key), with cloud-cover filter and full provenance notes.
- Real terrain analysis from AWS Terrain Tiles (Terrarium DEM): elevation statistics, slope (Horn),
  roughness (TRI), gradient-orientation consistency, nodata handling, tile-count limits.
- Documented, weighted surface terrain-anomaly score with explicit scientific caveats
  (surface indicator only — not a buried-target probability).
- Android Settings screen: runtime API base URL override + connection health check.
- GitHub Actions workflows: automatic APK build (debug + release; signed when secrets present,
  unsigned otherwise; artifacts + tag releases) and backend test suite.
- Gradle release signing driven by environment variables (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
  `KEY_ALIAS`, `KEY_PASSWORD`) — no keystore ever committed.
- Documentation: architecture, science scope, operations (data collection & registration),
  Termux signing guide (فارسی/English), roadmap.
- Persian (فارسی) UI localization; RTL support.
