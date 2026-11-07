# Changelog

All notable changes to this project are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
