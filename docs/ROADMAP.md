# Roadmap

Staged, evidence-first implementation plan. Nothing here promises buried-target
detection; each milestone adds one defensible capability with provenance.

## M0 — Bootstrap (✅ shipped in 1.0.0)

- [x] Android app: map, region sketching (3–10 vertices), area estimate, offline validation
- [x] Backend: strict polygon validation
- [x] Sentinel-2 L2A catalog metadata search (anonymous, Copernicus OData)
- [x] Open-DEM terrain indicators + transparent surface-anomaly score
- [x] CI: automatic APK builds (signed via secrets) + backend tests
- [x] Scientific guardrails, provenance everywhere, bilingual docs (EN/فا)

## M1 — Optical surface indicators (next)

- [ ] Optional Copernicus (free) account: authenticated asset download of S2 L2A bands
      (10/20 m) for a selected scene
- [ ] Band math on the backend: NDVI / NDWI / BSI / NDMI mosaics clipped to the region
- [ ] Multi-temporal medians (e.g. 3 dates) to suppress single-scene artefacts
- [ ] Indicator: vegetation/soil-moisture anomaly layers, aligned on the common grid
- [ ] Provenance: scene ID, processing baseline, tile, per-band resolution, licence

## M2 — Radar & thermal context

- [ ] Sentinel-1 GRD (VV/VH) metadata + availability layer
- [ ] Thermal (L2A band B10 successor / LST products) where available
- [ ] Cross-sensor alignment report (resolution mismatch table per region)

## M3 — Analysis hardening

- [ ] Persisted "survey projects" (local DB: regions, chosen scenes, notes, results)
- [ ] Export: GeoJSON of region + JSON report with full provenance chain
- [ ] Time-series view of indicators per region
- [ ] Optional read-only sharing link (self-hosted backend)

## M4 — Field-truth bridge

- [ ] Manual entry of ground observations (method, date, operator, result)
- [ ] Side-by-side comparison: surface indicators vs field results (honest scatter, no
      model fitting until data volume justifies it)
- [ ] Clear separation: "prioritisation tool" — never an autonomous "detector"

## Non-goals

- Depth estimation, target classification, "treasure maps", or any claim that satellite
  data can identify buried objects. These are physically unsupported without site-specific
  physical models and ground truth (see SCIENCE-SCOPE.md).
