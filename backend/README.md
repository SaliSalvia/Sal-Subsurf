# Backend — SubsurfaceExplorer API

FastAPI service: strict region validation, Sentinel-2 L2A catalog metadata search
(Copernicus, anonymous) and open-DEM terrain indicators with provenance.

## Run

```bash
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
python -m pip install -e ".[dev]"
uvicorn subsurface_api.main:app --reload
```

Interactive docs: <http://127.0.0.1:8000/docs>

## Endpoints

| Method | Path | Body | Result |
| --- | --- | --- | --- |
| GET | `/api/v1/health` | — | status, version, time |
| POST | `/api/v1/regions/validate` | `{"polygon": [[lon,lat], …]}` | valid/issues/warnings, vertex count, area, perimeter, centroid, bbox |
| POST | `/api/v1/regions/catalog-search` | `{polygon, date_from, date_to, max_cloud_percent, max_results}` | region check + Sentinel-2 L2A product metadata (≤ 25) + provenance |
| POST | `/api/v1/regions/terrain-indicators` | `{"polygon": …}` | elevation/slope/TRI/gradient stats + surface-anomaly score + provenance + caveats |

Polygon rules: 3–10 distinct vertices, coordinates in range, no self-intersection,
area between 500 m² and 500 km² (warning above 100 km²).

## Tests

```bash
pytest tests -q          # 36 tests, no network access (providers are mocked)
```

## Deployment notes

* Dev: `--host 0.0.0.0` for LAN phones, `adb reverse tcp:8000 tcp:8000` for USB,
  or run inside Termux on the phone itself (`http://127.0.0.1:8000/`).
* **Docker / PaaS:** a production `Dockerfile` is included (honours `$PORT`, built-in
  health check on `/api/v1/health`):
  ```bash
  docker build -t subsurface-api .
  docker run -p 8000:8000 subsurface-api
  ```
  Deploys as-is to RunFlare (Docker app / GitHub integration, free Iranian tier — see
  `docs/OPERATIONS.md` option E), Hugging Face Spaces (set `PORT=7860`), or any Docker host.
* Production: reverse proxy with TLS (Caddy/Traefik) in front of uvicorn.
* The service is read-only and stores nothing.
