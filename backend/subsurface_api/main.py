"""FastAPI application: region validation, Sentinel-2 catalog search, terrain indicators,
and the deep temporal-processing pipeline (/api/v1/deep)."""

from __future__ import annotations

import logging
import os
from datetime import date, datetime, timedelta, timezone

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse

from . import __version__
from .deep.router import router as deep_router
from .geo import normalize_ring
from .providers import cdse
from .providers import terrain as terrain_provider
from .schemas import (
    CatalogRequest,
    CatalogResponse,
    HealthResponse,
    RegionCheckOut,
    RegionRequest,
    TerrainResponse,
)
from .indicators import surface_anomaly_score
from .validation import validate_region

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("subsurface_api")

app = FastAPI(
    title="SubsurfaceExplorer API",
    version=__version__,
    description=(
        "Evidence-aware geospatial workspace API. Strict polygon validation, live "
        "Sentinel-2 L2A catalog metadata search (Copernicus Data Space Ecosystem, "
        "anonymous) and open-DEM terrain indicators with full provenance. "
        "Surface indicators only — never buried-target claims."
    ),
)

# CORS: comma-separated origins in CORS_ORIGINS (env). Default stays "*" so the
# Android app and the bundled /deep-console keep working out of the box; set
# e.g. CORS_ORIGINS=https://your-frontend.example on public deployments.
# Native mobile apps are not subject to CORS; only browsers are.
_cors_env = os.environ.get("CORS_ORIGINS", "*").strip()
_cors_origins = ["*"] if _cors_env in ("*", "") else [o.strip() for o in _cors_env.split(",") if o.strip()]

app.add_middleware(
    CORSMiddleware,
    allow_origins=_cors_origins,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Content-Type", "X-API-Key"],
)

# Deep temporal-processing endpoints (acquire → indices → anomaly → fusion).
app.include_router(deep_router)

_STATIC_DIR = os.path.join(os.path.dirname(__file__), "static")


@app.get("/deep-console", include_in_schema=False)
def deep_console() -> FileResponse:
    """Browser console for the deep pipeline (bbox → analyze → artifacts)."""
    path = os.path.join(_STATIC_DIR, "deep_console.html")
    if not os.path.isfile(path):
        raise HTTPException(status_code=404, detail="Console asset missing.")
    return FileResponse(path, media_type="text/html")


def _ring(polygon):
    ring = normalize_ring(polygon)
    if len(ring) < 3:
        raise HTTPException(
            status_code=422, detail=["Polygon needs at least 3 distinct vertices."]
        )
    return ring


def _check_out(check) -> RegionCheckOut:
    return RegionCheckOut(
        valid=check.valid,
        issues=check.issues,
        warnings=check.warnings,
        vertex_count=check.vertex_count,
        area_sqm=round(check.area_sqm, 2),
        area_hectares=round(check.area_hectares, 4),
        perimeter_m=round(check.perimeter_m, 2),
        centroid=list(check.centroid),
        bbox=check.bbox,
    )


def _validate_dates(date_from: str, date_to: str) -> None:
    try:
        d_from = date.fromisoformat(date_from)
        d_to = date.fromisoformat(date_to)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=[f"Invalid date: {exc}"]) from exc
    today = datetime.now(timezone.utc).date()
    if d_from > d_to:
        raise HTTPException(status_code=422, detail=["date_from must be ≤ date_to."])
    if (d_to - d_from).days > 366:
        raise HTTPException(status_code=422, detail=["Date range is limited to 366 days."])
    if d_to > today + timedelta(days=1):
        raise HTTPException(status_code=422, detail=["date_to is in the future."])


@app.get("/api/v1/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="ok",
        version=__version__,
        time_utc=datetime.now(timezone.utc).isoformat(timespec="seconds"),
    )


@app.post("/api/v1/regions/validate", response_model=RegionCheckOut)
def validate_region_endpoint(req: RegionRequest) -> RegionCheckOut:
    _ring(req.polygon)
    return _check_out(validate_region(req.polygon))


@app.post("/api/v1/regions/catalog-search", response_model=CatalogResponse)
def catalog_search(req: CatalogRequest) -> CatalogResponse:
    _validate_dates(req.date_from, req.date_to)
    check = validate_region(req.polygon)
    if not check.valid:
        raise HTTPException(status_code=400, detail=check.issues)
    ring = _ring(req.polygon)
    try:
        result = cdse.search_products(
            ring, req.date_from, req.date_to, req.max_cloud_percent, req.max_results
        )
    except RuntimeError as exc:
        log.warning("Catalog search failed: %s", exc)
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    return CatalogResponse(
        region=_check_out(check),
        products=result["products"],
        total_matched=result["total_matched"],
        provenance=result["provenance"],
        warnings=check.warnings + result["warnings"],
    )


@app.post("/api/v1/regions/terrain-indicators", response_model=TerrainResponse)
def terrain_indicators(req: RegionRequest) -> TerrainResponse:
    check = validate_region(req.polygon)
    if not check.valid:
        raise HTTPException(status_code=400, detail=check.issues)
    ring = _ring(req.polygon)
    try:
        result = terrain_provider.analyze_region(ring)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except RuntimeError as exc:
        log.warning("Terrain analysis failed: %s", exc)
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    result.update(surface_anomaly_score(result))
    return TerrainResponse(**result)
