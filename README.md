```text
             .,.................;,
           ,,.                .:.
          ,.   ,,..........,:,,
        .,    .:.                      .,.:,
        ,.          . ....,,          ,, ,,
         ,,...........    .: :,,,.., .. ,. .:,,,,.:....,...:
                   .:,   ,:..:,,:  ,,. ,  .,,,,:. ,:. ,,,,,
     ................   ,,,..,,, .,,. :.....,,:  ,,  :.
   ,,.                .,.,. ,,,..::,...:;, .,,..,:..:
 .,.  ...,...,,,,,,,,,,. :,,,,,,..,.,.,..,,........,
.;::;:::.  .,,,,,,,,,...,....,;.
         .,.                .:.
        ,,   .,..........,::,       ....          ..
       ,.    ,,........,  .        .,.:.        .:.:.
       ,,      .       .;. ,,,.,, .: ,,,,, .,.:.,:;..,,,.,,
        ,,........:.   .:.::,:: ,;, :.,, ;,,.,;, ,, ::::: :,
     .............,   ,:,,,,:, .;, :. ,. :,.,:, ,,,,,,:, .,
   ,,....            ::,. :::  ;:  ,;.,,  ,,,, ,;,. :,: .:
 ,,.   ............,, .,.,.,,.,,.,.,,  :.,. :.,,,,...,,.,,
;+::;;:,..........
```

# SubsurfaceExplorer (Sal-Subsurf)

**An evidence-aware geospatial workspace for exploring satellite and terrain observations.**

SubsurfaceExplorer is an Android-first project for selecting survey areas, organizing geospatial
observations, and analysing verified satellite/terrain products. It is **not** a remote subsurface
scanner. Satellite imagery does not directly identify buried metal objects, tunnels, cavities, or
their depth. The project therefore focuses on mapping, region geometry, data provenance, and
defensible surface indicators.

## Status

* Native Android app (Kotlin, Jetpack Compose, Material 3, MapLibre Native 11).
* Region sketching on an interactive map (3–10 vertices, undo, clear, live area estimate).
* FastAPI backend with strict polygon validation and live Sentinel-2 L2A catalog metadata search
  (Copernicus Data Space Ecosystem, anonymous access, no API key).
* **Deep temporal processing (v1.3.0)** — real Sentinel-2 L2A pixel analysis via the Copernicus
  Process API: per-date 8-band stacks, SCL cloud masking, 7 spectral indices, robust multi-date
  statistics (median/MAD z-scores, persistence, Theil–Sen trend), an unsupervised anomaly model
  (robust PCA or numpy autoencoder), transparent evidence fusion (0–100), connected-component
  detections (GeoJSON + GeoTIFF + PNG overlay) and a browser console (`/deep-console`).
  Full spec: [docs/DEEP-PROCESSING.md](docs/DEEP-PROCESSING.md).
* Real terrain analysis from open global DEM tiles (AWS Terrain Tiles / Terrarium — no account):
  elevation statistics, slope, roughness (TRI), gradient-orientation consistency, and a documented,
  weighted **surface terrain-anomaly score** with full provenance and caveats.
* Android-to-API requests with loading, success, and error states; local offline validation fallback.
* Python unit tests and CI workflows.
* Scientific guardrails and a staged implementation roadmap.

Only Copernicus catalog **metadata** search, open DEM analysis, and the deep *surface-change*
pipeline are connected. Satellite full-scene downloads, user accounts, and buried-target
interpretation are **not** implemented, by design. The UI never fabricates results.

## Repository layout

```
android/       Android application (Kotlin + Compose + MapLibre)
backend/       Python geospatial API (FastAPI)
docs/          Architecture, science scope, operations, signing, roadmap
.github/       CI/CD: APK build workflow + backend tests
```

## Build the APK (GitHub Actions — fully automatic)

1. Create a new **empty** GitHub repository (public recommended — Actions are free and unlimited).
2. Upload the contents of this folder to the repository — **push with `git`** (recommended, keeps
   the hidden `.github/` workflow folder):
   ```bash
   bash scripts/upload-to-github.sh https://github.com/USER/REPO.git
   ```
   The GitHub *web* uploader drops dot-folders; if you use it anyway, recreate the workflows from
   `github-workflow-backup/` (see its README). Persian walkthrough: `docs/QUICKSTART-FA.md`.
3. GitHub Actions (`.github/workflows/android.yml`) runs automatically and builds:
   * `Sal-Subsurf-debug.apk` — always
   * `Sal-Subsurf-release-signed.apk` — **if you added signing secrets**, otherwise
     `Sal-Subsurf-release-unsigned.apk`
   * It also runs the app's unit tests on every build.
