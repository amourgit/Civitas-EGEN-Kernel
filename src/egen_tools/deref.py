"""Déréférencement des schémas JSON : remplace chaque ``$ref`` par le schéma désigné.

Sert au générateur AsyncAPI (charge utile autonome) et au détecteur de rupture (comparaison structurelle).
Les schémas des contrats ne sont pas récursifs ; une récursion éventuelle est refusée.
"""
from __future__ import annotations

import copy
from urllib.parse import urldefrag, urljoin

from .contracts import Contracts

_META = {"$schema", "$id"}


def _pointer(doc: dict, fragment: str):
    node = doc
    for part in [p for p in fragment.lstrip("#").split("/") if p]:
        node = node[part.replace("~1", "/").replace("~0", "~")]
    return node


def deref(node, base_id: str, contracts: Contracts, _stack: tuple = ()):
    """Retourne une copie de ``node`` sans ``$ref``. ``base_id`` : identifiant du document qui contient ``node``."""
    if isinstance(node, list):
        return [deref(n, base_id, contracts, _stack) for n in node]
    if not isinstance(node, dict):
        return node
    if "$ref" in node:
        absolute = urljoin(base_id, node["$ref"])
        doc_id, fragment = urldefrag(absolute)
        if absolute in _stack:
            raise ValueError(f"référence récursive : {absolute}")
        name = doc_id.rsplit("/", 1)[-1]
        doc = contracts.schemas[name]
        target = _pointer(doc, fragment) if fragment else doc
        resolved = deref(target, doc_id, contracts, _stack + (absolute,))
        siblings = {k: deref(v, base_id, contracts, _stack) for k, v in node.items() if k != "$ref"}
        return {**resolved, **siblings}
    out = {}
    for key, value in node.items():
        if key in _META:
            continue
        out[key] = deref(value, base_id, contracts, _stack)
    return out


def deref_document(name: str, contracts: Contracts):
    doc = contracts.schemas[name]
    return deref(copy.deepcopy(doc), doc["$id"], contracts)
