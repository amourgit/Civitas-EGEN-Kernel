"""R1.5 : le document AsyncAPI des événements C5 (généré depuis le catalogue)."""
import shutil
import subprocess
from pathlib import Path

import pytest
import yaml

from egen_tools.contracts import Contracts
from egen_tools.gen_asyncapi import render

ROOT = Path(__file__).resolve().parents[1]
FICHIER = ROOT / "contracts/asyncapi/egen-events-v1.asyncapi.yaml"
OUTIL = ROOT / "tools/asyncapi"
CONTRACTS = Contracts(ROOT / "contracts")


def test_le_document_committe_est_celui_du_generateur():
    assert FICHIER.read_text(encoding="utf-8") == render(CONTRACTS), "relancer : egen-gen-asyncapi"


def test_un_canal_par_type_d_evenement_du_catalogue():
    doc = yaml.safe_load(FICHIER.read_text(encoding="utf-8"))
    adresses = {c["address"] for c in doc["channels"].values()}
    assert adresses == set(CONTRACTS.event_types)
    assert doc["asyncapi"] == "3.0.0"


def test_chaque_charge_utile_impose_son_type_et_les_champs_cloudevents():
    doc = yaml.safe_load(FICHIER.read_text(encoding="utf-8"))
    for message in doc["components"]["messages"].values():
        payload = message["payload"]
        assert payload["properties"]["type"] == {"const": message["name"]}
        assert {"specversion", "id", "source", "type", "subject", "time", "data"} <= set(payload["required"])
        assert "$ref" not in str(payload), "la charge utile doit être autonome"


def _node_pret():
    return shutil.which("node") and (OUTIL / "node_modules/@asyncapi/parser").exists()


@pytest.mark.skipif(not _node_pret(), reason="installer l'analyseur : cd tools/asyncapi && npm install")
def test_l_analyseur_officiel_valide_le_document():
    r = subprocess.run(["node", "validate.mjs", str(FICHIER)], cwd=OUTIL, capture_output=True, text=True)
    assert r.returncode == 0, r.stderr
    assert "VALIDE : AsyncAPI 3.0.0, 14 canaux" in r.stdout


@pytest.mark.skipif(not _node_pret(), reason="installer l'analyseur : cd tools/asyncapi && npm install")
def test_l_analyseur_officiel_refuse_un_document_casse(tmp_path):
    doc = yaml.safe_load(FICHIER.read_text(encoding="utf-8"))
    del doc["operations"]["sendOperationSucceeded"]["channel"]
    casse = tmp_path / "casse.yaml"
    casse.write_text(yaml.safe_dump(doc), encoding="utf-8")
    r = subprocess.run(["node", "validate.mjs", str(casse)], cwd=OUTIL, capture_output=True, text=True)
    assert r.returncode == 1
    assert "INVALIDE" in r.stderr
