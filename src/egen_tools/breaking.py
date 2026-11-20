"""R1.6 — Détection des ruptures de compatibilité entre deux versions des contrats (règle C6).

Compare ``contracts/`` à une version de référence (``contracts/baseline/<version>/``) :
- rupture (``breaking``) : exige une version MAJEURE ;
- ajout compatible (``compatible``) : exige une version MINEURE ;
- aucun changement : la version ne doit pas avoir reculé.
Les contrats servent en lecture et en écriture : la comparaison est volontairement prudente.
"""
from __future__ import annotations

import argparse
import copy
import json
import sys
from dataclasses import dataclass
from pathlib import Path

import yaml

from .contracts import Contracts
from .deref import deref_document


@dataclass(frozen=True)
class Change:
    kind: str  # "breaking" | "compatible"
    where: str
    what: str

    def __str__(self):
        return f"[{self.kind}] {self.where} : {self.what}"


# ------------------------------------------------------------------ schémas JSON (déréférencés)

def diff_schema(old, new, where: str) -> list[Change]:
    out: list[Change] = []
    if not isinstance(old, dict) or not isinstance(new, dict):
        if old != new:
            out.append(Change("breaking", where, "valeur différente"))
        return out
    B = lambda what: out.append(Change("breaking", where, what))
    C = lambda what: out.append(Change("compatible", where, what))

    if old.get("type") != new.get("type"):
        B(f"type {old.get('type')} -> {new.get('type')}")
    for kw in ("const", "pattern", "format"):
        if old.get(kw) != new.get(kw):
            B(f"{kw} modifié")
    if "enum" in old or "enum" in new:
        o, n = set(map(json.dumps, old.get("enum", []))), set(map(json.dumps, new.get("enum", [])))
        if "enum" not in new:
            C("enum retiré")
        elif "enum" not in old:
            B("enum ajouté")
        else:
            if o - n:
                B(f"valeurs retirées de enum : {sorted(o - n)}")
            if n - o:
                C(f"valeurs ajoutées à enum : {sorted(n - o)}")
    for kw in ("minLength", "minItems", "minimum", "exclusiveMinimum"):
        a, b = old.get(kw), new.get(kw)
        if a != b:
            (B if (b is not None and (a is None or b > a)) else C)(f"{kw} {a} -> {b}")
    for kw in ("maxLength", "maxItems", "maximum", "exclusiveMaximum"):
        a, b = old.get(kw), new.get(kw)
        if a != b:
            (B if (b is not None and (a is None or b < a)) else C)(f"{kw} {a} -> {b}")
    ro, rn = set(old.get("required", [])), set(new.get("required", []))
    for r in sorted(rn - ro):
        B(f"champ devenu obligatoire : {r}")
    for r in sorted(ro - rn):
        C(f"champ devenu facultatif : {r}")
    po, pn = old.get("properties", {}), new.get("properties", {})
    for p in sorted(set(po) - set(pn)):
        B(f"propriété retirée : {p}")
    for p in sorted(set(pn) - set(po)):
        C(f"propriété ajoutée : {p}")
    for p in sorted(set(po) & set(pn)):
        out += diff_schema(po[p], pn[p], f"{where}/{p}")
    ao, an = old.get("additionalProperties", True), new.get("additionalProperties", True)
    if ao != an:
        if isinstance(ao, bool) and isinstance(an, bool):
            (B if ao and not an else C)(f"additionalProperties {ao} -> {an}")
        else:
            out += diff_schema(ao if isinstance(ao, dict) else {}, an if isinstance(an, dict) else {}, f"{where}/additionalProperties")
    for kw in ("items", "propertyNames", "not", "if", "then", "else"):
        if kw in old or kw in new:
            if kw in old and kw in new:
                out += diff_schema(old[kw], new[kw], f"{where}/{kw}")
            else:
                B(f"{kw} ajouté ou retiré")
    for kw, grow_is_breaking in (("allOf", True), ("oneOf", False), ("anyOf", False)):
        lo, ln = old.get(kw, []), new.get(kw, [])
        if len(lo) != len(ln):
            shrink = len(ln) < len(lo)
            (B if (grow_is_breaking and not shrink) or (not grow_is_breaking and shrink) else C)(f"{kw} : {len(lo)} -> {len(ln)} éléments")
        for i, (a, b) in enumerate(zip(lo, ln)):
            out += diff_schema(a, b, f"{where}/{kw}[{i}]")
    return out


