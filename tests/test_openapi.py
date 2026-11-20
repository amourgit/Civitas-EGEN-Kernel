"""R1.4 : la spécification OpenAPI de C1 à C4 (et du repli HTTP de C5)."""
import copy
import json
import re
from pathlib import Path

import pytest
import yaml
from openapi_spec_validator import validate
from openapi_spec_validator.validation.exceptions import OpenAPIValidationError

ROOT = Path(__file__).resolve().parents[1]
SPEC_PATH = ROOT / "contracts/openapi/egen-v1.openapi.yaml"
SPEC = yaml.safe_load(SPEC_PATH.read_text(encoding="utf-8"))


def _valider(spec):
    validate(spec, base_uri=SPEC_PATH.as_uri())


def test_la_specification_est_une_openapi_3_1_1_valide():
    assert SPEC["openapi"] == "3.1.1"
    _valider(SPEC)


def test_le_validateur_refuse_une_specification_cassee():
    cassee = copy.deepcopy(SPEC)
    cassee["components"]["parameters"]["PageSize"]["in"] = "nulle-part"  # emplacement de paramètre invalide
    with pytest.raises(Exception):
        _valider(cassee)
    cassee = copy.deepcopy(SPEC)
    cassee["paths"]["/egen/v1/operations"]["post"]["responses"]["202"]["headers"]["Location"]["schema"] = 3  # schéma invalide
    with pytest.raises(Exception):
        _valider(cassee)


def test_toute_reference_de_schema_existe():
    texte = SPEC_PATH.read_text(encoding="utf-8")
    for ref in re.findall(r"\$ref: \"(\.\./schemas/[^\"#]+)", texte):
        assert (SPEC_PATH.parent / ref).exists(), ref


def test_les_codes_http_des_raisons_standard_sont_declares():
    raisons = json.loads((ROOT / "contracts/catalog/reasons.json").read_text(encoding="utf-8"))["errors"]
    declares = {int(c) for chemin in SPEC["paths"].values() for op in chemin.values() if isinstance(op, dict)
                for c in op.get("responses", {})}
    assert {r["http"] for r in raisons} <= declares


def test_chaque_chemin_du_document_04_est_dans_la_specification():
    doc = (ROOT / "docs/04-contrats-egen.md").read_text(encoding="utf-8")
    attendus = ["/.well-known/egen/controlplane", "/egen/v1/capabilities", "/egen/v1/operations",
                "/egen/v1/operations/{id}:cancel", "/egen/v1/subscriptions"]
    for chemin in attendus:
        assert chemin.replace("{id}", "{id}") in SPEC["paths"], chemin
    for chemin in ("/.well-known/egen/controlplane", "/egen/v1/capabilities", "/egen/v1/operations",
                   "/egen/v1/subscriptions"):
        assert chemin in doc


def test_toute_demande_qui_modifie_exige_la_cle_d_idempotence():
    for chemin, item in SPEC["paths"].items():
        for methode in ("post",):
            if methode in item and chemin != "/egen/v1/subscriptions/{id}":
                params = item[methode].get("parameters", [])
                assert {"$ref": "#/components/parameters/IdempotencyKey"} in params, (chemin, methode)
