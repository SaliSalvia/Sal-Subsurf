"""The deep-processing pipeline: acquisition → evidence → fusion → artifacts.

Live mode downloads small per-date band stacks from the Copernicus Process API
(credentials required, see cdse_process.py). Selftest mode runs the *identical*
downstream chain on a deterministic synthetic stack, so a deployment can be
verified end-to-end without any Copernicus account.

Every artifact is written into ``DEEP_DATA_DIR/<job_id>/`` (default
``./deep_jobs/<job_id>/``) and summarised in a machine-readable report.json.
"""

from __future__ import annotations

import logging
import os
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Callable, Dict, List, Optional, Sequence

import numpy as np

from . import cdse_process, raster
from . import temporal as temporal_mod
from .fusion import WEIGHTS, fuse
from .labels import detections_to_geojson, label_components
from .model import deep_anomaly
from .outputs import (
    job_dir,
    write_geotiff,
    write_json,
    write_layer_png,
    write_overlay_png,
    write_score_png,
)

log = logging.getLogger("subsurface_api.deep.pipeline")

ProgressCb = Optional[Callable[[int, str], None]]

ARTIFACTS = [
    "score_map.png",
    "overlay.png",
    "layer_moisture.png",
    "layer_vegetation.png",
    "layer_soil.png",
    "layer_persistence.png",
    "layer_deep_model.png",
    "score.tif",
    "persistence.tif",
    "deep_anomaly.tif",
    "detections.geojson",
    "timeseries.json",
    "report.json",
]

CAVEATS = [
    "Outputs are multi-temporal SURFACE-CHANGE indicators only — never a "
    "probability of a buried target, never a depth estimate (docs/SCIENCE-SCOPE.md).",
    "Optical Sentinel-2 cannot see underground; signatures may also be produced "
    "by ordinary agronomy, hydrology, geology or residual cloud/shadow artefacts.",
    "Any field follow-up must use ground-based geophysics within local law.",
]


@dataclass
class DeepParams:
    polygon: List[List[float]]
    date_from: str
    date_to: str
    grid: int = 512                     # 128 | 256 | 512
    max_cloud_percent: float = 20.0
    max_dates: int = 14                 # accepted scenes target (4..24)
    min_valid_fraction: float = 0.55    # per-scene quality gate
    recent_k: int = 4                   # "recent" window length
    z_threshold: float = 1.5            # persistence threshold
    score_threshold: float = 55.0       # component cut on the 0-100 score
    min_pixels: int = 9
    model: str = "pca"                  # pca | autoencoder
    autoencoder_epochs: int = 300

    def sanitized(self) -> "DeepParams":
        p = DeepParams(**vars(self))
        p.grid = int(min(512, max(64, p.grid)))
        p.max_dates = int(min(24, max(4, p.max_dates)))
        p.recent_k = int(min(max(2, p.recent_k), p.max_dates - 1))
        p.min_valid_fraction = float(min(0.95, max(0.30, p.min_valid_fraction)))
        p.max_cloud_percent = float(min(100.0, max(0.0, p.max_cloud_percent)))
        p.score_threshold = float(min(90.0, max(25.0, p.score_threshold)))
        p.min_pixels = int(max(1, p.min_pixels))
        p.model = "autoencoder" if p.model == "autoencoder" else "pca"
        p.autoencoder_epochs = int(min(1200, max(50, p.autoencoder_epochs)))
        return p


@dataclass
class SceneInfo:
    date: str
    valid_fraction: float
    indices: Dict[str, np.ndarray] = field(default_factory=dict)
    composite: Optional[np.ndarray] = None


def data_dir() -> str:
    return os.environ.get("DEEP_DATA_DIR", os.path.join(os.getcwd(), "deep_jobs"))


def _cell_size_m(bbox_wsen: Sequence[float], width: int, height: int) -> float:
    west, south, east, north = bbox_wsen
    lat_mid = (north + south) / 2.0
    lon_len_m = (east - west) * 111320.0 * max(0.05, np.cos(np.radians(lat_mid)))
    lat_len_m = (north - south) * 110574.0
    return float(((lon_len_m / width) + (lat_len_m / height)) / 2.0)