# ------------------------------------------------------------------ OpenAPI

def _openapi(root: Path, contracts: Contracts) -> dict:
    spec = yaml.safe_load((root / "openapi" / "egen-v1.openapi.yaml").read_text(encoding="utf-8"))

    def resolve(node, stack=()):
        if isinstance(node, list):
            return [resolve(n, stack) for n in node]
        if not isinstance(node, dict):
            return node
        if "$ref" in node:
            ref = node["$ref"]
            if ref in stack:
                raise ValueError(f"référence récursive {ref}")
            if ref.startswith("#/"):
                target = spec
                for part in ref[2:].split("/"):
                    target = target[part]
                return resolve(target, stack + (ref,))
            fichier, _, fragment = ref.partition("#")
            name = Path(fichier).name
            from .deref import deref
            schema = contracts.schemas[name]
            target = schema
            for part in [p for p in fragment.split("/") if p]:
                target = target[part]
            return deref(copy.deepcopy(target), schema["$id"], contracts)
        return {k: resolve(v, stack) for k, v in node.items()}

    return resolve({"paths": spec["paths"]})["paths"]


def diff_openapi(old: dict, new: dict) -> list[Change]:
    out: list[Change] = []
    for path in sorted(set(old) | set(new)):
        if path not in new:
            out.append(Change("breaking", path, "chemin retiré"))
            continue
        if path not in old:
            out.append(Change("compatible", path, "chemin ajouté"))
            continue
        for method in sorted(set(old[path]) | set(new[path])):
            where = f"{method.upper()} {path}"
            if method not in new[path]:
                out.append(Change("breaking", where, "méthode retirée"))
                continue
            if method not in old[path]:
                out.append(Change("compatible", where, "méthode ajoutée"))
                continue
            o, n = old[path][method], new[path][method]
            if not isinstance(o, dict):
                continue
            for code in sorted(set(o.get("responses", {})) | set(n.get("responses", {}))):
                ro, rn = o.get("responses", {}).get(code), n.get("responses", {}).get(code)
                if rn is None:
                    out.append(Change("breaking", f"{where} -> {code}", "réponse retirée"))
                elif ro is None:
                    out.append(Change("compatible", f"{where} -> {code}", "réponse ajoutée"))
                else:
                    so = ro.get("content", {}).get("application/json", {}).get("schema", {})
                    sn = rn.get("content", {}).get("application/json", {}).get("schema", {})
                    out += diff_schema(so, sn, f"{where} -> {code}")
            po = {p["name"]: p for p in o.get("parameters", [])}
            pn = {p["name"]: p for p in n.get("parameters", [])}
            for name in sorted(set(po) | set(pn)):
                if name not in pn:
                    out.append(Change("breaking", f"{where} paramètre {name}", "paramètre retiré"))
                elif name not in po:
                    out.append(Change("breaking" if pn[name].get("required") else "compatible", f"{where} paramètre {name}",
                                      "paramètre obligatoire ajouté" if pn[name].get("required") else "paramètre facultatif ajouté"))
                else:
                    if not po[name].get("required") and pn[name].get("required"):
                        out.append(Change("breaking", f"{where} paramètre {name}", "devenu obligatoire"))
                    out += diff_schema(po[name].get("schema", {}), pn[name].get("schema", {}), f"{where} paramètre {name}")
            bo = o.get("requestBody", {}).get("content", {}).get("application/json", {}).get("schema")
            bn = n.get("requestBody", {}).get("content", {}).get("application/json", {}).get("schema")
            if bo is not None and bn is not None:
                out += diff_schema(bo, bn, f"{where} corps")
            elif (bo is None) != (bn is None):
                out.append(Change("breaking", f"{where} corps", "corps ajouté ou retiré"))
    return out


# ------------------------------------------------------------------ AsyncAPI et catalogues

def diff_asyncapi(old: dict, new: dict) -> list[Change]:
    out: list[Change] = []
    ao = {c["address"]: c for c in old.get("channels", {}).values()}
    an = {c["address"]: c for c in new.get("channels", {}).values()}
    for a in sorted(set(ao) - set(an)):
        out.append(Change("breaking", a, "canal retiré"))
    for a in sorted(set(an) - set(ao)):
        out.append(Change("compatible", a, "canal ajouté"))
    mo, mn = old.get("components", {}).get("messages", {}), new.get("components", {}).get("messages", {})
    by_name_o = {m["name"]: m for m in mo.values()}
    by_name_n = {m["name"]: m for m in mn.values()}
    for name in sorted(set(by_name_o) & set(by_name_n)):
        out += diff_schema(by_name_o[name]["payload"], by_name_n[name]["payload"], name)
    return out


