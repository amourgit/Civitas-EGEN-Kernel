"""R1.2 à R1.5 : les schémas, les exemples et les catalogues des contrats."""
import json
import re
from pathlib import Path

import pytest
import yaml
from jsonschema import Draft202012Validator

from egen_tools.contracts import BASE, Contracts

ROOT = Path(__file__).resolve().parents[1]
CONTRACTS = Contracts(ROOT / "contracts")
MANIFEST = yaml.safe_load((ROOT / "contracts/examples/manifest.yaml").read_text(encoding="utf-8"))["examples"]
RESERVED = ["cellule", "tenant", "catalogue", "affectation", "portail"]


@pytest.mark.parametrize("name", sorted(CONTRACTS.schemas))
def test_chaque_schema_est_un_schema_valide(name):
    schema = CONTRACTS.schemas[name]
    Draft202012Validator.check_schema(schema)
    assert schema["$id"] == BASE + name
    assert schema["$schema"] == "https://json-schema.org/draft/2020-12/schema"


@pytest.mark.parametrize("entry", [m for m in MANIFEST if m["valid"]], ids=lambda m: m["file"])
def test_exemples_valides_sont_acceptes(entry):
    data = json.loads((ROOT / "contracts/examples" / entry["file"]).read_text(encoding="utf-8"))
    assert CONTRACTS.errors(data, entry["schema"]) == []


@pytest.mark.parametrize("entry", [m for m in MANIFEST if not m["valid"]], ids=lambda m: m["file"])
def test_exemples_invalides_sont_refuses_pour_la_bonne_raison(entry):
    data = json.loads((ROOT / "contracts/examples/manifest.yaml".replace("manifest.yaml", entry["file"])).read_text(encoding="utf-8"))
    errors = CONTRACTS.errors(data, entry["schema"])
    assert errors, "un exemple invalide a été accepté : le schéma est trop laxiste"
    attendu = (entry["keyword"], entry["path"])
    assert any((e["keyword"], e["path"]) == attendu for e in errors), (attendu, errors)


def test_chaque_exemple_du_dossier_est_dans_le_manifeste():
    sur_disque = {f"{d}/{p.name}" for d in ("valid", "invalid") for p in (ROOT / "contracts/examples" / d).glob("*.json")}
    assert sur_disque == {m["file"] for m in MANIFEST}


def test_chaque_schema_a_au_moins_un_exemple_valide_et_un_invalide():
    refs = {m["schema"].split("#")[0] for m in MANIFEST if m["valid"]}
    refs_inv = {m["schema"].split("#")[0] for m in MANIFEST if not m["valid"]}
    couverts_requis = {"error", "controlplane-descriptor", "controlplane-document", "capability", "operation-request",
                       "operation", "cloudevent", "subscription"}
    assert couverts_requis <= {r.removesuffix(".schema.json") for r in refs}
    assert couverts_requis - {"controlplane-document"} <= {r.removesuffix(".schema.json") for r in refs_inv}


# --- C5 : catalogue des événements --------------------------------------------------------------

def _evenement(type_, data):
    return {"specversion": "1.0", "id": "evt-0001", "source": "spiffe://univ-x.civitas.africa/egen/cp/metier",
            "type": type_, "subject": "operation/01JD6K3X8Q2M5N7P9R1S3T5V7W", "time": "2026-11-19T14:05:40Z",
            "datacontenttype": "application/json", "data": data}


DONNEES = {
    "operationLifecycle": {"operationId": "01JD6K3X8Q2M5N7P9R1S3T5V7W", "capability": "object-storage", "scope": "univ-x/rh", "phase": "Succeeded"},
    "controlPlaneLifecycle": {"controlPlaneId": "spiffe://univ-x.civitas.africa/egen/cp/metier"},
    "capabilityLifecycle": {"controlPlaneId": "spiffe://univ-x.civitas.africa/egen/cp/metier", "capability": "object-storage"},
    "resourceState": {"ref": {"kind": "Bucket", "name": "rh-agenda"}},
}


