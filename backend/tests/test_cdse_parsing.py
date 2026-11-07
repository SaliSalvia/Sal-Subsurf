from unittest.mock import patch

from subsurface_api.providers import cdse

RING = [(0.0, 0.0), (1.0, 0.0), (1.0, 1.0), (0.0, 1.0)]

RAW_PRODUCT = {
    "Id": "abc-123",
    "Name": "S2A_MSIL2A_20250101T082231_N0510_R021_T40SDB_20250101T112358.SAFE",
    "ContentDate": {"Start": "2025-01-01T08:22:31Z", "End": "2025-01-01T08:22:31Z"},
    "ContentLength": 104857600,
    "Attributes": [{"Name": "CloudCoverage", "Value": 12.5}],
    "GeoFootprint": {"type": "Polygon", "coordinates": [[[0, 0], [1, 0], [1, 1], [0, 0]]]},
}


def test_parse_product_extracts_metadata():
    p = cdse.parse_product(RAW_PRODUCT)
    assert p["id"] == "abc-123"
    assert p["processing_level"] == "S2MSI2A"
    assert p["datetime"] == "2025-01-01T08:22:31Z"
    assert p["cloud_cover_percent"] == 12.5
    assert p["size_mb"] == 100.0
    assert p["footprint"]["type"] == "Polygon"


def test_parse_product_accepts_live_api_attribute_name():
    p = cdse.parse_product({**RAW_PRODUCT, "Attributes": [{"Name": "cloudCover", "Value": 7.25}]})
    assert p["cloud_cover_percent"] == 7.25


def test_parse_product_tolerates_missing_fields():
    p = cdse.parse_product({"Id": "x", "Name": "S2_UNKNOWN"})
    assert p["cloud_cover_percent"] is None
    assert p["size_mb"] is None
    assert p["processing_level"] == "S2"


def test_build_filter_contains_geometry_and_level():
    f = cdse.build_filter(RING, "2025-01-01", "2025-02-01")
    assert "Collection/Name eq 'SENTINEL-2'" in f
    assert "SRID=4326;POLYGON((0.0000000 0.0000000" in f
    assert "ContentDate/Start gt 2025-01-01T00:00:00.000Z" in f
    assert "ContentDate/Start lt 2025-02-01T23:59:59.999Z" in f
    assert "contains(Name,'MSIL2A')" in f


def test_search_products_filters_by_cloud_and_returns_provenance():
    cloudy = dict(RAW_PRODUCT)
    cloudy["Id"] = "cloudy-1"
    cloudy["Attributes"] = [{"Name": "CloudCoverage", "Value": 80.0}]
    response = {"value": [RAW_PRODUCT, cloudy]}
    with patch("subsurface_api.providers.cdse.requests.get") as mock_get:
        mock_get.return_value.status_code = 200
        mock_get.return_value.json.return_value = response
        out = cdse.search_products(RING, "2025-01-01", "2025-02-01", 30.0, 10)
    assert [p["id"] for p in out["products"]] == ["abc-123"]
    assert out["total_matched"] == 2
    assert out["provenance"][0]["provider"] == cdse.PROVIDER
    assert out["warnings"] == []
    # $filter/$top/$orderby must be present in the query
    params = mock_get.call_args.kwargs["params"]
    assert params["$top"] == 10
    assert "$orderby" in params and "$filter" in params


def test_search_products_warns_when_all_cloudy():
    cloudy = dict(RAW_PRODUCT)
    cloudy["Attributes"] = [{"Name": "CloudCoverage", "Value": 95.0}]
    with patch("subsurface_api.providers.cdse.requests.get") as mock_get:
        mock_get.return_value.status_code = 200
        mock_get.return_value.json.return_value = {"value": [cloudy]}
        out = cdse.search_products(RING, "2025-01-01", "2025-02-01", 30.0, 10)
    assert out["products"] == []
    assert out["warnings"]


def test_search_products_raises_on_http_error():
    with patch("subsurface_api.providers.cdse.requests.get") as mock_get:
        mock_get.return_value.status_code = 429
        try:
            cdse.search_products(RING, "2025-01-01", "2025-02-01", 30.0, 10)
            raised = False
        except RuntimeError as exc:
            raised = True
            assert "429" in str(exc)
        assert raised