def diff_catalogs(old: Contracts, new: Contracts) -> list[Change]:
    out: list[Change] = []
    pairs = [
        ("types d'événements", lambda c: {e["type"]: e["dataSchema"] for e in c.catalog("event-types.json")["events"]}),
        ("raisons d'erreur", lambda c: {r["reason"]: r["http"] for r in c.catalog("reasons.json")["errors"]}),
        ("capacités standard", lambda c: {x["name"]: 1 for x in c.catalog("capabilities-standard.json")["capabilities"]}),
    ]
    for label, getter in pairs:
        o, n = getter(old), getter(new)
        for k in sorted(set(o) - set(n)):
            out.append(Change("breaking", f"catalogue {label}", f"retiré : {k}"))
        for k in sorted(set(n) - set(o)):
            out.append(Change("compatible", f"catalogue {label}", f"ajouté : {k}"))
        for k in sorted(set(o) & set(n)):
            if o[k] != n[k]:
                out.append(Change("breaking", f"catalogue {label}", f"{k} modifié : {o[k]} -> {n[k]}"))
    return out


# ------------------------------------------------------------------ comparaison complète

def comparer(current: Path, baseline: Path) -> list[Change]:
    cur, base = Contracts(current), Contracts(baseline)
    out: list[Change] = []
    for name in sorted(set(base.schemas) | set(cur.schemas)):
        if name not in cur.schemas:
            out.append(Change("breaking", name, "schéma retiré"))
        elif name not in base.schemas:
            out.append(Change("compatible", name, "schéma ajouté"))
        else:
            out += diff_schema(deref_document(name, base), deref_document(name, cur), name)
    out += diff_openapi(_openapi(baseline, base), _openapi(current, cur))
    ya = lambda r: yaml.safe_load((r / "asyncapi" / "egen-events-v1.asyncapi.yaml").read_text(encoding="utf-8"))
    out += diff_asyncapi(ya(baseline), ya(current))
    out += diff_catalogs(base, cur)
    return out


def _version(root: Path) -> tuple[int, int]:
    major, minor = (root / "VERSION").read_text().strip().split(".")
    return int(major), int(minor)


def verdict(changes: list[Change], base_version: tuple[int, int], cur_version: tuple[int, int]) -> list[str]:
    """Liste des violations de la règle C6 (vide = conforme)."""
    problems = []
    breaking = [c for c in changes if c.kind == "breaking"]
    compatible = [c for c in changes if c.kind == "compatible"]
    if cur_version < base_version:
        problems.append(f"la version a reculé : {cur_version} < {base_version}")
    if breaking and cur_version[0] <= base_version[0]:
        problems.append(f"{len(breaking)} rupture(s) de compatibilité sans version majeure supérieure "
                        f"(version {cur_version[0]}.{cur_version[1]}, référence {base_version[0]}.{base_version[1]})")
    elif compatible and not breaking and cur_version <= base_version:
        problems.append(f"{len(compatible)} ajout(s) compatible(s) sans version mineure supérieure "
                        f"(version {cur_version[0]}.{cur_version[1]}, référence {base_version[0]}.{base_version[1]})")
    return problems


def _derniere_reference(contracts: Path) -> Path | None:
    refs = [p for p in (contracts / "baseline").glob("*") if (p / "VERSION").exists()] if (contracts / "baseline").is_dir() else []
    return max(refs, key=_version, default=None) if refs else None


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--contracts", type=Path, default=Path("contracts"))
    ap.add_argument("--baseline", type=Path, default=None, help="dossier de référence (défaut : la plus haute version de contracts/baseline/)")
    args = ap.parse_args(argv)
    base = args.baseline or _derniere_reference(args.contracts)
    if base is None:
        print("aucune version de référence : rien à comparer (la première publication crée contracts/baseline/<version>/)")
        return 0
    changes = comparer(args.contracts, base)
    for c in changes:
        print(c)
    problems = verdict(changes, _version(base), _version(args.contracts))
    for p in problems:
        print("ERREUR :", p)
    print(f"contrats : {len(changes)} changement(s) par rapport à {base.name}, {len(problems)} violation(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