@pytest.mark.parametrize("type_", sorted(CONTRACTS.event_types))
def test_chaque_type_du_catalogue_accepte_des_donnees_conformes(type_):
    famille = CONTRACTS.event_types[type_]["dataSchema"]
    assert CONTRACTS.event_errors(_evenement(type_, DONNEES[famille])) == []


@pytest.mark.parametrize("type_", sorted(CONTRACTS.event_types))
def test_chaque_type_du_catalogue_refuse_des_donnees_vides(type_):
    assert CONTRACTS.event_errors(_evenement(type_, {})), "des données vides ont été acceptées"


def test_un_type_hors_catalogue_est_refuse():
    errs = CONTRACTS.event_errors(_evenement("africa.civitas.egen.inconnu.cree.v1", {}))
    assert any(e["keyword"] == "catalog" for e in errs)


def test_les_familles_du_catalogue_existent_dans_les_schemas_de_donnees():
    defs = CONTRACTS.schemas["event-data.schema.json"]["$defs"]
    assert {e["dataSchema"] for e in CONTRACTS.event_types.values()} <= set(defs)


def test_les_types_d_evenements_du_catalogue_sont_ceux_du_document_04():
    doc = (ROOT / "docs/04-contrats-egen.md").read_text(encoding="utf-8")
    section = doc.split("| Type (préfixe `africa.civitas.egen.`)")[1].split("**Règles.**")[0]
    decrits = set()
    for ligne in section.splitlines():
        m = re.match(r"\| (.+?) \|", ligne)
        if not m or ligne.startswith("|---"):
            continue
        cellule = m.group(1)
        sujets = re.findall(r"`([^`]+)`", cellule)
        base = None
        for s in sujets:
            if s.startswith("."):
                decrits.add(f"{base.rsplit('.', 2)[0] if base else ''}")  # préfixe de famille
            else:
                base = s
                decrits.add(s)
    attendus = {t.removeprefix("africa.civitas.egen.") for t in CONTRACTS.event_types}
    # chaque événement du catalogue apparaît dans le document, sous forme complète ou abrégée (« .suspended.v1 »)
    texte = section
    for t in attendus:
        famille, _, reste = t.partition(".")
        dernier = t.split(".")[-2]
        assert t in texte or f".{dernier}.v1" in texte, f"{t} absent du document 04"


# --- noms, raisons, mots réservés -------------------------------------------------------------

def test_aucun_nom_ne_contient_un_mot_reserve():
    noms = [c["name"] for c in CONTRACTS.catalog("capabilities-standard.json")["capabilities"]]
    noms += [e["type"] for e in CONTRACTS.event_types.values()]
    noms += [r["reason"] for r in CONTRACTS.catalog("reasons.json")["errors"]]
    noms += list(CONTRACTS.schemas)
    for schema in CONTRACTS.schemas.values():
        noms += list(json.dumps(schema).lower().split('"'))  # toutes les clés et valeurs textuelles
    for nom in noms:
        for mot in RESERVED:
            assert mot not in nom.lower() or nom.startswith(("^", "http")), (nom, mot)


def test_les_raisons_du_catalogue_sont_celles_du_document_04_et_pascalcase():
    cat = CONTRACTS.catalog("reasons.json")["errors"]
    doc = (ROOT / "docs/04-contrats-egen.md").read_text(encoding="utf-8")
    section = doc.split("### Raisons d'erreur standard")[1].split("##")[0]
    dans_doc = {m.group(1): int(m.group(2)) for m in re.finditer(r"\| `(\w+)` \| (\d+) \|", section)}
    assert dans_doc == {r["reason"]: r["http"] for r in cat}
    assert all(CONTRACTS.is_valid(r["reason"], "common.schema.json#/$defs/reason") for r in cat)


def test_les_capacites_standard_sont_celles_du_document_04():
    cat = {c["name"] for c in CONTRACTS.catalog("capabilities-standard.json")["capabilities"]}
    doc = (ROOT / "docs/04-contrats-egen.md").read_text(encoding="utf-8")
    liste = re.search(r"Noms de capacités standard[^:]*:\s*(.*?)\.\n", doc, re.S).group(1)
    assert cat == set(re.findall(r"`([a-z-]+)`", liste))
    assert all(CONTRACTS.is_valid(n, "common.schema.json#/$defs/name") for n in cat)
