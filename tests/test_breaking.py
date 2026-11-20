"""R1.6 : le détecteur de rupture détecte les ruptures, accepte les ajouts, et applique la règle de version (C6)."""
import json
import shutil
from pathlib import Path

import pytest
import yaml

from egen_tools import breaking

ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture()
def deux(tmp_path):
    """(référence, courant) : deux copies identiques des contrats."""
    ref, cur = tmp_path / "ref", tmp_path / "cur"
    for dest in (ref, cur):
        for d in ("schemas", "openapi", "asyncapi", "catalog"):
            shutil.copytree(ROOT / "contracts" / d, dest / d)
        shutil.copy(ROOT / "contracts/VERSION", dest / "VERSION")
    return ref, cur


def _json(path, fn):
    d = json.loads(path.read_text(encoding="utf-8"))
    fn(d)
    path.write_text(json.dumps(d, indent=2, ensure_ascii=False), encoding="utf-8")


def _yaml(path, fn):
    d = yaml.safe_load(path.read_text(encoding="utf-8"))
    fn(d)
    path.write_text(yaml.safe_dump(d, sort_keys=False, allow_unicode=True), encoding="utf-8")


def _verdict(ref, cur):
    ch = breaking.comparer(cur, ref)
    return ch, breaking.verdict(ch, breaking._version(ref), breaking._version(cur))


def test_deux_copies_identiques_n_ont_aucun_changement(deux):
    ref, cur = deux
    ch, pb = _verdict(ref, cur)
    assert ch == [] and pb == []


def test_le_depot_est_comparable_a_lui_meme():
    ch = breaking.comparer(ROOT / "contracts", ROOT / "contracts")
    assert ch == []


def test_retirer_une_propriete_est_une_rupture_qui_exige_une_version_majeure(deux):
    ref, cur = deux
    _json(cur / "schemas/operation.schema.json", lambda d: d["properties"].pop("scope"))
    ch, pb = _verdict(ref, cur)
    assert any(c.kind == "breaking" and "propriété retirée : scope" in c.what for c in ch)
    assert pb, "une rupture sans version majeure doit être refusée"
    (cur / "VERSION").write_text("1.1\n")
    assert _verdict(ref, cur)[1], "une version mineure ne suffit pas pour une rupture"
    (cur / "VERSION").write_text("2.0\n")
    assert _verdict(ref, cur)[1] == []


def test_ajouter_une_propriete_facultative_exige_une_version_mineure(deux):
    ref, cur = deux
    _json(cur / "schemas/operation.schema.json", lambda d: d["properties"].update({"labels": {"type": "object"}}))
    ch, pb = _verdict(ref, cur)
    assert ch and all(c.kind == "compatible" for c in ch)  # signalé à chaque endroit où le schéma est référencé
    assert pb, "un ajout sans version mineure doit être signalé"
    (cur / "VERSION").write_text("1.1\n")
    assert _verdict(ref, cur)[1] == []


def test_ajouter_un_champ_obligatoire_est_une_rupture(deux):
    ref, cur = deux
    _json(cur / "schemas/operation-request.schema.json", lambda d: d["required"].append("deadline"))
    assert any(c.kind == "breaking" and "devenu obligatoire" in c.what for c in _verdict(ref, cur)[0])


def test_retirer_une_valeur_d_enumeration_est_une_rupture_en_ajouter_une_est_compatible(deux):
    ref, cur = deux
    _json(cur / "schemas/common.schema.json", lambda d: d["$defs"]["phase"]["enum"].remove("Unknown"))
    assert any(c.kind == "breaking" and "enum" in c.what for c in _verdict(ref, cur)[0])
    _json(cur / "schemas/common.schema.json", lambda d: d["$defs"]["phase"]["enum"].extend(["Unknown", "Paused"]))
    ch = _verdict(ref, cur)[0]
    assert ch and all(c.kind == "compatible" for c in ch)


def test_durcir_un_motif_ou_une_borne_est_une_rupture(deux):
    ref, cur = deux
    _json(cur / "schemas/error.schema.json", lambda d: d["properties"]["message"].update({"maxLength": 200}))
    assert any(c.kind == "breaking" and "maxLength" in c.what for c in _verdict(ref, cur)[0])


def test_openapi_chemin_retire_reponse_retiree_et_chemin_ajoute(deux):
    ref, cur = deux
    spec = cur / "openapi/egen-v1.openapi.yaml"
    _yaml(spec, lambda d: d["paths"].pop("/egen/v1/capabilities/{name}"))
    assert any(c.kind == "breaking" and "chemin retiré" in c.what for c in _verdict(ref, cur)[0])
    shutil.copy(ref / "openapi/egen-v1.openapi.yaml", spec)
    _yaml(spec, lambda d: d["paths"]["/egen/v1/operations"]["post"]["responses"].pop("429"))
    assert any(c.kind == "breaking" and "réponse retirée" in c.what for c in _verdict(ref, cur)[0])
    shutil.copy(ref / "openapi/egen-v1.openapi.yaml", spec)
    _yaml(spec, lambda d: d["paths"].update({"/egen/v1/health": {"get": {"responses": {"200": {"description": "ok"}}}}}))
    ch = _verdict(ref, cur)[0]
    assert ch and all(c.kind == "compatible" for c in ch)


def test_openapi_parametre_obligatoire_ajoute_est_une_rupture(deux):
    ref, cur = deux
    _yaml(cur / "openapi/egen-v1.openapi.yaml", lambda d: d["paths"]["/egen/v1/operations"]["get"]["parameters"].append(
        {"name": "tenantless", "in": "query", "required": True, "schema": {"type": "string"}}))
    assert any(c.kind == "breaking" and "obligatoire" in c.what for c in _verdict(ref, cur)[0])


def test_asyncapi_canal_retire_est_une_rupture(deux):
    ref, cur = deux
    _yaml(cur / "asyncapi/egen-events-v1.asyncapi.yaml", lambda d: d["channels"].pop("operationSucceeded"))
    assert any(c.kind == "breaking" and "canal retiré" in c.what for c in _verdict(ref, cur)[0])


def test_catalogue_raison_retiree_est_une_rupture_ajoutee_est_compatible(deux):
    ref, cur = deux
    _json(cur / "catalog/reasons.json", lambda d: d["errors"].pop(0))
    assert any(c.kind == "breaking" and "raisons d'erreur" in c.where for c in _verdict(ref, cur)[0])
    shutil.copy(ref / "catalog/reasons.json", cur / "catalog/reasons.json")
    _json(cur / "catalog/reasons.json", lambda d: d["errors"].append({"reason": "QuotaExceeded", "http": 429}))
    ch = _verdict(ref, cur)[0]
    assert ch and all(c.kind == "compatible" for c in ch)


def test_la_version_ne_peut_pas_reculer(deux):
    ref, cur = deux
    (ref / "VERSION").write_text("1.2\n")
    assert any("reculé" in p for p in _verdict(ref, cur)[1])
