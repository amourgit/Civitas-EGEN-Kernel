"""Chargement et validation des contrats EGEN (schémas JSON, catalogues).

Source unique : le dossier ``contracts/`` du dépôt (ou celui désigné par ``EGEN_CONTRACTS``).
"""
from __future__ import annotations

import json
import os
from functools import lru_cache
from pathlib import Path

from jsonschema import Draft202012Validator
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT202012

BASE = "https://schemas.civitas.africa/egen/v1/"


def find_contracts_dir() -> Path:
    env = os.environ.get("EGEN_CONTRACTS")
    if env:
        return Path(env)
    here = Path(__file__).resolve()
    for parent in [here.parents[2], *Path.cwd().resolve().parents, Path.cwd().resolve()]:
        if (parent / "contracts" / "schemas").is_dir():
            return parent / "contracts"
    raise FileNotFoundError("dossier contracts/ introuvable (définir EGEN_CONTRACTS)")


class Contracts:
    """Registre des schémas d'un dossier ``contracts/`` et validateurs associés."""

    def __init__(self, root: Path | None = None):
        self.root = Path(root) if root else find_contracts_dir()
        self.schemas: dict[str, dict] = {}
        registry = Registry()
        for path in sorted((self.root / "schemas").glob("*.schema.json")):
            schema = json.loads(path.read_text(encoding="utf-8"))
            self.schemas[path.name] = schema
            registry = registry.with_resource(
                schema["$id"], Resource.from_contents(schema, default_specification=DRAFT202012)
            )
        self.registry = registry
        self.event_types = {
            e["type"]: e for e in self.catalog("event-types.json")["events"]
        }

    def catalog(self, name: str) -> dict:
        return json.loads((self.root / "catalog" / name).read_text(encoding="utf-8"))

    def validator(self, ref: str) -> Draft202012Validator:
        """``ref`` : ``operation.schema.json`` ou ``subscription.schema.json#/$defs/request``."""
        return Draft202012Validator({"$ref": BASE + ref}, registry=self.registry)

    def errors(self, instance, ref: str) -> list[dict]:
        out = []
        for err in self.validator(ref).iter_errors(instance):
            out.append(
                {
                    "keyword": err.validator,
                    "path": "/".join(str(p) for p in err.absolute_path),
                    "message": err.message[:200],
                }
            )
        return out

    def is_valid(self, instance, ref: str) -> bool:
        return not self.errors(instance, ref)

    def event_errors(self, event: dict) -> list[dict]:
        """Enveloppe CloudEvents, puis données selon le catalogue des types."""
        errs = self.errors(event, "cloudevent.schema.json")
        entry = self.event_types.get(event.get("type", ""))
        if entry is None:
            if not errs or all(e["path"] != "type" for e in errs):
                errs.append({"keyword": "catalog", "path": "type",
                             "message": f"type d'événement hors catalogue : {event.get('type')}"})
            return errs
        errs += [
            {**e, "path": "data/" + e["path"] if e["path"] else "data"}
            for e in self.errors(event.get("data", {}), f"event-data.schema.json#/$defs/{entry['dataSchema']}")
        ]
        return errs


@lru_cache(maxsize=1)
def default_contracts() -> Contracts:
    return Contracts()
