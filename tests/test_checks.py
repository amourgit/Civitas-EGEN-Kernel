"""Les contrôles automatiques passent sur le dépôt, et détectent bien les fautes qu'ils ciblent."""
import shutil
from pathlib import Path

from egen_tools import checkarch, checkdocs

ROOT = Path(__file__).resolve().parents[1]


def test_la_documentation_du_depot_est_conforme():
    assert checkdocs.verifier(ROOT) == []


def test_l_architecture_du_depot_est_conforme():
    assert checkarch.verifier(ROOT) == []


def _copie(tmp_path):
    for d in ("docs", "contracts"):
        shutil.copytree(ROOT / d, tmp_path / d, ignore=shutil.ignore_patterns("node_modules"))
    for f in ("README.md", "CONTRIBUTING.md"):
        shutil.copy(ROOT / f, tmp_path / f)
    return tmp_path


def _ajoute(path: Path, texte: str):
    path.write_text(path.read_text(encoding="utf-8") + texte, encoding="utf-8")


def test_checkdocs_detecte_un_lien_casse(tmp_path):
    t = _copie(tmp_path)
    _ajoute(t / "docs/01-vision-et-perimetre.md", "\n[x](inexistant.md)\n")
    assert any("lien cassé" in e for e in checkdocs.verifier(t))


def test_checkdocs_detecte_un_identifiant_non_defini(tmp_path):
    t = _copie(tmp_path)
    _ajoute(t / "docs/01-vision-et-perimetre.md", "\nVoir PO-99.\n")
    assert any("PO-99" in e for e in checkdocs.verifier(t))


def test_checkdocs_detecte_une_tournure_historique_et_un_secret(tmp_path):
    t = _copie(tmp_path)
    _ajoute(t / "docs/03-architecture-generale.md", "\nAuparavant on faisait autrement.\nghp_" + "a" * 30 + "\n")
    erreurs = checkdocs.verifier(t)
    assert any("terme interdit" in e for e in erreurs)
    assert any("secret potentiel" in e for e in erreurs)


def test_checkdocs_detecte_une_section_d_ouverture_manquante(tmp_path):
    t = _copie(tmp_path)
    f = t / "docs/10-evenements-observabilite-audit.md"
    f.write_text(f.read_text(encoding="utf-8").replace("## Ce qu'il ne couvre pas", "## Autre"), encoding="utf-8")
    assert any("section manquante" in e for e in checkdocs.verifier(t))


def test_checkdocs_detecte_un_tableau_mal_forme(tmp_path):
    t = _copie(tmp_path)
    _ajoute(t / "docs/01-vision-et-perimetre.md", "\n| a | b |\n|---|---|\n| 1 | 2 | 3 |\n")
    assert any("colonnes" in e for e in checkdocs.verifier(t))


def test_checkarch_detecte_un_mot_reserve_dans_un_nom_de_capacite(tmp_path):
    t = _copie(tmp_path)
    import json
    f = t / "contracts/catalog/capabilities-standard.json"
    d = json.loads(f.read_text(encoding="utf-8"))
    d["capabilities"].append({"name": "gestion-tenant", "description": "x"})
    f.write_text(json.dumps(d), encoding="utf-8")
    assert any("mot réservé" in e for e in checkarch.verifier(t))


def test_checkarch_detecte_une_technologie_dans_un_contrat(tmp_path):
    t = _copie(tmp_path)
    f = t / "contracts/schemas/capability.schema.json"
    avant = f.read_text(encoding="utf-8")
    assert "C2 — Capacité" in avant, "le test ne casse rien : chaîne introuvable"
    f.write_text(avant.replace("C2 — Capacité", "C2 — Capacité Kubernetes"), encoding="utf-8")
    assert any("technologie" in e for e in checkarch.verifier(t))


def test_checkarch_detecte_du_code_de_moteur_hors_extension(tmp_path):
    t = _copie(tmp_path)
    (t / "src").mkdir()
    (t / "src/nomad_adapter.py").write_text("x = 1\n", encoding="utf-8")
    assert any("G-05" in e for e in checkarch.verifier(t))
    (t / "src/nomad_adapter.py").unlink()
    (t / "src/extensions").mkdir()
    (t / "src/extensions/nomad_adapter.py").write_text("x = 1\n", encoding="utf-8")
    assert not any("G-05" in e for e in checkarch.verifier(t))
