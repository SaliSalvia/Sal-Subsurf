# Deep temporal processing (`/api/v1/deep`)

Version 1.2.0 adds the **deep-processing pipeline**: real multi-temporal pixel
analysis of Sentinel-2 L2A data — not just catalog metadata. This document is
the exact specification of every number the pipeline emits. `docs/SCIENCE-SCOPE.md`
still applies verbatim: outputs are *surface-change indicators*, never
buried-target probabilities, never depths.

## 1. Pipeline overview

```
┌ acquire ────────┐  ┌ preprocess ────┐  ┌ indices ─────────────────────┐
│ CDSE Process API│→ │ reflectance    │→ │ NDVI NDWI MNDWI NDMI         │
│ 8-band stacks   │  │ (DN−1000)/10⁴  │  │ NBR BSI albedo               │
│ per date, 10 m  │  │ SCL cloud mask │  │ (NaN = invalid pixel)        │
└─────────────────┘  └────────────────┘  └──────────────┬───────────────┘
                                                        ↓
┌ outputs ─────────────────────────┐  ┌ fusion ─────────────────────────────┐
│ GeoTIFF (EPSG:4326) · PNG        │← │ weighted evidence 0–100 + coherence │
│ overlay · GeoJSON · report.json  │  │ + confidence classes                │
└──────────────────────────────────┘  └──────────────┬──────────────────────┘
                                     ┌ model ────────┴──────────────────────┐
                                     │ unsupervised: robust PCA (default)   │
                                     │ or numpy autoencoder (8→3→8)         │
                                     │ reconstruction-error percentile      │
                                     └──────────────┬───────────────────────┘
                                     ┌ temporal ────┴───────────────────────┐
                                     │ median baseline · MAD z-scores       │
                                     │ persistence · Theil–Sen trend        │
                                     └──────────────────────────────────────┘
```

## 2. Acquisition

* Endpoint: `https://sh.dataspace.copernicus.eu/api/v1/process` (sentinel-2-l2a).
* Auth: CDSE token — either personal account (`grant_type=password`,
  `client_id=cdse-public`) or an OAuth client (`client_credentials`).
  Environment variables (never logged):
  * `COPERNICUS_USERNAME` + `COPERNICUS_PASSWORD`, **or**
  * `COPERNICUS_CLIENT_ID` + `COPERNICUS_CLIENT_SECRET`.
* Evalscript returns one UINT16 stack per date: `B02 B03 B04 B08 B11 B12 SCL dataMask`.
* A 429/5xx is retried 3× with linear back-off; the free-tier rate limits are
  respected by daily stepping and the `max_dates` cap (≤ 24 accepted scenes).
* Only the requested bbox is transferred — tens of KB per date, never full scenes.

## 3. Preprocessing

* Reflectance = `(DN − 1000) / 10000` (processing-baseline 4.0 harmonisation
  offset; override with `S2_DN_OFFSET` if Copernicus ever changes it).
* Valid surface pixels: SCL ∈ {2 dark, 4 vegetation, 5 bare, 6 water,
  7 unclassified} **and** `dataMask > 0`. Clouds (8/9), cirrus (10), shadows (3),
  snow (11), nodata (0/1) are excluded — clouds must never read as ground change.
* Scene quality gate: a scene is used only if `valid_fraction ≥ min_valid_fraction`
  (default 0.55). At least 4 usable scenes are required.

## 4. Spectral indices (exact formulas)

| Index | Formula | Meaning |
| --- | --- | --- |
| NDVI | (B08−B04)/(B08+B04) | greenness / vegetation vigour |
| NDWI | (B03−B08)/(B03+B08) | open water (McFeeters) |
| MNDWI | (B03−B11)/(B03+B11) | moist land / wet surfaces (Xu) |
| NDMI | (B08−B11)/(B08+B11) | canopy & soil moisture |
| NBR | (B08−B12)/(B08+B12) | drying / disturbance proxy |
| BSI | ((B11+B04)−(B08+B02)) / ((B11+B04)+(B08+B02)) | bare-soil exposure |
| Albedo | mean(B02,B03,B04,B08) | broadband brightening |

## 5. Temporal evidence (robust statistics)

For every index:

* **Baseline** = pixel-wise *median* over all accepted scenes before the recent
  window; **dispersion** = MAD × 1.4826 (+10⁻⁴ floor):
  `z = (recent_median − baseline_median) / (1.4826·MAD + 10⁻⁴)`.
* **Persistence** = fraction of the last `recent_k` scenes in which the pixel's
  z crossed the threshold (default |z| ≥ 1.5) in the anomaly direction; the
  reported layer is the mean over {NDMI, NDVI, BSI, NBR} — indices whose
  *deficit/exposure* direction is physically coherent.