def _bbox_wsen(ring: Sequence[Sequence[float]]) -> List[float]:
    lons = [float(p[0]) for p in ring]
    lats = [float(p[1]) for p in ring]
    return [min(lons), min(lats), max(lons), max(lats)]


def _report(p: DeepParams, mode: str, bbox_wsen: Sequence[float], scenes, extra: Dict) -> Dict:
    from . import DEEP_VERSION

    now = datetime.now(timezone.utc).isoformat(timespec="seconds")
    return {
        "schema": "subsurf.deep.report/1.2.0",
        "mode": mode,
        "generated_utc": now,
        "deep_version": DEEP_VERSION,
        "bbox_wgs84": list(bbox_wsen),
        "parameters": {
            "grid": p.grid,
            "max_cloud_percent": p.max_cloud_percent,
            "max_dates": p.max_dates,
            "min_valid_fraction": p.min_valid_fraction,
            "recent_k": p.recent_k,
            "z_threshold": p.z_threshold,
            "score_threshold": p.score_threshold,
            "min_pixels": p.min_pixels,
            "model": extra.get("model_used", p.model),
        },
        "scenes_used": scenes,
        "caveats": CAVEATS,
        **extra,
    }


def _finalize(
    p: DeepParams,
    mode: str,
    ring: Sequence[Sequence[float]],
    scenes: List[SceneInfo],
    progress: ProgressCb,
    out_dir: str,
) -> Dict:
    """Shared downstream chain: temporal → model → fusion → labels → artifacts."""
    bbox_wsen = _bbox_wsen(ring)
    width = height = p.grid
    cell_size_m = _cell_size_m(bbox_wsen, width, height)

    if progress:
        progress(55, "computing spectral indices + temporal statistics")
    dates, stacks = raster.stack_scenes(scenes)
    evidence = temporal_mod.temporal_evidence(dates, stacks, p.recent_k, p.z_threshold)

    if progress:
        progress(70, f"unsupervised anomaly model ({p.model})")
    deep_map, model_used = deep_anomaly(evidence, model=p.model, autoencoder_epochs=p.autoencoder_epochs)

    if progress:
        progress(80, "evidence fusion")
    fused = fuse(evidence, deep_map)
    score = fused["score_0_100"]
    coherence = fused["coherence"]
    persistence = fused["layers"]["persistence"]

    if progress:
        progress(88, "connected components + polygons")
    mask = (score >= p.score_threshold) & np.isfinite(evidence["z_ndmi"])
    labels, count = label_components(mask)
    features, summaries = detections_to_geojson(
        labels, count, score, coherence, persistence, bbox_wsen, cell_size_m,
        min_pixels=p.min_pixels,
    )

    if progress:
        progress(94, "writing artifacts")
    write_score_png(os.path.join(out_dir, "score_map.png"), score)
    composite = scenes[-1].composite
    if composite is None:
        composite = np.zeros((height, width, 3), np.float32)
    write_overlay_png(os.path.join(out_dir, "overlay.png"), composite, score)
    write_layer_png(os.path.join(out_dir, "layer_moisture.png"), fused["layers"]["moisture_deficit"])
    write_layer_png(os.path.join(out_dir, "layer_vegetation.png"), fused["layers"]["vegetation_stress"])
    write_layer_png(os.path.join(out_dir, "layer_soil.png"), fused["layers"]["soil_exposure"])
    write_layer_png(os.path.join(out_dir, "layer_persistence.png"), persistence)
    write_layer_png(os.path.join(out_dir, "layer_deep_model.png"), fused["layers"]["deep_model"])
    write_geotiff(os.path.join(out_dir, "score.tif"), score, bbox_wsen)
    write_geotiff(os.path.join(out_dir, "persistence.tif"), persistence, bbox_wsen)
    write_geotiff(os.path.join(out_dir, "deep_anomaly.tif"), deep_map, bbox_wsen)
    write_json(os.path.join(out_dir, "detections.geojson"),
               {"type": "FeatureCollection",
                "features": features})

    # AOI-median time series per index (chartable in the app/console).
    timeseries = {"dates": dates, "series": {}}
    for name in raster.INDICES:
        with np.errstate(all="ignore"):
            timeseries["series"][name] = [
                None if not np.isfinite(v) else round(float(v), 4)
                for v in np.nanmedian(stacks[name].reshape(len(dates), -1), axis=1)
            ]
    write_json(os.path.join(out_dir, "timeseries.json"), timeseries)

    scene_rows = [
        {"date": s.date, "valid_fraction": round(s.valid_fraction, 4)} for s in scenes
    ]
    high = [s for s in summaries if s["confidence"] in ("high", "medium")]
    report = _report(
        p, mode, bbox_wsen, scene_rows,
        {
            "model_used": model_used,
            "grid": p.grid,
            "grid_width_px": width,
            "grid_height_px": height,
            "cell_size_m": round(cell_size_m, 2),
            "coverage_m2": round(cell_size_m * width * height, 1),
            "scenes_accepted": len(scenes),
            "recent_window": dates[-p.recent_k:],
            "baseline_window": dates[: max(2, len(dates) - p.recent_k + 1)],
            "score_weights": dict(WEIGHTS),
            "components_detected": len(summaries),
            "components_high_or_medium": len(high),
            "detections": summaries,
            "artifacts": ARTIFACTS,
        },
    )
    write_json(os.path.join(out_dir, "report.json"), report)
    if progress:
        progress(100, "done")
    return report


