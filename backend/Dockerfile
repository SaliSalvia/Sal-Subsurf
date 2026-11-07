# SubsurfaceExplorer API — container image
# Works on RunFlare (Docker hosting), Hugging Face Spaces (set PORT=7860),
# Fly.io/Koyeb-style platforms, or any plain Docker host.
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PORT=8000

WORKDIR /app

# Install the package (pulls fastapi, uvicorn, shapely, numpy, Pillow, requests, tifffile).
COPY pyproject.toml README.md requirements.txt ./
COPY subsurface_api ./subsurface_api
RUN pip install --no-cache-dir .

EXPOSE 8000

# Container-native health check against the API's own health endpoint.
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD python -c "import os, urllib.request; urllib.request.urlopen('http://127.0.0.1:%s/api/v1/health' % os.environ.get('PORT', '8000'), timeout=5)"

# $PORT is honoured so platform-managed ports work (RunFlare, HF Spaces, etc.).
CMD ["sh", "-c", "uvicorn subsurface_api.main:app --host 0.0.0.0 --port ${PORT}"]
