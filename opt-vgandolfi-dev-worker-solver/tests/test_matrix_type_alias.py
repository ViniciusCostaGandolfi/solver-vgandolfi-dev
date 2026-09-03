"""Tests for `matrixType` (camelCase) alias on TspRequest and VrpIn.

The orchestrator/UI sends the input JSON with `matrixType` for every job type.
`TspRequest` and `VrpIn` accept it via Field(alias="matrixType") +
populate_by_name, while keeping snake_case `matrix_type` for compatibility.
"""
import pytest

from app.dtos import MatrixType, TspRequest, VrpIn


def _address() -> dict:
    return {
        "customer_name": "Cliente Teste",
        "street_name": "Rua A",
        "street_number": "100",
        "city": "Sao Paulo",
        "state": "SP",
        "postal_code": "01000-000",
        "latitude": -23.55,
        "longitude": -46.63,
    }


def _tsp_payload(matrix_key: str | None = None, matrix_value: str = "STREET") -> dict:
    payload: dict = {
        "origin": _address(),
        "stops": [{"id": "s1", "address": _address()}],
    }
    if matrix_key is not None:
        payload[matrix_key] = matrix_value
    return payload


def _vrp_payload(matrix_key: str | None = None, matrix_value: str = "STREET") -> dict:
    payload: dict = {
        "origin": _address(),
        "clients": [
            {"volume_liters": 10.0, "weight_kg": 5.0, "address": _address()}
        ],
        "vehicles": [{"name": "Van"}],
    }
    if matrix_key is not None:
        payload[matrix_key] = matrix_value
    return payload


# --- TspRequest -----------------------------------------------------------

def test_tsp_accepts_camel_case_matrix_type_street():
    """Orchestrator payload `{"matrixType": "STREET", ...}` must be respected."""
    req = TspRequest.model_validate(_tsp_payload("matrixType", "STREET"))
    assert req.matrix_type == MatrixType.STREET


def test_tsp_accepts_camel_case_matrix_type_euclidian():
    req = TspRequest.model_validate(_tsp_payload("matrixType", "EUCLIDIAN"))
    assert req.matrix_type == MatrixType.EUCLIDIAN


def test_tsp_accepts_snake_case_matrix_type():
    """Legacy snake_case `matrix_type` still works (populate_by_name)."""
    req = TspRequest.model_validate(_tsp_payload("matrix_type", "STREET"))
    assert req.matrix_type == MatrixType.STREET


def test_tsp_defaults_to_euclidian():
    """Omitted matrixType must default to EUCLIDIAN."""
    req = TspRequest.model_validate(_tsp_payload())
    assert req.matrix_type == MatrixType.EUCLIDIAN


# --- VrpIn -----------------------------------------------------------------

def test_vrp_accepts_camel_case_matrix_type_street():
    """Orchestrator payload `{"matrixType": "STREET", ...}` must be respected."""
    vrp = VrpIn.model_validate(_vrp_payload("matrixType", "STREET"))
    assert vrp.matrix_type == MatrixType.STREET


def test_vrp_accepts_camel_case_matrix_type_euclidian():
    vrp = VrpIn.model_validate(_vrp_payload("matrixType", "EUCLIDIAN"))
    assert vrp.matrix_type == MatrixType.EUCLIDIAN


def test_vrp_accepts_snake_case_matrix_type():
    """Legacy snake_case `matrix_type` still works (populate_by_name)."""
    vrp = VrpIn.model_validate(_vrp_payload("matrix_type", "STREET"))
    assert vrp.matrix_type == MatrixType.STREET


def test_vrp_defaults_to_euclidian():
    """Omitted matrixType must default to EUCLIDIAN."""
    vrp = VrpIn.model_validate(_vrp_payload())
    assert vrp.matrix_type == MatrixType.EUCLIDIAN