* **Trend** = Theil–Sen slope (median of pairwise slopes, ≤ 36 pairs) over the
  accepted window, reported per 30 days, clipped to ±0.1.

The recent window is the last `recent_k` (default 4) scenes; the baseline is
everything up to and including its first scene.

## 6. Anomaly model (unsupervised, deterministic)

Feature vector per pixel (13 dims): NDVI NDWI MNDWI NDMI NBR BSI albedo,
z(NDMI) z(NDVI) z(BSI), persistence, trend(NDMI), trend(NDVI).
Robust-standardised (median / IQR); invalid pixels excluded and scored 0.

* `pca` (default): SVD, components kept for ≥ 90 % variance, per-pixel
  reconstruction error → percentile rank in [0, 1].
* `autoencoder`: numpy tanh MLP 13→8→3→8→13, SGD lr 0.02, ≤ 300 epochs,
  batch 1024, early stop 10⁻⁷, seeded (42), trained on ≤ 40 000 valid pixels.
  Reconstruction error → percentile rank.

The output is always "how spectrally *surprising* is this pixel relative to the
region" — a relative measure, never an absolute physical quantity.

## 7. Evidence fusion (published, hand-recomputable)

```
score/100 = 0.25 · min(−z(NDMI)/3, 1)        (moisture deficit)
          + 0.20 · min(−z(NDVI)/3, 1)        (vegetation stress)
          + 0.15 · min( z(BSI)/3, 1)         (soil exposure)
          + 0.20 · persistence
          + 0.20 · deep-model percentile
```

* **Coherence** = share of the five layers ≥ 0.5 (agreement 0–1).
* **Pixel confidence classes**: high = score ≥ 65 ∧ coherence ≥ 0.6 ∧
  persistence ≥ 0.5; medium = score ≥ 50 ∧ coherence ≥ 0.4; low = score ≥ 35.

## 8. Detections (connected components)

* Mask: fused score ≥ `score_threshold` (default 55/100) ∧ valid pixel.
* 8-connected labelling (BFS); regions < `min_pixels` (default 9 px) dropped;
  at 10 m GSD that is a ~900 m² minimum footprint.
* Each region → convex-hull polygon (pixel centres → WGS84) with properties:
  area (geodesic m²), mean/max score, coherence, persistence, confidence,
  centroid. Top 50 components by size are reported.

## 9. Artifacts (per job, under `DEEP_DATA_DIR/<job_id>/`)

| File | Content |
| --- | --- |
| `score_map.png` | fused score, fixed colour ramp |
| `overlay.png` | latest scene natural colour + anomaly overlay |
| `layer_*.png` | the five evidence layers |
| `score.tif` / `persistence.tif` / `deep_anomaly.tif` | GeoTIFF float32, EPSG:4326 |
| `detections.geojson` | component polygons + properties |
| `timeseries.json` | AOI-median of every index per date (charts) |
| `report.json` | full parameters, scenes used, weights, caveats, provenance |

## 10. API

| Endpoint | Purpose |
| --- | --- |
| `GET /api/v1/deep/capabilities` | what this deployment supports right now |
| `POST /api/v1/deep/analyze` | start a job (`mode: live \| selftest`) |
| `GET /api/v1/deep/jobs` / `jobs/{id}` | queue status / progress / stage |
| `GET /api/v1/deep/jobs/{id}/result` | full report |
| `GET /api/v1/deep/jobs/{id}/files/{name}` | artifact download |
| `POST /api/v1/deep/selftest?grid=192` | synchronous offline validation |
| `GET /deep-console` | browser console (bbox → analyze → artifacts) |

If `DEEP_API_KEY` is set in the environment, all deep endpoints require the
header `X-API-Key`.

## 11. Selftest mode

`mode: "selftest"` runs the *identical* downstream chain on a deterministic
synthetic stack (a fading "drying/stress" ellipse injected into 9 scenes).
Use it to verify a deployment without any Copernicus account. The report is
marked `"mode": "synthetic-selftest"` and is clearly labelled synthetic — it
must never be presented as a real-world result.

## 12. Honest limitations

1. Optical imagery sees the **surface**, never the subsurface; a "detection" is
   a coherent multi-date surface-change anomaly.
2. Agriculture (harvest, irrigation change), hydrology, geology, fire, and
   residual cloud/shadow artefacts can produce identical signatures.
3. The default grid (≤ 512 px) trades spatial detail for PaaS memory limits;
   the effective cell size is reported in every response.
4. Statistical evidence prioritises land for ground investigation; field
   confirmation (EMI / magnetometry / GPR, legally) is always required.
