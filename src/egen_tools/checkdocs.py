"""Contrôles de la documentation : liens, sections d'ouverture, identifiants, tournures historiques, secrets, tableaux."""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

OUVERTURES = ("## À quoi sert ce document", "## Ce qu'il ne couvre pas")
SECRET = re.compile(r"github_pat_[A-Za-z0-9_]{20,}|ghp_[A-Za-z0-9]{20,}|-----BEGIN [A-Z ]*PRIVATE KEY-----")
# (motif, fichiers où le motif est admis)
INTERDITS = [
    (re.compile(r"avant la r[ée]forme|avant la refonte|pr[ée]c[ée]demment|auparavant|à l'origine|obsol[èe]te", re.I), set()),
    (re.compile(r"kernel natif|legacy", re.I), {"docs/00-etat-et-reprise.md"}),
    (re.compile(r"\bpasserelle\b(?! de domaine)", re.I), {"docs/README.md"}),
]
FAMILLES = {
    "D": (r"^\|\s*(D-\d\d)\s*\|", "docs/02-principes-et-decisions.md"),
    "P": (r"^\|\s*(P-\d\d)\s*\|", "docs/02-principes-et-decisions.md"),
    "G": (r"^\|\s*(G-\d\d)\s*\|", "docs/02-principes-et-decisions.md"),
    "PO": (r"^\|\s*(PO-\d\d)\s*\|", "docs/16-risques-et-verifications.md"),
    "VR": (r"^\|\s*(VR-\d\d)\s*\|", "docs/16-risques-et-verifications.md"),
    "RK": (r"^\|\s*(RK-\d\d)\s*\|", "docs/16-risques-et-verifications.md"),
    "S": (r"^\|\s*(S-\d\d)\s*\|", "docs/references.md"),
    "R": (r"^\|\s*(R\d\.\d+)\s*\|", "docs/14-plan-de-developpement.md"),
}
MOTIFS_REF = {"D": r"\bD-\d\d\b", "P": r"\bP-\d\d\b", "G": r"\bG-\d\d\b", "PO": r"\bPO-\d\d\b",
              "VR": r"\bVR-\d\d\b", "RK": r"\bRK-\d\d\b", "S": r"\bS-\d\d\b", "R": r"\bR\d\.\d+\b"}


def _fichiers(root: Path):
    out = [p for p in root.rglob("*.md") if ".git" not in p.parts and "node_modules" not in p.parts
           and "fondations" not in p.parts]
    return sorted(out)


def verifier(root: Path) -> list[str]:
    erreurs: list[str] = []
    textes = {p.relative_to(root).as_posix(): p.read_text(encoding="utf-8") for p in _fichiers(root)}

    for nom, t in textes.items():
        # liens relatifs (le code en ligne est ignoré : une expression régulière peut contenir « ]( »)
        sans_code = re.sub(r"`[^`\n]*`", "", t)
        for m in re.finditer(r"\]\(([^)\s]+)\)", sans_code):
            lien = m.group(1)
            if lien.startswith(("http", "mailto:", "#")):
                continue
            cible = (root / nom).parent / lien.split("#")[0]
            if not cible.exists():
                erreurs.append(f"{nom} : lien cassé -> {lien}")
        # sections d'ouverture
        if nom.startswith("docs/") or nom == "CONTRIBUTING.md":
            for s in OUVERTURES:
                if s not in t:
                    erreurs.append(f"{nom} : section manquante « {s[3:]} »")
        # secrets
        if SECRET.search(t):
            erreurs.append(f"{nom} : secret potentiel")
        # tournures interdites
        for motif, admis in INTERDITS:
            if nom in admis:
                continue
            for n, ligne in enumerate(t.splitlines(), 1):
                if motif.search(ligne):
                    erreurs.append(f"{nom}:{n} : terme interdit « {motif.search(ligne).group(0)} »")
        # tableaux
        lignes = t.splitlines()
        i = 0
        while i < len(lignes):
            if lignes[i].startswith("|"):
                j = i
                while j < len(lignes) and lignes[j].startswith("|"):
                    j += 1

                def cols(l):
                    l = re.sub(r"`[^`]*`", lambda m: m.group(0).replace("|", "¦"), l)
                    return len(l.strip().strip("|").split("|"))

                n0 = cols(lignes[i])
                for k in range(i, j):
                    if cols(lignes[k]) != n0:
                        erreurs.append(f"{nom}:{k + 1} : {cols(lignes[k])} colonnes au lieu de {n0}")
                i = j
            else:
                i += 1

    # identifiants définis / référencés
    if all(src in textes for _, src in FAMILLES.values()):
        for fam, (motif_def, src) in FAMILLES.items():
            definis = set(re.findall(motif_def, textes[src], re.M))
            for nom, t in textes.items():
                for ref in set(re.findall(MOTIFS_REF[fam], t)):
                    if ref not in definis:
                        erreurs.append(f"{nom} : {ref} référencé mais non défini")
    return erreurs


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--root", type=Path, default=Path("."))
    args = ap.parse_args(argv)
    erreurs = verifier(args.root.resolve())
    for e in erreurs:
        print("ERREUR :", e)
    print(f"documentation : {len(erreurs)} erreur(s)")
    return 1 if erreurs else 0


if __name__ == "__main__":
    raise SystemExit(main())