4. Download the artifacts from the run page (*Actions → latest run → Artifacts*), or push a tag
   `v1.3.0` to get the APKs attached to a GitHub Release.

### Sign releases with your own key (Termux)

No external service is required. Either run the one-shot helper (installs tools, generates the
keystore, registers all four secrets via `gh`):

```bash
bash scripts/termux-sign.sh
```

…or follow the manual step-by-step guide:
[docs/SIGNING-TERMUX.md](docs/SIGNING-TERMUX.md) (فارسی + English).

| Secret | Meaning |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 of your `.keystore` file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password (for PKCS12 = keystore password) |

## Run the backend

```bash
cd backend
python -m venv .venv
# Activate the environment, then:
python -m pip install -e ".[dev]"
uvicorn subsurface_api.main:app --reload
```

API documentation: `http://127.0.0.1:8000/docs`.

Operations: `GET /api/v1/health` is a dependency-free liveness probe, `GET /api/v1/ready`
returns `503` unless the artifact directory is writable (used by the container health check),
and every response includes an `X-Request-ID` that also appears in the server log line.

## Open the Android app

Open `android/` in Android Studio with JDK 17+, Android SDK Platform 36, and Build Tools 36.0.0.
The Gradle build uses Kotlin DSL and a version catalog. The **complete Gradle wrapper is committed**
(`gradlew` + JAR, Gradle 8.14.6), so both CI and local builds (`./gradlew :app:assembleDebug`)
work without installing anything — the wrapper downloads Gradle itself.

### Connect the Android app to the backend

Start the API on your development computer
(`uvicorn subsurface_api.main:app --host 0.0.0.0 --port 8000`). The Android emulator uses
`http://10.0.2.2:8000/` by default. For a physical phone, open **Settings** inside the app and set
the API base URL to your computer's LAN address, e.g. `http://192.168.1.20:8000/`. You can also
pass it at build time:

```bash
gradle -PAPI_BASE_URL=http://192.168.1.20:8000/ :app:assembleDebug
```

The runtime value in Settings always wins over the build-time default, so you never need to rebuild
just to change the endpoint. Practical deployment options (LAN, ADB reverse, Termux on the same
phone, **Runflare**, VPS + HTTPS) are described in [docs/OPERATIONS.md](docs/OPERATIONS.md),
[docs/DEPLOY-RUNFLARE-FA.md](docs/DEPLOY-RUNFLARE-FA.md) (راهنمای کامل فارسی رانفلر) and
[docs/QUICKSTART-FA.md](docs/QUICKSTART-FA.md) (فارسی).

### Deep processing API (v1.3.0)

```bash
# offline end-to-end validation (no Copernicus account needed)
curl -X POST "http://127.0.0.1:8000/api/v1/deep/selftest?grid=160"
# native 10 m tiling selftest:  /api/v1/deep/selftest?resolution_m=10

# real analysis — needs CDSE credentials in the environment (see docs/DEEP-PROCESSING.md)
curl -X POST http://127.0.0.1:8000/api/v1/deep/analyze -H 'Content-Type: application/json' \
  -d '{"polygon":[[52.50,29.55],[52.70,29.55],[52.70,29.70],[52.50,29.70]],
       "date_from":"2026-06-01","date_to":"2026-11-07","grid":512,"mode":"live"}'
```

Browser console: `http://127.0.0.1:8000/deep-console`. Full endpoint list and the exact math:
[docs/DEEP-PROCESSING.md](docs/DEEP-PROCESSING.md).

## Scientific guardrails

1. A surface anomaly is not evidence of a buried target.
2. Optical, thermal, SAR, and elevation datasets have different spatial/temporal resolutions,
   processing artefacts, and license terms.
3. No depth estimate or object-type classification is emitted without a validated physical model
   and site-specific ground truth.
4. Every observation retains provider, product ID, acquisition time, processing version, spatial
   resolution, quality flags, and licence/provenance metadata.
5. Field confirmation needs suitable ground-based methods (e.g. EMI, magnetometry, or GPR), chosen
   for the target and geology.

See [docs/SCIENCE-SCOPE.md](docs/SCIENCE-SCOPE.md) (indicator definitions and scoring),
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/OPERATIONS.md](docs/OPERATIONS.md) (data
collection & registration), and [docs/ROADMAP.md](docs/ROADMAP.md).

## Licensing

Application/backend source: MIT. Documentation: CC BY 4.0.
See `LICENSE` and `docs/LICENSE-DOCS.md`.