def run_live(p: DeepParams, ring, progress: ProgressCb, out_dir: str) -> Dict:
    """Live acquisition + full analysis (CDSE credentials required)."""
    p = p.sanitized()
    if not cdse_process.credentials_present():
        raise cdse_process.DeepAuthError(
            "No Copernicus credentials configured (COPERNICUS_USERNAME/"
            "COPERNICUS_PASSWORD or COPERNICUS_CLIENT_ID/COPERNICUS_CLIENT_SECRET)."
        )

    if progress:
        progress(2, "authenticating with Copernicus Data Space")
    token = cdse_process.get_token()

    candidates = cdse_process.plan_dates(p.date_from, p.date_to, max_dates=min(60, p.max_dates * 4))
    if not candidates:
        raise ValueError("Empty date window after clamping to today (UTC).")

    scenes: List[SceneInfo] = []
    total = len(candidates)
    for i, date_iso in enumerate(candidates):
        if len(scenes) >= p.max_dates:
            break
        if progress:
            pct = 3 + int(40 * i / max(1, total))
            progress(pct, f"acquiring {date_iso} ({len(scenes)}/{p.max_dates} accepted)")
        try:
            stack = cdse_process.fetch_scene_stack(
                token, ring, date_iso, p.grid, p.grid, p.max_cloud_percent
            )
        except cdse_process.DeepFetchError as exc:
            log.warning("skip %s: %s", date_iso, exc)
            continue
        frac = raster.scene_valid_fraction(stack)
        if frac < p.min_valid_fraction:
            log.info("skip %s: valid fraction %.2f < %.2f", date_iso, frac, p.min_valid_fraction)
            continue
        refl = raster.to_reflectance(stack)
        valid = raster.valid_mask(stack)
        info = SceneInfo(date=date_iso, valid_fraction=frac)
        info.indices = raster.compute_indices(refl, valid)
        info.composite = raster.composite_rgb(refl)
        scenes.append(info)

    scenes.sort(key=lambda s: s.date)
    if len(scenes) < 4:
        raise ValueError(
            f"Only {len(scenes)} usable scene(s) in the window "
            f"(need >= 4). Widen the date range, raise max_cloud_percent, or "
            f"lower min_valid_fraction."
        )

    if progress:
        progress(50, "preprocessing complete")
    return _finalize(p, "live", ring, scenes, progress, out_dir)


