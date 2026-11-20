"""Tests d'architecture automatiques : mots réservés, technologies dans les contrats, code propre à un moteur, secrets."""
from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path

RESERVES = ["cellule", "tenant", "catalogue", "affectation", "portail"]
TECHNOLOGIES = ["kubernetes", "k8s", "crossplane", "nomad", "consul", "vault", "openbao", "temporal", "spire"]
MOTEURS_DANS_LE_NOM = ["nomad", "consul", "vault", "openbao", "kubernetes", "crossplane", "temporal"]
SECRET = re.compile(r"github_pat_[A-Za-z0-9_]{20,}|ghp_[A-Za-z0-9]{20,}|-----BEGIN [A-Z ]*PRIVATE KEY-----")
TEXTE = {".py", ".md", ".json", ".yaml", ".yml", ".toml", ".mjs", ".txt", ".sh", ".cfg"}


def _suivis(root: Path) -> list[Path]:
    try:
        out = subprocess.run(["git", "ls-files"], cwd=root, capture_output=True, text=True, check=True).stdout.split("\n")
        return [root / p for p in out if p and (root / p).is_file()]
    except Exception:
        return [p for p in root.rglob("*") if p.is_file() and ".git" not in p.parts and "node_modules" not in p.parts
                and "__pycache__" not in p.parts and ".egg-info" not in str(p)]


def _noms_definis(contracts: Path) -> list[tuple[str, str]]:
    """Noms que définit EGEN : clés et valeurs de ses catalogues, noms de schémas, valeurs d'exemples."""
    noms: list[tuple[str, str]] = []
    for p in sorted((contracts / "catalog").glob("*.json")):
        noms += [(p.name, s) for s in _chaines(json.loads(p.read_text(encoding="utf-8")))]
    noms += [(p.name, p.name) for p in (contracts / "schemas").glob("*.json")]
    return noms


def _chaines(obj):
    if isinstance(obj, dict):
        for k, v in obj.items():
            yield str(k)
            yield from _chaines(v)
    elif isinstance(obj, list):
        for v in obj:
            yield from _chaines(v)
    elif isinstance(obj, str):
        yield obj


def verifier(root: Path) -> list[str]:
    erreurs: list[str] = []
    contracts = root / "contracts"
    # 1. mots réservés dans les noms définis par EGEN
    if contracts.is_dir():
        for source, nom in _noms_definis(contracts):
            for mot in RESERVES:
                if mot in nom.lower() and not nom.startswith(("^", "Exécuter", "Découvrir", "Une ", "Un ", "Le ", "La ")):
                    erreurs.append(f"{source} : « {nom} » contient le mot réservé « {mot} » (P-10)")
        # 2. aucune technologie imposée dans les contrats (G-04)
        for p in list(contracts.glob("schemas/*.json")) + list(contracts.glob("catalog/*.json")) + list(contracts.glob("openapi/*.yaml")):
            texte = p.read_text(encoding="utf-8").lower()
            for techno in TECHNOLOGIES:
                if re.search(rf"\b{techno}\b", texte):
                    erreurs.append(f"{p.relative_to(root)} : le contrat mentionne la technologie « {techno} » (G-04)")
    # 3. code propre à un moteur hors d'une extension (G-05)
    for p in _suivis(root):
        rel = p.relative_to(root).as_posix()
        if rel.startswith("src/") and p.suffix == ".py" and not rel.startswith("src/extensions/"):
            for moteur in MOTEURS_DANS_LE_NOM:
                if moteur in rel.lower():
                    erreurs.append(f"{rel} : code propre au moteur « {moteur} » hors de extensions/ (G-05)")
    # 4. secrets
    for p in _suivis(root):
        if p.suffix in TEXTE or p.name in {".gitignore"}:
            try:
                if SECRET.search(p.read_text(encoding="utf-8")):
                    erreurs.append(f"{p.relative_to(root)} : secret potentiel")
            except UnicodeDecodeError:
                pass
    return erreurs


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--root", type=Path, default=Path("."))
    args = ap.parse_args(argv)
    erreurs = verifier(args.root.resolve())
    for e in erreurs:
        print("ERREUR :", e)
    print(f"architecture : {len(erreurs)} erreur(s)")
    return 1 if erreurs else 0


if __name__ == "__main__":
    raise SystemExit(main())