# ------------------------------------------------------------------ selftest

def _synthetic_stack(p: DeepParams, scene_dates: List[str]):
    """Deterministic 8-band stacks with an injected drying/stress anomaly."""
    rng = np.random.default_rng(20261107)
    g = min(p.grid, 256)
    yy, xx = np.mgrid[0:g, 0:g].astype(np.float32)
    n_scenes = len(scene_dates)

    # Gentle natural texture (corrugation + noise) in the "vegetated" scene.
    texture = 0.5 + 0.5 * np.sin(xx / 17.0) * np.sin(yy / 23.0)
    # Radiometric-noise level of real L2A pixels (~0.1% reflectance) — keeps the
    # robust baseline tight so the injected anomaly stands out, like real data.
    noise = lambda: rng.normal(0, 0.0008, size=(g, g)).astype(np.float32)

    # Anomaly ellipse: centred, fading in over the recent scenes.
    cy, cx, ay, ax = g * 0.38, g * 0.62, g * 0.11, g * 0.15
    ellipse = np.exp(-(((yy - cy) / ay) ** 2 + ((xx - cx) / ax) ** 2)).astype(np.float32)
    ramp = np.clip(
        (np.arange(n_scenes) - (n_scenes - 5)) / 4.0, 0.0, 1.0
    ).astype(np.float32)

    stacks = []
    for t in range(n_scenes):
        a = ellipse * ramp[t]
        # Drying / stress signature: NIR drops (vegetation loss), SWIR1 rises
        # (moisture deficit), visible bands rise (soil exposure).
        blue = (0.05 + 0.01 * texture + noise()) * (1.0 + 0.15 * a)
        green = (0.09 + 0.015 * texture + noise()) * (1.0 + 0.15 * a)
        red = (0.06 + 0.02 * texture + noise()) * (1.0 + 0.60 * a)
        nir = (0.34 + 0.05 * texture + noise()) * (1.0 - 0.28 * a)
        swir1 = (0.20 + 0.02 * texture + noise()) * (1.0 + 0.38 * a)
        swir2 = (0.13 + 0.015 * texture + noise()) * (1.0 + 0.22 * a)
        bands = [blue, green, red, nir, swir1, swir2]
        dn = [np.clip(b, 0.0, 1.4) * 10000.0 + cdse_process.DN_OFFSET for b in bands]
        scl = np.full((g, g), 4, dtype=np.uint16)      # vegetation
        mask = np.ones((g, g), dtype=np.uint16)
        stack = np.stack(dn + [scl, mask], axis=-1).astype(np.uint16)
        stacks.append(stack)
    return stacks


def run_selftest(p: DeepParams, ring, progress: ProgressCb, out_dir: str) -> Dict:
    """Offline end-to-end validation on a deterministic synthetic region."""
    p = p.sanitized()
    p.grid = min(p.grid, 256)
    if progress:
        progress(5, "generating synthetic Sentinel-2 stack (selftest mode)")
    today = datetime.now(timezone.utc).date()
    n_scenes = 9
    scene_dates = [
        (today - timedelta(days=n_scenes - 1 - t)).isoformat() for t in range(n_scenes)
    ]
    stacks = _synthetic_stack(p, scene_dates)

    scenes: List[SceneInfo] = []
    for t, date_iso in enumerate(scene_dates):
        if progress:
            progress(8 + int(34 * t / n_scenes), f"processing synthetic scene {t + 1}/{n_scenes}")
        stack = stacks[t]
        refl = raster.to_reflectance(stack)
        valid = raster.valid_mask(stack)
        info = SceneInfo(date=date_iso, valid_fraction=raster.scene_valid_fraction(stack))
        info.indices = raster.compute_indices(refl, valid)
        info.composite = raster.composite_rgb(refl)
        scenes.append(info)
    scenes.sort(key=lambda s: s.date)
    return _finalize(p, "synthetic-selftest", ring, scenes, progress, out_dir)


def new_job_id() -> str:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    return f"{stamp}-{uuid.uuid4().hex[:8]}"
